import assert from "node:assert/strict";
import test from "node:test";
import {installLoggingApi} from "../src/logging.js";

function setup() {
    const requests = [];
    const state = {initialized: false};
    const jatos = {httpTimeout: 1000, httpRetry: 2, httpRetryWait: 300};
    installLoggingApi(jatos, {
        getURL: path => `https://example.test/component/${path}`,
        isInitialized: () => state.initialized,
        sendToHttpLoop: request => requests.push(request)
    });
    return {jatos, state, requests};
}

test("logging waits for initialization and uses current HTTP settings", () => {
    const {jatos, state, requests} = setup();
    assert.equal(jatos.log("early"), undefined);
    assert.equal(requests.length, 0);
    state.initialized = true;
    jatos.httpTimeout = 5000;
    assert.equal(jatos.log("message"), undefined);
    assert.deepEqual(requests, [{
        url: "https://example.test/component/log", method: "POST", data: "message",
        contentType: "text/plain; charset=UTF-8", timeout: 5000, retry: 2, retryWait: 300
    }]);
});

test("error capture forwards browser events and preserves original console calls", t => {
    const {jatos, state, requests} = setup();
    state.initialized = true;
    const listeners = {};
    const previousWindow = globalThis.window;
    globalThis.window = {addEventListener: (name, callback) => { listeners[name] = callback; }};
    t.after(() => {
        if (previousWindow === undefined) delete globalThis.window;
        else globalThis.window = previousWindow;
    });
    const calls = [];
    for (const method of ["error", "warn"]) {
        t.mock.method(console, method, function (...args) { calls.push({method, receiver: this, args}); });
    }
    jatos.catchAndLogErrors();
    listeners.error({filename: "study.js", lineno: 7, message: "failed"});
    listeners.unhandledrejection({filename: "study.js", lineno: 8, message: "rejected"});
    const detail = {value: 1};
    console.error("error", detail);
    console.warn("warning", detail);
    assert.deepEqual(requests.map(request => request.data), [
        "Via 'error' event in study.js:7 - failed",
        "Via 'unhandledrejection' event in study.js:8 - rejected",
        "Via console.error - error", "Via console.warn - warning"
    ]);
    assert.deepEqual(calls.map(call => [call.method, call.args]), [
        ["error", ["error", detail]], ["warn", ["warning", detail]]
    ]);
    assert.ok(calls.every(call => call.receiver === console));
});

test("deprecated logging APIs only warn", t => {
    const {jatos, requests} = setup();
    const warnings = [];
    t.mock.method(console, "warn", message => warnings.push(message));
    jatos.logError("old message");
    jatos.onError(() => assert.fail("deprecated callback must not be registered"));
    assert.deepEqual(warnings, [
        "jatos.logError is abolished - use jatos.log instead",
        "jatos.onError is abolished - use the specific function's error callback or Promise function"
    ]);
    assert.deepEqual(requests, []);
});
