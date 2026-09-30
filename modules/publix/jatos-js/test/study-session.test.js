import assert from "node:assert/strict";
import test from "node:test";

import {installStudySessionApi} from "../src/study-session.js";

test("study session persistence updates local data and queues a JSON snapshot", () => {
    const calls = [];
    const promise = {};
    const jatos = {studySessionData: {old: true}};
    installStudySessionApi(jatos, {
        getURL: path => `https://example.test/${path}`,
        sendToHttpLoop: (...args) => {
            calls.push(args);
            assert.equal(jatos.studySessionData, data);
            return {promise: () => promise};
        }
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
    installStudySessionApi(jatos, {
        getURL: () => assert.fail("must not resolve a URL"),
        sendToHttpLoop: () => assert.fail("must not queue a request")
    });
    const data = {};
    data.circular = data;

    assert.throws(() => jatos.setStudySessionData(data), TypeError);
    assert.equal(jatos.studySessionData, data);
});
