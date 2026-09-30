import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import vm from "node:vm";

import {createChannels} from "../src/channels.js";
import {createJatosPromiseCompatibility} from "../src/jatos-promise.js";

const {createDeferred, rejectedPromise} = createJatosPromiseCompatibility();
const patchContext = {};
vm.runInNewContext(readFileSync(new URL("../../public/javascripts/fast-json-patch.js", import.meta.url), "utf8"), patchContext);

function setup(t) {
    const sockets = [];
    const timers = new Map();
    const requests = [];
    const events = [];
    const state = {ending: false, starting: false, invalid: false};
    let timerId = 0;
    class FakeWebSocket {
        OPEN = 1;
        CLOSED = 3;
        readyState = 0;
        sent = [];
        constructor(url) { this.url = url; sockets.push(this); }
        send(data) { this.sent.push(JSON.parse(data)); }
        open() { this.readyState = this.OPEN; this.onopen(); }
        receive(data) { this.onmessage({data: JSON.stringify(data)}); }
        close() { this.readyState = this.CLOSED; this.onclose(); }
    }
    const window = new EventTarget();
    window.location = {protocol: "https:", host: "example.test"};
    window.WebSocket = FakeWebSocket;
    for (const [name, value] of Object.entries({window, WebSocket: FakeWebSocket, jsonpatch: patchContext.jsonpatch})) {
        const descriptor = Object.getOwnPropertyDescriptor(globalThis, name);
        Object.defineProperty(globalThis, name, {value, configurable: true, writable: true});
        t.after(() => {
            if (descriptor) Object.defineProperty(globalThis, name, descriptor);
            else delete globalThis[name];
        });
    }
    for (const [name, interval] of [["setTimeout", false], ["setInterval", true]]) {
        t.mock.method(globalThis, name, (callback, delay) => {
            timers.set(++timerId, {callback, delay, interval});
            return timerId;
        });
    }
    for (const name of ["clearTimeout", "clearInterval"]) {
        t.mock.method(globalThis, name, id => timers.delete(id));
    }
    for (const name of ["info", "warn", "error"]) t.mock.method(console, name, () => {});
    const jatos = {
        urlBasePath: "/jatos/", studyResultUuid: "run-uuid", studyResultId: "self",
        httpTimeout: 123, httpRetry: 2, httpRetryWait: 456,
        showOverlay: config => events.push(config.text),
        jQuery: {ajax: options => {
            const request = {options};
            requests.push(request);
            return {retry: options => { request.retry = options; }};
        }}
    };
    const channels = createChannels(jatos, {
        createDeferred, rejectedPromise,
        getURL: path => `https://example.test/${path}`,
        getAjaxErrorMsg: error => error.statusText,
        showIdOverlay: () => events.push("ids"),
        isEndingStudy: () => state.ending,
        isStartingComponent: () => state.starting,
        isStudyRunInvalid: () => state.invalid,
        setStudyRunInvalid: value => { state.invalid = value; }
    });
    function fire(id) {
        const timer = timers.get(id);
        assert.ok(timer, `timer ${id} exists`);
        if (!timer.interval) timers.delete(id);
        timer.callback();
    }
    function open(kind, callbacks = {}) {
        const promise = kind === "batch" ? channels.openBatchChannelWithRetry() : jatos.joinGroup(callbacks);
        const socket = sockets.at(-1);
        socket.open();
        assert.equal(promise.state(), "pending");
        socket.receive(kind === "batch"
            ? {action: "OPENED", data: {score: 1}, version: 4}
            : {action: "OPENED", sessionData: {score: 1}, sessionVersion: 4,
                groupResultId: 8, memberId: "self", members: ["self"], channels: ["self"], groupState: "STARTED"});
        assert.equal(promise.state(), "resolved");
        return socket;
    }
    return {jatos, channels, sockets, timers, requests, events, state, open, fire};
}

for (const kind of ["batch", "group"]) {
    test(`${kind} opens after session initialization and applies patches before callbacks`, t => {
        const {jatos, open} = setup(t);
        const session = jatos[`${kind}Session`];
        const calls = [];
        const callback = (...args) => calls.push([...args, session.get("score")]);
        if (kind === "batch") jatos.onBatchSession(callback);
        const socket = open(kind, {onGroupSession: callback});
        assert.equal(socket.url, `wss://example.test/jatos/publix/run-uuid/${kind}/${kind === "batch" ? "open" : "join"}`);
        assert.deepEqual(socket.sent, [{action: "READY"}]);
        const patches = [{op: "replace", path: "/score", value: 2}];
        socket.receive(kind === "batch" ? {action: "SESSION", patches, version: 5}
            : {action: "SESSION", sessionPatches: patches, sessionVersion: 5});
        assert.deepEqual(calls, [["/score", "replace", 2]]);
        const copy = session.getAll();
        copy.score = 99;
        assert.equal(session.get("score"), 2);
        socket.receive(kind === "batch" ? {patches: [{op: "replace", path: "", value: {new: true}}]}
            : {sessionPatches: [{op: "replace", path: "", value: {new: true}}]});
        assert.deepEqual(session.getAll(), {new: true});
    });

    test(`${kind} session writes preserve protocol, version guards, acknowledgements and timeouts`, t => {
        const {jatos, open, timers, fire} = setup(t);
        const socket = open(kind);
        const session = jatos[`${kind}Session`];
        const calls = [];
        const pending = session.set("score", 3, msg => calls.push(msg));
        assert.equal(pending.resolve, undefined);
        assert.equal(pending.state(), "pending");
        assert.deepEqual(socket.sent.at(-1), kind === "batch"
            ? {action: "SESSION", id: 0, patches: [{op: "add", path: "/score", value: 3}], version: 4, versioning: true}
            : {action: "SESSION", sessionActionId: 0, sessionPatches: [{op: "add", path: "/score", value: 3}], sessionVersion: 4, sessionVersioning: true});
        assert.equal(session.set("score", 4).state(), "rejected");
        const ackId = kind === "batch" ? "id" : "sessionActionId";
        socket.receive({action: "SESSION_ACK", [ackId]: 0});
        assert.equal(pending.state(), "resolved");
        assert.deepEqual(calls, [`${kind === "batch" ? "Batch" : "Group"} session update successful`]);
        assert.equal([...timers.values()].filter(timer => !timer.interval).length, 0);
        const failed = session.set("score", 4, undefined, msg => calls.push(msg));
        socket.receive({action: "SESSION_FAIL", [ackId]: 1, errorMsg: "conflict"});
        assert.equal(failed.state(), "rejected");
        assert.equal(calls.at(-1), "conflict");
        const timeout = session.set("score", 5, undefined, msg => calls.push(msg));
        fire([...timers].find(([, timer]) => !timer.interval)[0]);
        assert.equal(timeout.state(), "rejected");
        assert.equal(calls.at(-1), "Timeout sending session patch");
        jatos[`${kind}SessionVersioning`] = false;
        assert.equal(session.set("score", 6).state(), "pending");
        assert.equal(session.set("score", 7).state(), "pending");
    });

    test(`${kind} reconnects after close and observes live study-run guards`, t => {
        const {jatos, open, timers, sockets, state, fire} = setup(t);
        const socket = open(kind);
        const checkId = [...timers].find(([, timer]) => timer.delay === jatos.channelClosedCheckInterval)[0];
        socket.close();
        fire(checkId);
        assert.equal(sockets.length, 2);
        const replacement = sockets[1];
        replacement.open();
        replacement.receive(kind === "batch" ? {version: 5} : {sessionVersion: 5});
        state.ending = true;
        const newCheckId = [...timers].find(([, timer]) => timer.delay === jatos.channelClosedCheckInterval)[0];
        replacement.close();
        fire(newCheckId);
        assert.equal(sockets.length, 2);
        assert.ok([...timers.values()].some(timer => timer.delay === 2 * jatos.channelOpeningBackoffTimeMin));
    });

    test(`${kind} heartbeat pong cancels the outstanding heartbeat timeout`, t => {
        const {jatos, open, timers, fire} = setup(t);
        const socket = open(kind);
        fire([...timers].find(([, timer]) => timer.delay === jatos.channelHeartbeatInterval)[0]);
        assert.deepEqual(socket.sent.at(-1), {heartbeat: "ping"});
        assert.equal([...timers.values()].filter(timer => !timer.interval).length, 1);
        socket.receive({heartbeat: "pong"});
        assert.equal([...timers.values()].filter(timer => !timer.interval).length, 0);
    });
}

test("batch connection events fire once per transition and server closure invalidates the run", t => {
    const {jatos, open, state, events} = setup(t);
    const transitions = [];
    jatos.onConnected(() => transitions.push("connected"));
    jatos.onDisconnected(() => transitions.push("disconnected"));
    const socket = open("batch");
    socket.receive({heartbeat: "pong"});
    assert.equal(jatos.isConnected(), true);
    socket.receive({action: "CLOSED"});
    socket.close();
    assert.equal(jatos.isConnected(), false);
    assert.deepEqual(transitions, ["connected", "disconnected"]);
    assert.equal(state.invalid, true);
    assert.deepEqual(events, ["This study run is invalid."]);
    assert.equal(jatos.joinGroup().state(), "rejected");
});

test("late callbacks from a replaced batch socket cannot alter the current session", t => {
    const {jatos, open, timers, sockets, fire} = setup(t);
    const old = open("batch");
    old.close();
    fire([...timers].find(([, timer]) => timer.delay === jatos.channelClosedCheckInterval)[0]);
    const replacement = sockets[1];
    replacement.open();
    replacement.receive({action: "OPENED", data: {score: 9}, version: 7});
    old.onopen();
    old.receive({data: {score: 100}, version: 100});
    old.onerror();
    old.onclose();
    assert.equal(jatos.batchSession.get("score"), 9);
    assert.equal(jatos.isConnected(), true);
    assert.deepEqual(replacement.sent, [{action: "READY"}]);
});

test("group membership deltas, messaging and fixing preserve callbacks and state", t => {
    const {jatos, open} = setup(t);
    const events = [];
    const socket = open("group", {
        onMemberJoin: member => events.push(["join", member]),
        onMemberOpen: member => events.push(["open", member]),
        onMessage: msg => events.push(msg)
    });
    socket.receive({action: "JOINED", memberId: "other"});
    socket.receive({action: "CHANNEL_OPENED", memberId: "other"});
    socket.receive({msg: {hello: true}});
    assert.deepEqual(jatos.groupMembers, ["self", "other"]);
    assert.deepEqual(jatos.groupChannels, ["self", "other"]);
    assert.equal(jatos.groupResultId, "8");
    assert.equal(jatos.groupMemberId, "self");
    assert.equal(jatos.isGroupOpen(), true);
    assert.deepEqual(events, [["join", "other"], ["open", "other"], {hello: true}]);
    jatos.sendGroupMsg("hello");
    jatos.sendGroupMsgTo("other", "private");
    assert.deepEqual(socket.sent.slice(-2), [{msg: "hello"}, {recipient: "other", msg: "private"}]);
    const fixed = jatos.setGroupFixed();
    assert.deepEqual(socket.sent.at(-1), {action: "FIXED"});
    socket.receive({action: "FIXED", groupState: "FIXED"});
    assert.equal(fixed.state(), "resolved");
    assert.equal(jatos.isGroupFixed(), true);
    socket.receive({action: "CHANNEL_CLOSED", memberId: "other"});
    socket.receive({action: "LEFT", memberId: "other"});
    assert.deepEqual(jatos.groupMembers, ["self"]);
    assert.deepEqual(jatos.groupChannels, ["self"]);
});

test("group reassignment and leaving keep HTTP options, guards and completion behavior", t => {
    const {jatos, open, requests, timers} = setup(t);
    const socket = open("group");
    const reassignment = jatos.reassignGroup();
    assert.equal(requests[0].options.url, "https://example.test/../group/reassign");
    assert.equal(jatos.leaveGroup().state(), "rejected");
    requests[0].options.statusCode[200]();
    assert.equal(reassignment.state(), "resolved");
    const leave = jatos.leaveGroup();
    assert.equal(requests[1].options.url, "https://example.test/../group/leave");
    assert.equal(requests[1].options.type, "GET");
    assert.equal(requests[1].options.timeout, 123);
    assert.deepEqual(requests[1].retry, {times: 2, timeout: 456});
    requests[1].options.success("left");
    assert.equal(leave.state(), "resolved");
    assert.equal([...timers.values()].some(timer => timer.delay === jatos.channelClosedCheckInterval), false);
    assert.equal(socket.readyState, socket.OPEN);
});

test("lifecycle cleanup stops only closed-channel checks", t => {
    const {jatos, channels, open, timers} = setup(t);
    const batch = open("batch");
    const group = open("group");
    channels.stopClosedChecks();
    assert.equal(timers.size, 2);
    assert.ok([...timers.values()].every(timer => timer.delay === jatos.channelHeartbeatInterval));
    assert.equal(batch.readyState, batch.OPEN);
    assert.equal(group.readyState, group.OPEN);
});
