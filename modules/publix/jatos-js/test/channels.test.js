import assert from "node:assert/strict";
import test from "node:test";

import {installResultDataApi} from "../src/result-data.js";
import {createStudyRunState, installStudyRunApi} from "../src/study-run.js";
import {createChannels} from "../src/channels.js";

function setup(t) {
    const sockets = [];
    const timers = new Map();
    const requests = [];
    const events = [];
    const state = createStudyRunState();
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
    for (const [name, value] of Object.entries({window, WebSocket: FakeWebSocket})) {
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

    };
    const channels = createChannels(jatos, {
        requestHttp: options => { requests.push({options}); },
        studyRunState: state,
        getURL: path => `https://example.test/${path}`,
        showIdOverlay: () => events.push("ids"),
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

    test(`${kind} session retries version conflicts only when opted in`, t => {
        const {jatos, open, timers} = setup(t);
        const socket = open(kind);
        const session = jatos[`${kind}Session`];
        const maxRetriesSetting = `${kind}SessionPatchMaxRetries`;
        const idKey = kind === "batch" ? "id" : "sessionActionId";
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const successes = [];
        const failures = [];

        assert.equal(jatos[maxRetriesSetting], 0);
        jatos[maxRetriesSetting] = 1;
        const pending = session.set("score", 3,
            msg => successes.push(msg), msg => failures.push(msg));
        const firstAttempt = socket.sent.at(-1);
        assert.equal(firstAttempt[idKey], 0);
        assert.equal(firstAttempt[versionKey], 4);

        socket.receive({
            action: "SESSION_FAIL",
            [idKey]: 0,
            [versionKey]: 5,
            errorCode: "SESSION_VERSION_CONFLICT",
            errorMsg: "Version mismatch"
        });

        assert.equal(socket.sent.at(-1)[idKey], 0, "waits for the broadcast");
        assert.equal(session.get("score"), 1, "conflict version does not advance local data");
        socket.receive({action: "SESSION", [versionKey]: 5,
            [kind === "batch" ? "patches" : "sessionPatches"]: [{op: "replace", path: "/score", value: 2}]});

        const retry = socket.sent.at(-1);
        assert.equal(retry[idKey], 1);
        assert.equal(retry[versionKey], 5);
        assert.deepEqual(retry[kind === "batch" ? "patches" : "sessionPatches"],
            firstAttempt[kind === "batch" ? "patches" : "sessionPatches"]);
        assert.equal(pending.state(), "pending");
        assert.equal(session.get("score"), 2);
        assert.deepEqual(successes, []);
        assert.deepEqual(failures, []);
        assert.equal([...timers.values()].filter(timer => !timer.interval).length, 1);

        socket.receive({action: "SESSION_ACK", [idKey]: 1});
        assert.equal(pending.state(), "resolved");
        assert.equal(successes.length, 1);
        assert.deepEqual(failures, []);
    });

    test(`${kind} session stops after the configured number of conflict retries`, t => {
        const {jatos, open} = setup(t);
        const socket = open(kind);
        const session = jatos[`${kind}Session`];
        const idKey = kind === "batch" ? "id" : "sessionActionId";
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const failures = [];
        jatos[`${kind}SessionPatchMaxRetries`] = 1;

        const pending = session.set("score", 3, undefined, msg => failures.push(msg));
        socket.receive({action: "SESSION", [versionKey]: 5,
            [kind === "batch" ? "patches" : "sessionPatches"]: [{op: "replace", path: "/score", value: 2}]});
        socket.receive({action: "SESSION_FAIL", [idKey]: 0, [versionKey]: 5,
            errorCode: "SESSION_VERSION_CONFLICT", errorMsg: "first conflict"});
        jatos[`${kind}SessionPatchMaxRetries`] = 100; // Applies only to future operations.
        socket.receive({action: "SESSION_FAIL", [idKey]: 1, [versionKey]: 6,
            errorCode: "SESSION_VERSION_CONFLICT", errorMsg: "second conflict"});

        assert.equal(socket.sent.filter(msg => msg.action === "SESSION").length, 2);
        assert.equal(pending.state(), "rejected");
        assert.deepEqual(failures, ["second conflict"]);
    });

    test(`${kind} retries ignore late acknowledgements and preserve the original patch`, t => {
        const {jatos, open} = setup(t);
        const socket = open(kind);
        const idKey = kind === "batch" ? "id" : "sessionActionId";
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const dataKey = kind === "batch" ? "data" : "sessionData";
        const patchKey = kind === "batch" ? "patches" : "sessionPatches";
        jatos[`${kind}SessionPatchMaxRetries`] = 1;
        const value = {nested: 1};
        const promise = jatos[`${kind}Session`].set("score", value);
        value.nested = 99;
        socket.receive({action: "SESSION", [versionKey]: 5,
            [patchKey]: [{op: "replace", path: "/score", value: 2}]});
        socket.receive({action: "SESSION_FAIL", [idKey]: 0, [versionKey]: 5,
            errorCode: "SESSION_VERSION_CONFLICT"});
        assert.equal(socket.sent.at(-1)[patchKey][0].value.nested, 1);
        socket.receive({action: "SESSION_ACK", [idKey]: 0, [versionKey]: 1});
        socket.receive({action: "SESSION_FAIL", [idKey]: 0, [versionKey]: 1,
            [dataKey]: {score: -1}, errorCode: "SESSION_VERSION_CONFLICT"});
        assert.equal(jatos[`${kind}Session`].get("score"), 2);
        assert.equal(promise.state(), "pending");
        socket.receive({action: "SESSION_ACK", [idKey]: 1});
        assert.equal(promise.state(), "resolved");
    });

    test(`${kind} does not retry default conflicts, ordinary failures, missing versions, or timeouts`, t => {
        const {jatos, open, timers, fire} = setup(t);
        const socket = open(kind);
        const idKey = kind === "batch" ? "id" : "sessionActionId";
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        for (const scenario of ["default", "ordinary", "version", "timeout"]) {
            jatos[`${kind}SessionPatchMaxRetries`] = scenario === "default" ? 0 : 2;
            const failures = [];
            const promise = jatos[`${kind}Session`].set("score", 3, undefined, msg => failures.push(msg));
            const count = socket.sent.length;
            const id = socket.sent.at(-1)[idKey];
            if (scenario === "timeout") {
                fire([...timers].find(([, timer]) => !timer.interval && timer.delay === jatos.channelSendingTimeoutTime)[0]);
            } else {
                socket.receive({action: "SESSION_FAIL", [idKey]: id,
                    ...(scenario === "version" ? {} : {[versionKey]: 4}),
                    ...(scenario === "ordinary" ? {} : {errorCode: "SESSION_VERSION_CONFLICT"})});
            }
            assert.equal(promise.state(), "rejected", scenario);
            assert.equal(failures.length, 1, scenario);
            assert.equal(socket.sent.length, count, scenario);
        }
    });

    test(`${kind} additional error codes never trigger patch retries`, t => {
        const {jatos, open} = setup(t);
        const socket = open(kind);
        const idKey = kind === "batch" ? "id" : "sessionActionId";
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        jatos[`${kind}SessionPatchMaxRetries`] = 3;
        for (const errorCode of ["SESSION_PATCH_FAILED", "SESSION_WRITE_FORBIDDEN",
            "SESSION_UPDATE_RETRIES_EXHAUSTED", "UNKNOWN_ACTION", "BATCH_NOT_FOUND",
            "GROUP_NOT_FOUND", "MESSAGE_DELIVERY_FAILED"]) {
            const failures = [];
            const pending = jatos[`${kind}Session`].set("score", 2, undefined, error => failures.push(error));
            const count = socket.sent.length;
            const id = socket.sent.at(-1)[idKey];
            socket.receive({action: "SESSION_FAIL", [idKey]: id, [versionKey]: 4, errorCode, errorMsg: "Rejected"});
            assert.equal(pending.state(), "rejected", errorCode);
            assert.deepEqual(failures, ["Rejected"]);
            assert.equal(socket.sent.length, count);
        }
    });

    test(`${kind} requests full state only after waiting and ignores broadcasts already included`, t => {
        const {jatos, open, timers, fire} = setup(t);
        const socket = open(kind);
        const idKey = kind === "batch" ? "id" : "sessionActionId";
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const dataKey = kind === "batch" ? "data" : "sessionData";
        const patchKey = kind === "batch" ? "patches" : "sessionPatches";
        jatos[`${kind}SessionPatchMaxRetries`] = 1;
        const session = jatos[`${kind}Session`];
        const pending = session.set("score", 3);
        socket.receive({action: "SESSION_FAIL", [idKey]: 0, [versionKey]: 5,
            errorCode: "SESSION_VERSION_CONFLICT"});
        assert.equal(socket.sent.length, 2);
        fire([...timers].find(([, timer]) => timer.delay === 250)[0]);
        assert.deepEqual(socket.sent.at(-1), {action: "SESSION_GET"});
        socket.receive({action: "SESSION", [versionKey]: 5, [dataKey]: {list: [1]}});
        assert.equal(socket.sent.at(-1)[idKey], 1);
        assert.equal(pending.state(), "pending");
        socket.receive({action: "SESSION", [versionKey]: 5,
            [patchKey]: [{op: "add", path: "/list/-", value: 1}]});
        socket.receive({action: "SESSION", [versionKey]: 6,
            [patchKey]: [{op: "add", path: "/list/-", value: 2}]});
        assert.deepEqual(session.get("list"), [1, 2]);
    });

    test(`${kind} full-state SESSION replies stay silent while patches emit callbacks`, t => {
        const {jatos, open} = setup(t);
        const calls = [];
        const onSession = (...args) => calls.push(args);
        if (kind === "batch") jatos.onBatchSession(onSession);
        const socket = open(kind, {onGroupSession: onSession, onUpdate: () => calls.push("update")});
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const dataKey = kind === "batch" ? "data" : "sessionData";
        const patchKey = kind === "batch" ? "patches" : "sessionPatches";
        socket.receive({action: "SESSION", [versionKey]: 5, [dataKey]: {score: 5}});
        assert.equal(jatos[`${kind}Session`].get("score"), 5);
        assert.deepEqual(calls, []);
        socket.receive({action: "SESSION", [versionKey]: 6,
            [patchKey]: [{op: "replace", path: "/score", value: 6}]});
        assert.equal(jatos[`${kind}Session`].get("score"), 6);
        assert.deepEqual(calls, kind === "batch" ? [["/score", "replace"]]
            : [["/score", "replace"], "update"]);
    });

    test(`${kind} out-of-order broadcasts fill gaps without requesting full state`, t => {
        const {jatos, open} = setup(t);
        const socket = open(kind);
        const idKey = kind === "batch" ? "id" : "sessionActionId";
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const patchKey = kind === "batch" ? "patches" : "sessionPatches";
        jatos[`${kind}SessionPatchMaxRetries`] = 1;
        jatos[`${kind}Session`].set("score", 3);
        socket.receive({action: "SESSION", [versionKey]: 6,
            [patchKey]: [{op: "replace", path: "/score", value: 6}]});
        assert.equal(jatos[`${kind}Session`].get("score"), 1);
        socket.receive({action: "SESSION_FAIL", [idKey]: 0, [versionKey]: 5,
            errorCode: "SESSION_VERSION_CONFLICT"});
        socket.receive({action: "SESSION", [versionKey]: 5,
            [patchKey]: [{op: "replace", path: "/score", value: 5}]});
        assert.equal(jatos[`${kind}Session`].get("score"), 6);
        assert.equal(socket.sent.at(-1)[versionKey], 6);
        assert.equal(socket.sent.some(msg => msg.action === "SESSION_GET"), false);
    });

    for (const failure of ["timeout", "close", "server close", "send", "ending"]) {
        test(`${kind} waiting retry rejects on synchronization ${failure}`, t => {
            const {jatos, open, timers, fire, state} = setup(t);
            const socket = open(kind);
            const idKey = kind === "batch" ? "id" : "sessionActionId";
            const versionKey = kind === "batch" ? "version" : "sessionVersion";
            const dataKey = kind === "batch" ? "data" : "sessionData";
            const errors = [];
            jatos[`${kind}SessionPatchMaxRetries`] = 1;
            const pending = jatos[`${kind}Session`].set("score", 3, undefined, error => errors.push(error));
            socket.receive({action: "SESSION_FAIL", [idKey]: 0, [versionKey]: 5,
                errorCode: "SESSION_VERSION_CONFLICT"});
            if (failure === "close") socket.close();
            else if (failure === "server close") socket.receive({action: "CLOSED", [versionKey]: 5});
            else {
                if (failure === "send") socket.send = () => { throw new Error("send failed"); };
                fire([...timers].find(([, timer]) => timer.delay === 250)[0]);
                if (failure === "timeout") {
                    fire([...timers].find(([, timer]) => !timer.interval
                        && timer.delay === jatos.channelSendingTimeoutTime)[0]);
                } else if (failure === "ending") {
                    state.ending = true;
                    socket.receive({action: "SESSION", [versionKey]: 5, [dataKey]: {score: 2}});
                }
            }
            assert.equal(pending.state(), "rejected");
            assert.equal(errors.length, 1);
            assert.equal(socket.sent.filter(msg => msg.action === "SESSION").length, 1);
            assert.equal([...timers.values()].some(timer => timer.delay === 250), false);
        });
    }

    test(`${kind} detects gaps without write retries and shares one refresh request`, t => {
        const {jatos, open, timers, fire} = setup(t);
        const socket = open(kind);
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const dataKey = kind === "batch" ? "data" : "sessionData";
        const patchKey = kind === "batch" ? "patches" : "sessionPatches";
        const patch = version => ({action: "SESSION", [versionKey]: version,
            [patchKey]: [{op: "replace", path: "/score", value: version}]});
        socket.receive(patch(6));
        socket.receive(patch(7));
        assert.equal(jatos[`${kind}Session`].get("score"), 1);
        assert.equal([...timers.values()].filter(timer => timer.delay === 250).length, 1);
        fire([...timers].find(([, timer]) => timer.delay === 250)[0]);
        socket.receive(patch(8));
        assert.equal(socket.sent.filter(msg => msg.action === "SESSION_GET").length, 1);
        socket.receive({action: "SESSION", [versionKey]: 6, [dataKey]: {score: 6}});
        assert.equal(jatos[`${kind}Session`].get("score"), 8);
        socket.receive({action: "SESSION", [versionKey]: 5, [dataKey]: {score: -1}});
        assert.equal(jatos[`${kind}Session`].get("score"), 8);
        assert.equal([...timers.values()].some(timer => !timer.interval), false);
    });

    test(`${kind} acknowledgements do not advance the applied session version`, t => {
        const {jatos, open} = setup(t);
        const socket = open(kind);
        const idKey = kind === "batch" ? "id" : "sessionActionId";
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const session = jatos[`${kind}Session`];
        const pending = session.set("score", 3);
        socket.receive({action: "SESSION_ACK", [idKey]: 0, [versionKey]: 5});
        assert.equal(pending.state(), "resolved");
        session.set("score", 4);
        assert.equal(socket.sent.at(-1)[versionKey], 4);
        assert.equal(session.get("score"), 1);
    });

    test(`${kind} failed sends reject without leaving a pending request or timeout`, t => {
        const {jatos, open, timers} = setup(t);
        const socket = open(kind);
        const session = jatos[`${kind}Session`];
        const error = new Error("send failed");
        const send = socket.send;
        socket.send = () => { throw error; };
        const errors = [];
        const failed = session.set("score", 2, undefined, value => errors.push(value));
        assert.equal(failed.state(), "rejected");
        failed.fail((...args) => assert.deepEqual(args, []));
        assert.deepEqual(errors, [error]);
        assert.equal([...timers.values()].filter(timer => !timer.interval).length, 0);
        socket.send = send;
        const next = session.set("score", 3);
        const ackId = kind === "batch" ? "id" : "sessionActionId";
        assert.equal(socket.sent.at(-1)[ackId], 1);
        socket.receive({action: "SESSION_ACK", [ackId]: 1});
        assert.equal(next.state(), "resolved");
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
        replacement.receive(kind === "batch" ? {version: 5, data: {}} : {sessionVersion: 5, sessionData: {}});
        state.ending = true;
        const newCheckId = [...timers].find(([, timer]) => timer.delay === jatos.channelClosedCheckInterval)[0];
        replacement.close();
        fire(newCheckId);
        assert.equal(sockets.length, 2);
        assert.equal([...timers.values()].some(timer => !timer.interval), false);
    });

    test(`${kind} heartbeat versions detect silent loss with retries disabled`, t => {
        const {jatos, open, timers, fire} = setup(t);
        const socket = open(kind);
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const dataKey = kind === "batch" ? "data" : "sessionData";
        assert.equal(jatos[`${kind}SessionPatchMaxRetries`], 0);
        fire([...timers].find(([, timer]) => timer.delay === jatos.channelHeartbeatInterval)[0]);
        socket.receive({heartbeat: "pong", [versionKey]: 5});
        assert.equal(jatos[`${kind}Session`].get("score"), 1);
        assert.equal([...timers.values()].filter(timer => !timer.interval).length, 1,
            "heartbeat timeout is replaced by a synchronization grace period");
        socket.receive({heartbeat: "pong", [versionKey]: 5});
        fire([...timers].find(([, timer]) => timer.delay === 250)[0]);
        assert.deepEqual(socket.sent.at(-1), {action: "SESSION_GET"});
        socket.receive({heartbeat: "pong", [versionKey]: 6});
        assert.equal(socket.sent.filter(msg => msg.action === "SESSION_GET").length, 1);
        socket.receive({action: "SESSION", [versionKey]: 6, [dataKey]: {score: 6}});
        assert.equal(jatos[`${kind}Session`].get("score"), 6);
        assert.equal([...timers.values()].some(timer => !timer.interval), false);
    });

    test(`${kind} heartbeat version waits for broadcasts without advancing local version`, t => {
        const {jatos, open, timers} = setup(t);
        const socket = open(kind);
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const patchKey = kind === "batch" ? "patches" : "sessionPatches";
        socket.receive({heartbeat: "pong", [versionKey]: 5});
        jatos[`${kind}Session`].set("score", 9);
        assert.equal(socket.sent.at(-1)[versionKey], 4);
        socket.receive({action: "SESSION", [versionKey]: 5,
            [patchKey]: [{op: "replace", path: "/score", value: 5}]});
        assert.equal(jatos[`${kind}Session`].get("score"), 5);
        assert.equal([...timers.values()].some(timer => timer.delay === 250), false);
        assert.equal(socket.sent.some(msg => msg.action === "SESSION_GET"), false);
    });

    test(`${kind} old, equal, absent and invalid heartbeat versions require no synchronization`, t => {
        const {jatos, open, timers} = setup(t);
        const socket = open(kind);
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        for (const version of [undefined, null, 3, 4, -1, 5.5, "5"]) {
            socket.receive({heartbeat: "pong", [versionKey]: version});
        }
        assert.equal(jatos[`${kind}Session`].get("score"), 1);
        assert.equal([...timers.values()].some(timer => !timer.interval), false);
        assert.equal(socket.sent.length, 1);
    });

    test(`${kind} later heartbeat can recover after a refresh timeout`, t => {
        const {jatos, open, timers, fire} = setup(t);
        const socket = open(kind);
        const versionKey = kind === "batch" ? "version" : "sessionVersion";
        const dataKey = kind === "batch" ? "data" : "sessionData";
        socket.receive({heartbeat: "pong", [versionKey]: 5});
        fire([...timers].find(([, timer]) => timer.delay === 250)[0]);
        fire([...timers].find(([, timer]) => !timer.interval
            && timer.delay === jatos.channelSendingTimeoutTime)[0]);
        socket.receive({heartbeat: "pong", [versionKey]: 5});
        fire([...timers].find(([, timer]) => timer.delay === 250)[0]);
        assert.equal(socket.sent.filter(msg => msg.action === "SESSION_GET").length, 2);
        socket.receive({action: "SESSION", [versionKey]: 5, [dataKey]: {score: 5}});
        assert.equal(jatos[`${kind}Session`].get("score"), 5);
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
    assert.equal(requests[1].options.method, "GET");
    assert.equal(requests[1].options.timeout, 123);
    assert.deepEqual(requests[1].options.retry, {times: 2, timeout: 456});
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

for (const kind of ["batch", "group"]) {
    test(`${kind} shared session API sends every patch operation through its own protocol`, t => {
        const {jatos, open} = setup(t);
        const socket = open(kind);
        const session = jatos[`${kind}Session`];
        const operations = [
            ["add", ["/nested/value", 2], {op: "add", path: "/nested/value", value: 2}],
            ["set", ["score", 3], {op: "add", path: "/score", value: 3}],
            ["setAll", [{score: 4}], {op: "replace", path: "", value: {score: 4}}],
            ["remove", ["/score"], {op: "remove", path: "/score"}],
            ["clear", [], {op: "replace", path: "", value: {}}],
            ["replace", ["/score", 5], {op: "replace", path: "/score", value: 5}],
            ["copy", ["/score", "/copy"], {op: "copy", path: "/copy", from: "/score"}],
            ["move", ["/score", "/moved"], {op: "move", path: "/moved", from: "/score"}]
        ];
        for (const [method, args, patch] of operations) {
            const successes = [];
            const failures = [];
            const pending = session[method](...args, msg => successes.push(msg), msg => failures.push(msg));
            const message = socket.sent.at(-1);
            const idKey = kind === "batch" ? "id" : "sessionActionId";
            assert.deepEqual(message[kind === "batch" ? "patches" : "sessionPatches"], [patch]);
            assert.equal(pending.state(), "pending");
            socket.receive({action: "SESSION_ACK", [idKey]: message[idKey]});
            assert.equal(pending.state(), "resolved");
            assert.equal(successes.length, 1);
            assert.deepEqual(failures, []);

            const rejected = session[method](...args, undefined, msg => failures.push(msg));
            const failedMessage = socket.sent.at(-1);
            socket.receive({action: "SESSION_FAIL", [idKey]: failedMessage[idKey], errorMsg: "conflict"});
            assert.equal(rejected.state(), "rejected");
            assert.deepEqual(failures, ["conflict"]);
        }
    });
}

test("shared session APIs keep batch and group data independent and read live replacements", t => {
    const {jatos, open} = setup(t);
    const batch = open("batch");
    const group = open("group");
    assert.notEqual(jatos.batchSession, jatos.groupSession);
    batch.receive({data: {nested: {score: 2}, zero: 0, nil: null}, version: 5});
    group.receive({sessionData: {nested: {score: 3}}, sessionVersion: 5});
    for (const [session, score] of [[jatos.batchSession, 2], [jatos.groupSession, 3]]) {
        assert.equal(session.find("/nested/score"), score);
        assert.equal(session.test("/nested/score", score), true);
        assert.equal(session.defined("/nested/score"), true);
        assert.equal(session.defined("/missing"), false);
        session.get("nested").score = 100;
        session.find("/nested").score = 100;
        session.getAll().nested.score = 100;
        assert.equal(session.find("/nested/score"), score);
    }
    assert.equal(jatos.batchSession.defined("/zero"), true);
    assert.equal(jatos.batchSession.defined("/nil"), true);
    batch.receive({data: {replacement: true}, version: 6});
    assert.deepEqual(jatos.batchSession.getAll(), {replacement: true});
    assert.equal(jatos.groupSession.find("/nested/score"), 3);
});

for (const kind of ["batch", "group"]) {
    test(`${kind} incoming updates preserve root replacements and full-session precedence`, t => {
        const {jatos, open} = setup(t);
        const socket = open(kind);
        const session = jatos[`${kind}Session`];
        const patchKey = kind === "batch" ? "patches" : "sessionPatches";
        const dataKey = kind === "batch" ? "data" : "sessionData";
        socket.receive({[patchKey]: [
            {op: "replace", path: "", value: {nested: {score: 2}}},
            {op: "replace", path: "/nested/score", value: 3}
        ]});
        assert.deepEqual(session.getAll(), {nested: {score: 3}});
        socket.receive({[patchKey]: [{op: "replace", path: "/nested/score", value: 4}],
            [dataKey]: {fullSession: true}});
        assert.deepEqual(session.getAll(), {fullSession: true});
        socket.receive({heartbeat: "pong"});
        assert.deepEqual(session.getAll(), {fullSession: true});
        socket.receive({[dataKey]: null});
        assert.deepEqual(session.getAll(), {});
        socket.receive({[patchKey]: [{op: "replace", path: "", value: null}]});
        assert.equal(session.getAll(), null);
        socket.receive({[dataKey]: {restored: true}});
        assert.deepEqual(session.getAll(), {restored: true});
    });
}


test("server invalidation reaches result-data and navigation through the shared run state", t => {
    const {jatos, open, state} = setup(t);
    installResultDataApi(jatos, {
        studyRunState: state,
        sendToHttpLoop: () => assert.fail("invalid run must not send result data")
    });
    installStudyRunApi(jatos, {studyRunState: state, isInitialized: () => true});
    const socket = open("batch");
    socket.receive({action: "CLOSED"});
    const errors = [];
    assert.equal(jatos.submitResultData("data", undefined, error => errors.push(error)).state(), "rejected");
    jatos.startComponent("next-uuid", undefined, error => errors.push(error));
    assert.equal(errors.length, 2);
    assert.ok(errors.every(error => error.includes("This study run is invalid")));
    assert.equal(createStudyRunState().invalid, false, "new runs do not inherit invalidation");
});

for (const kind of ["batch", "group"]) {
    test(`${kind} heartbeat failure clears old timers and reconnects with its own notifications`, t => {
        const {jatos, open, sockets, timers, fire} = setup(t);
        const errors = [];
        const disconnected = [];
        jatos.onDisconnected(() => disconnected.push(true));
        const socket = open(kind, {onError: error => errors.push(error)});
        const heartbeat = [...timers].find(([, timer]) => timer.delay === jatos.channelHeartbeatInterval)[0];
        fire(heartbeat);
        fire(heartbeat);
        const pending = [...timers].filter(([, timer]) => !timer.interval).map(([id]) => id);
        assert.equal(pending.length, 2);
        fire(pending[0]);
        assert.equal(socket.readyState, socket.CLOSED);
        assert.equal(sockets.length, 2);
        assert.equal(timers.has(heartbeat), false);
        assert.equal(timers.has(pending[1]), false);
        assert.deepEqual(errors, kind === "group" ? ["Group channel heartbeat fail"] : []);
        assert.equal(disconnected.length, kind === "batch" ? 1 : 0);
        const replacement = sockets[1];
        replacement.open();
        replacement.receive(kind === "batch" ? {version: 5} : {sessionVersion: 5});
        const restarted = [...timers].find(([, timer]) => timer.delay === jatos.channelHeartbeatInterval)[0];
        fire(restarted);
        assert.deepEqual(replacement.sent.at(-1), {heartbeat: "ping"});
    });
}

test("batch and group heartbeat timers remain independent and retain response rules", t => {
    const {jatos, open, timers, fire} = setup(t);
    const batch = open("batch");
    const group = open("group");
    const intervals = [...timers].filter(([, timer]) => timer.delay === jatos.channelHeartbeatInterval);
    jatos.channelHeartbeatTimeoutTime = 1234;
    for (const [id] of intervals) {
        fire(id);
        fire(id);
    }
    const pending = () => [...timers.values()].filter(timer => !timer.interval);
    assert.equal(pending().length, 4);
    assert.ok(pending().every(timer => timer.delay === 1234));
    batch.receive({heartbeat: "other"});
    assert.equal(pending().length, 4);
    group.receive({heartbeat: "other"});
    assert.equal(pending().length, 2);
    batch.receive({heartbeat: "pong"});
    assert.equal(pending().length, 0);
    group.readyState = group.CLOSED;
    const count = group.sent.length;
    fire(intervals[1][0]);
    assert.equal(group.sent.length, count);
    assert.equal(pending().length, 0);
});


test("group reassignment treats HTTP 204 as unsuccessful without retries", t => {
    const {jatos, open, requests} = setup(t);
    open("group");
    const calls = [];
    const result = jatos.reassignGroup(() => calls.push("success"), () => calls.push("failure"));
    assert.equal(requests[0].options.retry, undefined);
    requests[0].options.statusCode[204]();
    assert.equal(result.state(), "rejected");
    assert.deepEqual(calls, ["failure"]);
});

test("closed-channel polling keeps independent timers and channel-specific notifications", t => {
    const {jatos, open, timers, sockets, fire} = setup(t);
    const errors = [];
    const disconnected = [];
    jatos.onDisconnected(() => disconnected.push(true));
    const batch = open("batch");
    const group = open("group", {onError: error => errors.push(error)});
    const checks = [...timers].filter(([, timer]) => timer.delay === jatos.channelClosedCheckInterval);
    for (const [id] of checks) fire(id);
    assert.equal(sockets.length, 2, "open sockets must not reconnect");

    // Simulate closure without a close event: polling must still recover.
    batch.readyState = batch.CLOSED;
    fire(checks[0][0]);
    assert.equal(timers.has(checks[0][0]), false);
    assert.equal(timers.has(checks[1][0]), true);
    assert.equal(sockets.length, 3);
    assert.deepEqual(disconnected, [true]);
    assert.deepEqual(errors, []);

    group.readyState = group.CLOSED;
    fire(checks[1][0]);
    assert.equal(timers.has(checks[1][0]), false);
    assert.equal(sockets.length, 4);
    assert.deepEqual(errors, ["Group channel closed"]);
    assert.deepEqual(disconnected, [true]);
});

for (const kind of ["batch", "group"]) {
    test(`${kind} server closure stops polling without reopening`, t => {
        const {jatos, open, timers, sockets} = setup(t);
        const socket = open(kind);
        const checkId = [...timers].find(([, timer]) => timer.delay === jatos.channelClosedCheckInterval)[0];
        socket.receive({action: "CLOSED"});
        assert.equal(timers.has(checkId), false);
        assert.equal(sockets.length, 1);
    });
}

test("batch and group session senders keep independent counters and pending requests", t => {
    const {jatos, open} = setup(t);
    const batch = open("batch");
    const group = open("group");
    const batchPending = jatos.batchSession.set("score", 2);
    const groupPending = jatos.groupSession.set("score", 3);
    assert.equal(batch.sent.at(-1).id, 0);
    assert.equal(group.sent.at(-1).sessionActionId, 0);
    batch.receive({action: "SESSION_ACK", id: 0});
    assert.equal(batchPending.state(), "resolved");
    assert.equal(groupPending.state(), "pending");
    assert.equal(jatos.batchSession.set("score", 4).state(), "pending");
    assert.equal(batch.sent.at(-1).id, 1);
    assert.equal(jatos.groupSession.set("score", 5).state(), "rejected");
    group.receive({action: "SESSION_ACK", sessionActionId: 0});
    assert.equal(groupPending.state(), "resolved");
});


test("group joining retries with capped exponential backoff and resolves after session initialization", t => {
    const {jatos, sockets, timers, fire} = setup(t);
    const errors = [];
    const promise = jatos.joinGroup({onError: error => errors.push(error)});
    for (const delay of [1000, 2000, 4000, 8000, 8000]) {
        sockets.at(-1).close();
        assert.equal(promise.state(), "pending");
        fire([...timers].find(([, timer]) => timer.delay === delay)[0]);
    }
    sockets.at(-1).open();
    sockets.at(-1).receive({sessionVersion: 1, sessionData: {}});
    assert.equal(promise.state(), "resolved");
    assert.deepEqual(errors, []);
    assert.equal([...timers.values()].filter(timer => !timer.interval).length, 0);
});

test("group joining expires after 60 seconds even with a stalled socket", t => {
    const {jatos, sockets, timers, fire} = setup(t);
    const errors = [];
    const promise = jatos.joinGroup({onError: error => errors.push(error)});
    const socket = sockets[0];
    fire([...timers].find(([, timer]) => timer.delay === 60000)[0]);
    assert.equal(promise.state(), "rejected");
    assert.equal(errors.length, 1);
    assert.match(errors[0], /60 seconds/);
    socket.open();
    socket.receive({sessionVersion: 1});
    assert.equal(promise.state(), "rejected");
    assert.equal(timers.size, 0);
});

for (const pendingSocket of [true, false]) {
    test(`leaving cancels group joining with ${pendingSocket ? "a pending socket" : "a scheduled retry"}`, t => {
        const {jatos, sockets, timers, requests} = setup(t);
        const promise = jatos.joinGroup();
        if (!pendingSocket) sockets[0].close();
        jatos.leaveGroup();
        assert.equal(promise.state(), "rejected");
        assert.equal(timers.size, 0);
        assert.equal(requests.length, 1);
    });
}

for (const operation of ["startComponent", "endStudy", "abortStudy"]) {
    test(`${operation} cancels scheduled group joining before waiting for HTTP requests`, t => {
        const {jatos, channels, sockets, timers, state} = setup(t);
        installStudyRunApi(jatos, {
            studyRunState: state, isInitialized: () => true,
            cancelChannelOpenings: channels.cancelChannelOpenings,
            getURL: path => path, removeBeforeUnloadWarning: () => {},
            httpLoop: {isBusy: () => false, whenIdle: () => {}}
        });
        jatos.setStudySessionData = () => {};
        const promise = jatos.joinGroup();
        sockets[0].close();
        if (operation === "startComponent") jatos.startComponent("next-component");
        else jatos[operation]();
        assert.equal(promise.state(), "rejected");
        assert.equal(timers.size, 0);
    });
}


test("batch initial opening keeps one promise across failures until the session arrives", t => {
    const {channels, sockets, timers, fire} = setup(t);
    const promise = channels.openBatchChannelWithRetry();
    sockets[0].close();
    assert.equal(promise.state(), "pending");
    fire([...timers].find(([, timer]) => timer.delay === 2000)[0]);
    sockets[1].open();
    assert.equal(promise.state(), "pending");
    sockets[1].receive({version: 1, data: {}});
    assert.equal(promise.state(), "resolved");
    assert.equal([...timers.values()].some(timer => timer.delay === 120000), false);
});

test("batch initial opening times out and ignores late socket events", t => {
    const {channels, sockets, timers, fire} = setup(t);
    const promise = channels.openBatchChannelWithRetry();
    fire([...timers].find(([, timer]) => timer.delay === 120000)[0]);
    assert.equal(promise.state(), "rejected");
    sockets[0].open();
    sockets[0].receive({version: 1});
    assert.equal(promise.state(), "rejected");
    assert.equal(timers.size, 0);
});

for (const kind of ["batch", "group"]) {
    test(`${kind} reconnecting retries without an opening deadline`, t => {
        const {jatos, open, sockets, timers, fire} = setup(t);
        open(kind).close();
        fire([...timers].find(([, timer]) => timer.interval && timer.delay === jatos.channelClosedCheckInterval)[0]);
        for (let i = 0; i < 10; i++) {
            sockets.at(-1).close();
            const scheduled = [...timers].filter(([, timer]) => !timer.interval);
            assert.equal(scheduled.length, 1, "only a retry timer, no deadline");
            fire(scheduled[0][0]);
        }
        assert.equal(sockets.length, 12);
    });
}
