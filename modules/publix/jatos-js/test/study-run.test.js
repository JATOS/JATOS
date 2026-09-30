import assert from "node:assert/strict";
import test from "node:test";

import {
    installStudyRunApi,
    isInvalidComponentPosition
} from "../src/study-run.js";

import {createLegacyPromiseCompatibility} from "../src/jatos-promise.js";

const {createDeferred, rejectedPromise} = createLegacyPromiseCompatibility();

// Component navigation

test("component position validation uses the current component list", () => {
    const components = [{}, {}];

    assert.equal(isInvalidComponentPosition(components, 0), true);
    assert.equal(isInvalidComponentPosition(components, 1), false);
    assert.equal(isInvalidComponentPosition(components, 2), false);
    assert.equal(isInvalidComponentPosition(components, 3), true);
});

test("position and title helpers delegate to startComponent", t => {
    const {jatos} = createNavigationApi(t);
    const calls = [];
    const resultData = {score: 7, completed: true};
    const message = "Continue to the second component";
    const onError = () => {};
    jatos.startComponent = (...args) => calls.push(args);

    jatos.startComponentByPos(2, resultData, message);
    jatos.startComponentByTitle("First", resultData, onError);

    assert.deepEqual(calls, [
        ["second-uuid", resultData, message, undefined],
        ["first-uuid", resultData, onError, undefined]
    ]);
});

test("startComponent reads live guards and waits for pending HTTP work", t => {
    let initialized = false;
    let starting = false;
    let waitCallback;
    const errors = [];
    const {jatos, dependencies, location} = createNavigationApi(t, {
        isInitialized: () => initialized,
        isStartingComponent: () => starting,
        setStartingComponent: value => { starting = value; },
        httpLoop: {
            isBusy: () => true,
            whenIdle: callback => { waitCallback = callback; }
        }
    });

    jatos.startComponent("second-uuid", "data", error => errors.push(error));
    assert.equal(starting, false);

    initialized = true;
    jatos.startComponent("second-uuid", "data", error => errors.push(error));
    assert.equal(starting, true);
    assert.equal(location.href, "unchanged");

    waitCallback();
    assert.equal(location.href, "https://example.test/../second-uuid/start");
    assert.deepEqual(errors, []);
    assert.equal(dependencies.appendCalls(), 1);
    assert.equal(dependencies.sessionCalls(), 1);
});

function createNavigationApi(t, overrides = {}) {
    const previousWindow = globalThis.window;
    t.after(() => { globalThis.window = previousWindow; });
    t.mock.method(globalThis, "setTimeout", () => {});
    t.mock.method(console, "error", () => {});
    const location = {href: "unchanged"};
    globalThis.window = {
        location,
        removeEventListener: () => {}
    };

    let appendCalls = 0;
    let sessionCalls = 0;
    const jatos = {
        appendResultData: () => { appendCalls++; },
        componentList: [
            {id: 1, uuid: "first-uuid", title: "First", position: 1, active: true},
            {id: 2, uuid: "second-uuid", title: "Second", position: 2, active: true}
        ],
        componentPos: 1,
        jQuery: {param: () => ""},
        jatosRun: "GENERAL_MULTIPLE",
        showOverlay: () => {},
        studySessionData: {},
        waitSendDataOverlayConfig: {}
    };
    const dependencies = {
        beforeUnloadWarning: () => {},
        getURL: path => `https://example.test/${path}`,
        httpLoop: {isBusy: () => false, whenIdle: callback => callback()},
        isEndingStudy: () => false,
        isInitialized: () => true,
        isStartingComponent: () => false,
        isStudyRunInvalid: () => false,
        setStartingComponent: () => {},
        ...overrides
    };

    installStudyRunApi(jatos, dependencies);

    jatos.setStudySessionData = () => { sessionCalls++; };

    dependencies.appendCalls = () => appendCalls;
    dependencies.sessionCalls = () => sessionCalls;
    return {jatos, dependencies, location};
}

// Study session persistence

test("study session persistence updates local data and queues a JSON snapshot", () => {
    const calls = [];
    const promise = {};
    const jatos = {studySessionData: {old: true}};
    installStudyRunApi(jatos, {
        getURL: path => `https://example.test/${path}`,
        httpLoop: {send: (...args) => {
            calls.push(args);
            assert.equal(jatos.studySessionData, data);
            return {promise: () => promise};
        }}
    });
    // Settings are read at call time, including changes made after installation.
    Object.assign(jatos, {httpTimeout: 1000, httpRetry: 2, httpRetryWait: 500});
    const data = {score: 7, nested: {completed: true}};
    const onSuccess = () => {};
    const onFail = () => {};

    assert.equal(jatos.setStudySessionData(data, onSuccess, onFail), promise);
    data.score = 8;
    assert.deepEqual(calls, [[{
        url: "https://example.test/../studySessionData",
        data: '{"score":7,"nested":{"completed":true}}',
        method: "POST",
        contentType: "text/plain; charset=UTF-8",
        timeout: 1000,
        retry: 2,
        retryWait: 500
    }, onSuccess, onFail]]);
});

test("serialization failure still replaces local session data without sending", () => {
    const jatos = {studySessionData: {old: true}};
    installStudyRunApi(jatos, {
        getURL: () => assert.fail("must not resolve a URL"),
        httpLoop: {send: () => assert.fail("must not queue a request")}
    });
    const data = {};
    data.circular = data;

    assert.throws(() => jatos.setStudySessionData(data), TypeError);
    assert.equal(jatos.studySessionData, data);
});

// Study lifecycle

function createLifecycleApi(t) {
    const events = [];
    const requests = [];
    const timers = [];
    const state = {initialized: true, invalid: false, ending: false};
    const window = {location: {href: "unchanged"}, removeEventListener: () => events.push("remove-listener")};
    window.self = window.top = window;
    const previousWindow = globalThis.window;
    globalThis.window = window;
    t.after(() => { globalThis.window = previousWindow; });
    t.mock.method(globalThis, "setTimeout", (callback, delay, ...args) => {
        timers.push(() => callback(...args));
    });
    t.mock.method(console, "warn", () => {});
    t.mock.method(console, "error", () => {});
    let idleCallback;
    const jatos = {
        httpTimeout: 1000, httpRetry: 2, httpRetryWait: 500,
        jQuery: {param: obj => new URLSearchParams(obj).toString()},
        appendResultData: data => events.push(["append", data]),
        showBeforeUnloadWarning: value => events.push(["warning", value]),
        showOverlay: () => events.push("overlay"),
        removeOverlays: () => events.push("remove-overlays")
    };
    installStudyRunApi(jatos, {
        getURL: path => `https://example.test/${path}`,
        isInitialized: () => state.initialized,
        isStudyRunInvalid: () => state.invalid,
        isEndingStudy: () => state.ending,
        isStartingComponent: () => false,
        setEndingStudy: value => { state.ending = value; },
        rejectedPromise,
        stopStudyRun: () => events.push("stop"),
        httpLoop: {
            isBusy: () => true,
            whenIdle: callback => { idleCallback = callback; },
            send: (request, onSuccess, onError) => {
                const deferred = createDeferred();
                deferred.done(onSuccess).fail(onError);
                requests.push({request, deferred});
                events.push("send");
                return deferred;
            }
        }
    });
    return {jatos, state, events, requests, timers, window, idle: () => idleCallback()};
}

for (const operation of ["end", "abort"]) {
    test(`${operation} without redirect preserves request, promise, and cleanup behavior`, t => {
        const {jatos, state, events, requests, timers} = createLifecycleApi(t);
        const success = () => events.push("success");
        const fail = () => assert.fail("unexpected error");
        const promise = operation === "end"
            ? jatos.endStudyWithoutRedirect({score: 7}, true, "all done", success, fail)
            : jatos.abortStudyWithoutRedirect("stop", success, fail);
        assert.equal(state.ending, true);
        assert.deepEqual(events, operation === "end"
            ? [["append", {score: 7}], ["warning", false], "send"]
            : [["warning", false], "send"]);
        assert.deepEqual(requests[0].request, {
            url: operation === "end" ? "https://example.test/../end?successful=true&message=all+done"
                : "https://example.test/../abort?message=stop",
            method: "GET", timeout: 1000, retry: 2, retryWait: 500
        });
        assert.equal(promise, requests[0].deferred.promise());
        assert.equal(promise.resolve, undefined);
        timers[0]();
        assert.equal(events.at(-1), "overlay");
        requests[0].deferred.resolve("ok");
        assert.deepEqual(events.slice(-4), ["success", "remove-listener", "stop", "remove-overlays"]);
        const count = events.length;
        timers[0]();
        assert.equal(events.length, count);
    });

    test(`${operation} guards use current state and do not send requests`, t => {
        const {jatos, state, requests} = createLifecycleApi(t);
        for (const flag of ["initialized", "invalid", "ending"]) {
            Object.assign(state, {initialized: true, invalid: false, ending: false});
            state[flag] = flag !== "initialized";
            const promise = operation === "end" ? jatos.endStudyWithoutRedirect(true)
                : jatos.abortStudyWithoutRedirect();
            assert.equal(promise.state(), "rejected");
        }
        assert.equal(requests.length, 0);
    });

    test(`${operation} failure removes overlays without cleanup or redirect`, t => {
        const {jatos, events, requests, window} = createLifecycleApi(t);
        const fail = error => events.push(error);
        if (operation === "end") jatos.endStudyAndRedirect("/finished", true, "done", undefined, fail);
        else jatos.abortStudyAndRedirect("/finished", "stop", undefined, fail);
        requests[0].deferred.reject("failed");
        assert.deepEqual(events.slice(-2), ["failed", "remove-overlays"]);
        assert.equal(events.includes("stop"), false);
        assert.equal(window.location.href, "unchanged");
    });

    test(`${operation} custom redirect waits for successful completion`, t => {
        const {jatos, requests, window} = createLifecycleApi(t);
        if (operation === "end") jatos.endStudyAndRedirect("/finished", true);
        else jatos.abortStudyAndRedirect("/finished");
        assert.equal(window.location.href, "unchanged");
        requests[0].deferred.resolve();
        assert.equal(window.location.href, "/finished");
    });

    test(`${operation} end-page redirect waits for queued HTTP work and blocks navigation`, t => {
        const {jatos, state, events, window, idle} = createLifecycleApi(t);
        if (operation === "end") jatos.endStudy("results", false, "done");
        else jatos.abortStudy("stop");
        assert.equal(state.ending, true);
        assert.equal(window.location.href, "unchanged");
        const errors = [];
        jatos.startComponent("next", undefined, error => errors.push(error));
        assert.deepEqual(errors, ["Can't start component if study already ended."]);
        idle();
        assert.equal(window.location.href, operation === "end"
            ? "https://example.test/../end?successful=false&message=done"
            : "https://example.test/../abort?message=stop");
        assert.equal(events.includes("stop"), false);
    });

    test(`${operation} GUI iframe notifies the parent after success`, t => {
        const {jatos, requests, window, events} = createLifecycleApi(t);
        window.top = {};
        const previousParent = globalThis.parent;
        globalThis.parent = {onIframeComplete: (...args) => events.push(args)};
        t.after(() => { globalThis.parent = previousParent; });
        Object.assign(jatos, {workerType: "Jatos", urlQueryParameters: {frameId: "frame"}, studyId: 5});
        if (operation === "end") jatos.endStudy(true, "done");
        else jatos.abortStudy("stop");
        requests[0].deferred.resolve();
        assert.deepEqual(events.at(-1), ["frame", 5]);
        assert.equal(window.location.href, "unchanged");
    });
}
