import assert from "node:assert/strict";
import test from "node:test";

import {createInitialization} from "../src/initialization.js";
import {createDeferred} from "../src/jatos-promise.js";


function initData() {
    return {
        batchProperties: {title: "Batch", batchInput: '{"batch":1}'},
        studySessionData: '{"score":7}',
        studyProperties: {title: "Study", studyInput: '{"study":2}'},
        componentList: [{id: 1}, {id: 2}],
        componentProperties: {title: "Component", componentInput: '{"component":3}'},
        urlQueryParameters: {frameId: "frame-1", custom: "value"},
        studyCode: "code"
    };
}

function setup(t, cookie = "other=value; JATOS_ID_old=studyResultUuid=other&componentPos=9; JATOS_ID_current=studyResultUuid=run-uuid&componentPos=2&studyId=5&label=a%3Db") {
    const tasks = [];
    t.mock.method(globalThis, "setTimeout", callback => tasks.push(callback));
    function flush() {
        let remaining = 100;
        while (tasks.length) {
            assert.ok(remaining-- > 0, "startup callbacks must settle");
            tasks.shift()();
        }
    }
    t.after(flush);
    const events = [];
    const workers = [];
    const requests = [];
    const errors = [];
    const batch = createDeferred();
    const window = new EventTarget();
    window.location = {pathname: "/publix/run-uuid/component-uuid/start"};
    const document = {cookie};
    class Worker {
        messages = [];
        constructor(url) { this.url = url; workers.push(this); events.push("worker"); }
        postMessage(message) { this.messages.push(message); events.push("heartbeat"); }
        terminate() { events.push("terminate-heartbeat"); }
    }
    for (const [name, value] of Object.entries({window, document, Worker})) {
        const descriptor = Object.getOwnPropertyDescriptor(globalThis, name);
        Object.defineProperty(globalThis, name, {value, writable: true, configurable: true});
        t.after(() => {
            if (descriptor) Object.defineProperty(globalThis, name, descriptor);
            else delete globalThis[name];
        });
    }
    t.mock.method(console, "error", error => errors.push(error));
    const jatos = {studyProperties: {}, studySessionData: {}, httpTimeout: 123, httpRetry: 2, httpRetryWait: 456};
    const initialization = createInitialization(jatos, {
        requestHttp: options => {
            events.push("init-data");
            const deferred = createDeferred();
            deferred.done(options.success).fail(options.error);
            const request = {options, deferred};
            requests.push(request);
            return deferred.promise();
        },

        getURL: path => `https://example.test/${path}`,
        showIdOverlay: () => { events.push("ids"); },
        httpLoop: {start: () => events.push("http-start")},
        channels: {openBatchChannelWithRetry: () => { events.push("batch-open"); return batch.promise(); }}
    });
    function start() {
        initialization.start();
        flush();
    }
    return {jatos, initialization, workers, requests, batch, events, errors, start, flush};
}

test("startup loads dependencies, starts workers, reads init data, and waits for the batch channel", t => {
    const {jatos, initialization, workers, requests, batch, events, flush} = setup(t);
    const callbacks = [];
    jatos.onLoad(event => callbacks.push(["first", event.type, initialization.isInitialized()]));
    jatos.onload(() => callbacks.push(["second"]));
    assert.equal(jatos.onload, jatos.onLoad);
    assert.equal(initialization.isInitialized(), false);
    initialization.start();
    assert.equal(workers.length, 0);
    flush();
    assert.equal(jatos.studyResultUuid, "run-uuid");
    assert.equal(jatos.componentPos, 2);
    assert.equal(jatos.studyId, "5");
    assert.equal(jatos.label, "a=b");
    assert.equal(workers[0].url, "jatos-publix/javascripts/heartbeat.js");
    assert.deepEqual(workers[0].messages, [["run-uuid"]]);
    assert.equal(requests[0].options.url, "https://example.test/initData");
    assert.equal(requests[0].options.method, "GET");
    assert.equal(requests[0].options.dataType, "json");
    assert.equal(requests[0].options.timeout, 123);
    assert.deepEqual(requests[0].options.retry, {times: 2, timeout: 456});
    requests[0].deferred.resolve(initData());
    flush();
    assert.deepEqual(events, ["worker", "heartbeat", "http-start", "init-data", "ids", "batch-open"]);
    assert.equal(initialization.isInitialized(), false);
    assert.deepEqual(callbacks, []);
    batch.resolve();
    flush();
    assert.deepEqual(callbacks, [["first", "jatosOnLoad", true], ["second"]]);
    jatos.onLoad((...args) => callbacks.push(["late", ...args]));
    assert.deepEqual(callbacks.at(-1), ["late"]);
    batch.resolve();
    flush();
    assert.equal(callbacks.length, 3);
});

test("init data populates properties, input aliases, session, components and query parameters", t => {
    const {jatos, start, requests} = setup(t);
    start();
    const data = initData();
    requests[0].deferred.resolve(data);
    for (const kind of ["batch", "study", "component"]) {
        assert.equal(jatos[`${kind}Input`], jatos[`${kind}JsonInput`]);
        assert.equal(Object.hasOwn(jatos[`${kind}Properties`], `${kind}Input`), false);
    }
    assert.deepEqual(jatos.batchInput, {batch: 1});
    assert.deepEqual(jatos.studyInput, {study: 2});
    assert.deepEqual(jatos.componentInput, {component: 3});
    assert.deepEqual(jatos.studySessionData, {score: 7});
    assert.equal(jatos.componentList, data.componentList);
    assert.equal(jatos.studyLength, 2);
    assert.equal(jatos.urlQueryParameters, data.urlQueryParameters);
    assert.equal(jatos.frameId, "frame-1");
    assert.equal(jatos.studyCode, "code");
});

test("missing inputs and invalid session JSON retain the existing fallback behavior", t => {
    const {jatos, start, requests, errors} = setup(t);
    start();
    const data = initData();
    delete data.batchProperties.batchInput;
    data.studyProperties.studyInput = null;
    data.componentProperties.componentInput = null;
    data.studySessionData = "invalid JSON";
    data.urlQueryParameters = {};
    requests[0].deferred.resolve(data);
    assert.deepEqual(jatos.batchInput, {});
    assert.deepEqual(jatos.studyInput, {});
    assert.deepEqual(jatos.componentInput, {});
    assert.deepEqual(jatos.studySessionData, {});
    assert.equal(jatos.frameId, undefined);
    assert.equal(errors.length, 1);
});

for (const stage of ["init-data", "batch"]) {
    test(`${stage} failure still marks initialization complete and notifies onLoad`, t => {
        const {jatos, initialization, workers, requests, batch, events, errors, flush} = setup(t);
        let calls = 0;
        jatos.onLoad(() => { calls++; });
        initialization.start();
        flush();
        if (stage === "init-data") {
            requests[0].deferred.reject({statusText: "timeout"});
            assert.deepEqual(errors, ["JATOS server not responding"]);
            assert.equal(events.includes("batch-open"), false);
        } else {
            requests[0].deferred.resolve(initData());
            batch.reject("channel failed");
        }
        flush();
        assert.equal(initialization.isInitialized(), true);
        assert.equal(calls, 1);
        jatos.onload(() => { calls++; });
        assert.equal(calls, 2);
    });
}

test("missing ID cookie logs an error but does not stop startup", t => {
    const {jatos, start, requests, errors} = setup(t, "unrelated=value");
    start();
    assert.equal(jatos.studyResultUuid, "run-uuid");
    assert.equal(requests.length, 1);
    assert.match(errors[0], /cookie for current studyResultUuid not found/);
});

test("heartbeat period changes and lifecycle termination reach the current worker", t => {
    const {jatos, initialization, start, workers, events} = setup(t);
    jatos.setHeartbeatPeriod(500);
    start();
    jatos.setHeartbeatPeriod("ignored");
    jatos.setHeartbeatPeriod(750);
    assert.deepEqual(workers[0].messages, [["run-uuid"], ["run-uuid", 750]]);
    initialization.terminateHeartbeat();
    assert.equal(events.at(-1), "terminate-heartbeat");
});
