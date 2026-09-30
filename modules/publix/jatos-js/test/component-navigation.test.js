import assert from "node:assert/strict";
import test from "node:test";

import {
    installComponentNavigationApi,
    isInvalidComponentPosition
} from "../src/component-navigation.js";

test("component position validation uses the current component list", () => {
    const components = [{}, {}];

    assert.equal(isInvalidComponentPosition(components, 0), true);
    assert.equal(isInvalidComponentPosition(components, 1), false);
    assert.equal(isInvalidComponentPosition(components, 2), false);
    assert.equal(isInvalidComponentPosition(components, 3), true);
});

test("position and title helpers delegate to startComponent", () => {
    const {jatos} = createNavigationApi();
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

test("startComponent reads live guards and waits for pending HTTP work", () => {
    let initialized = false;
    let starting = false;
    let waitCallback;
    const errors = [];
    const {jatos, dependencies, location} = createNavigationApi({
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

function createNavigationApi(overrides = {}) {
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
        setStudySessionData: () => { sessionCalls++; },
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

    installComponentNavigationApi(jatos, dependencies);

    dependencies.appendCalls = () => appendCalls;
    dependencies.sessionCalls = () => sessionCalls;
    return {jatos, dependencies, location};
}
