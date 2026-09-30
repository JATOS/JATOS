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
    const scripts = [];
    const plugins = [];
    const workers = [];
    const requests = [];
    const errors = [];
    const batch = createDeferred();
    const window = new EventTarget();
    window.location = {pathname: "/publix/run-uuid/component-uuid/start"};
    const document = {
        cookie,
        createElement: tag => { assert.equal(tag, "script"); return {}; },
        getElementsByTagName: tag => {
            assert.equal(tag, "head");
            return [{appendChild: script => scripts.push(script), removeChild: script => {
                assert.equal(script, scripts[0]);
                events.push("remove-script");
            }}];
        }
    };
    class Worker {
        messages = [];
        constructor(url) { this.url = url; workers.push(this); events.push("worker"); }
        postMessage(message) { this.messages.push(message); events.push("heartbeat"); }
        terminate() { events.push("terminate-heartbeat"); }
    }
    const jquery = {
        noConflict: removeAll => { assert.equal(removeAll, true); events.push("no-conflict"); return jquery; },
        ajaxSetup: options => { assert.deepEqual(options, {cache: true}); events.push("ajax-setup"); },
        getScript: url => {
            const deferred = createDeferred();
            plugins.push({url, deferred});
            return deferred.promise();
        },
        when: (...promises) => {
            const combined = createDeferred();
            let remaining = promises.length;
            for (const promise of promises) {
                promise.done(() => { if (--remaining === 0) combined.resolve(); });
                promise.fail(error => combined.reject(error));
            }
            return combined.promise();
        },
        ajax: options => {
            events.push("init-data");
            const deferred = createDeferred();
            deferred.done(options.success).fail(options.error);
            const request = {options, deferred};
            requests.push(request);
            return {retry: options => { request.retry = options; return deferred.promise(); }};
        },
        parseJSON: JSON.parse
    };
    for (const [name, value] of Object.entries({window, document, Worker, jQuery: jquery})) {
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
        getURL: path => `https://example.test/${path}`,
        getAjaxErrorMsg: error => `Request failed: ${error.statusText}`,
        showIdOverlay: () => { events.push("ids"); },
        httpLoop: {start: () => events.push("http-start")},
        channels: {openBatchChannelWithRetry: () => { events.push("batch-open"); return batch.promise(); }}
    });
    function loadPlugins() {
        initialization.start();
        scripts[0].onload();
        for (const plugin of plugins) plugin.deferred.resolve();
        flush();
    }
    return {jatos, initialization, scripts, plugins, workers, requests, batch, events, errors, jquery, loadPlugins, flush};
}

test("startup loads dependencies, starts workers, reads init data, and waits for the batch channel", t => {
    const {jatos, initialization, scripts, plugins, workers, requests, batch, events, jquery, flush} = setup(t);
    const callbacks = [];
    jatos.onLoad(event => callbacks.push(["first", event.type, initialization.isInitialized()]));
    jatos.onload(() => callbacks.push(["second"]));
    assert.equal(jatos.onload, jatos.onLoad);
    assert.equal(initialization.isInitialized(), false);
    initialization.start();
    assert.equal(scripts[0].src, "jatos-publix/javascripts/jquery-3.7.1.min.js");
    scripts[0].onload();
    assert.equal(jatos.jQuery, jquery);
    assert.deepEqual(plugins.map(plugin => plugin.url), [
        "jatos-publix/javascripts/jquery.ajax-retry.min.js",
        "jatos-publix/javascripts/fast-json-patch.min.js"
    ]);
    plugins[0].deferred.resolve();
    flush();
    assert.equal(workers.length, 0);
    plugins[1].deferred.resolve();
    flush();
    assert.equal(jatos.studyResultUuid, "run-uuid");
    assert.equal(jatos.componentPos, 2);
    assert.equal(jatos.studyId, "5");
    assert.equal(jatos.label, "a=b");
    assert.equal(workers[0].url, "jatos-publix/javascripts/heartbeat.js");
    assert.deepEqual(workers[0].messages, [["run-uuid"]]);
    assert.equal(requests[0].options.url, "https://example.test/initData");
    assert.equal(requests[0].options.type, "GET");
    assert.equal(requests[0].options.dataType, "json");
    assert.equal(requests[0].options.timeout, 123);
    assert.deepEqual(requests[0].retry, {times: 2, timeout: 456});
    requests[0].deferred.resolve(initData());
    flush();
    assert.deepEqual(events, ["no-conflict", "ajax-setup", "remove-script", "worker", "heartbeat", "http-start", "init-data", "ids", "batch-open"]);
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

test("script loading accepts readyState and processes duplicate load events only once", t => {
    const {initialization, scripts, plugins, events} = setup(t);
    initialization.start();
    const script = scripts[0];
    const handler = script.onreadystatechange;
    script.readyState = "loading";
    handler.call(script);
    assert.equal(plugins.length, 0);
    script.readyState = "loaded";
    handler.call(script);
    script.readyState = "complete";
    handler.call(script);
    assert.equal(plugins.length, 2);
    assert.equal(script.onload, null);
    assert.equal(script.onreadystatechange, null);
    assert.equal(events.filter(event => event === "remove-script").length, 1);
});

test("init data populates properties, input aliases, session, components and query parameters", t => {
    const {jatos, loadPlugins, requests} = setup(t);
    loadPlugins();
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
    const {jatos, loadPlugins, requests, errors} = setup(t);
    loadPlugins();
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

for (const stage of ["plugins", "init-data", "batch"]) {
    test(`${stage} failure still marks initialization complete and notifies onLoad`, t => {
        const {jatos, initialization, scripts, plugins, workers, requests, batch, events, errors, flush} = setup(t);
        let calls = 0;
        jatos.onLoad(() => { calls++; });
        initialization.start();
        scripts[0].onload();
        if (stage === "plugins") {
            plugins[0].deferred.reject("script failed");
            assert.equal(workers.length, 0);
            assert.equal(requests.length, 0);
        } else {
            plugins.forEach(plugin => plugin.deferred.resolve());
            flush();
            if (stage === "init-data") {
                requests[0].deferred.reject({statusText: "timeout"});
                assert.deepEqual(errors, ["Request failed: timeout"]);
                assert.equal(events.includes("batch-open"), false);
            } else {
                requests[0].deferred.resolve(initData());
                batch.reject("channel failed");
            }
        }
        flush();
        assert.equal(initialization.isInitialized(), true);
        assert.equal(calls, 1);
        jatos.onload(() => { calls++; });
        assert.equal(calls, 2);
    });
}

test("missing ID cookie logs an error but does not stop startup", t => {
    const {jatos, loadPlugins, requests, errors} = setup(t, "unrelated=value");
    loadPlugins();
    assert.equal(jatos.studyResultUuid, "run-uuid");
    assert.equal(requests.length, 1);
    assert.match(errors[0], /cookie for current studyResultUuid not found/);
});

test("heartbeat period changes and lifecycle termination reach the current worker", t => {
    const {jatos, initialization, loadPlugins, workers, events} = setup(t);
    jatos.setHeartbeatPeriod(500);
    loadPlugins();
    jatos.setHeartbeatPeriod("ignored");
    jatos.setHeartbeatPeriod(750);
    assert.deepEqual(workers[0].messages, [["run-uuid"], ["run-uuid", 750]]);
    initialization.terminateHeartbeat();
    assert.equal(events.at(-1), "terminate-heartbeat");
});
