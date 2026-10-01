import assert from "node:assert/strict";
import test from "node:test";
import {requestHttp, getHttpErrorMessage} from "../src/http-transport.js";

function setup(t) {
    const requests = [], timers = [];
    const previous = Object.getOwnPropertyDescriptor(globalThis, "XMLHttpRequest");
    class XHR {
        status = 0; statusText = ""; responseText = ""; headers = {}; responseHeaders = {};
        constructor() { requests.push(this); }
        open(...args) { this.openArgs = args; }
        setRequestHeader(name, value) { this.headers[name] = value; }
        getResponseHeader(name) { return this.responseHeaders[name] ?? null; }
        send(body) { this.body = body; }
        respond(status, body = "", headers = {}) {
            this.status = status; this.responseText = body; this.responseHeaders = headers;
            this.statusText = status >= 400 ? "Failure" : "OK";
            this.onload();
        }
    }
    globalThis.XMLHttpRequest = XHR;
    t.after(() => {
        if (previous) Object.defineProperty(globalThis, "XMLHttpRequest", previous);
        else delete globalThis.XMLHttpRequest;
    });
    t.mock.method(globalThis, "setTimeout", (callback, delay) => timers.push({callback, delay}));
    return {requests, timers};
}

test("JSON requests preserve headers, timeout, callback order and promise facade", t => {
    const {requests} = setup(t);
    const events = [];
    const promise = requestHttp({url: "/initData", dataType: "json", timeout: 123,
        success: data => events.push(["success", data]), statusCode: {200: () => events.push("200")}});
    promise.done(data => events.push(["done", data]));
    const xhr = requests[0];
    assert.deepEqual(xhr.openArgs, ["GET", "/initData", true]);
    assert.equal(xhr.headers["X-Requested-With"], "XMLHttpRequest");
    assert.ok(xhr.headers.Accept.includes("application/json"));
    assert.equal(xhr.timeout, 123);
    assert.equal(xhr.body, null);
    assert.equal(promise.resolve, undefined);
    xhr.respond(200, '{"value":1}');
    assert.deepEqual(events, [["success", {value: 1}], "200", ["done", {value: 1}]]);
});

for (const status of [200, 204]) {
    test(`reassignment dispatches HTTP ${status} without retrying`, t => {
        const {requests, timers} = setup(t);
        const calls = [];
        requestHttp({url: "/reassign", statusCode: {200: () => calls.push(200), 204: () => calls.push(204)}});
        requests[0].respond(status);
        assert.deepEqual(calls, [status]);
        assert.equal(timers.length, 0);
    });
}

test("retry count is total attempts and errors are reported per attempt", t => {
    const {requests, timers} = setup(t);
    const errors = [];
    const promise = requestHttp({url: "/leave", retry: {times: 2, timeout: 456}, error: response => errors.push(response.status)});
    requests[0].respond(503, "busy");
    assert.equal(promise.state(), "pending");
    assert.equal(timers[0].delay, 456);
    timers.shift().callback();
    requests[1].respond(400, "bad");
    assert.equal(promise.state(), "rejected");
    assert.deepEqual(errors, [503, 400]);
    assert.equal(requests.length, 2);
    assert.equal(timers.length, 0);
});

for (const value of ["2", "invalid", "-1", "Wed, 21 Oct 2015 07:28:00 GMT"]) {
    test(`Retry-After ${value} preserves delay or fallback`, t => {
        const {requests, timers} = setup(t);
        t.mock.method(Date, "now", () => Date.parse("Wed, 21 Oct 2015 07:27:59 GMT"));
        requestHttp({url: "/initData", retry: {times: 2, timeout: 456}});
        requests[0].respond(503, "", {"Retry-After": value});
        assert.equal(timers[0].delay, value === "2" ? 2000 : value.startsWith("Wed") ? 1000 : 456);
    });
}

for (const failure of ["timeout", "error", "abort", "parsererror"]) {
    test(`${failure} rejects with response details`, t => {
        const {requests} = setup(t);
        let received;
        const promise = requestHttp({url: "/initData", dataType: "json"});
        promise.fail((...args) => { received = args; });
        if (failure === "parsererror") requests[0].respond(200, "invalid JSON");
        else requests[0][`on${failure}`]();
        assert.equal(promise.state(), "rejected");
        assert.equal(received[1], failure);
        assert.equal(received[0].status, failure === "parsererror" ? 200 : 0);
        if (failure === "timeout") assert.equal(received[0].statusText, "timeout");
    });
}

test("retry success infers JSON while ordinary responses stay text", t => {
    const {requests, timers} = setup(t);
    let value;
    const promise = requestHttp({url: "/leave", retry: {times: 2, timeout: 1}}).done(data => { value = data; });
    requests[0].respond(500);
    timers.shift().callback();
    requests[1].respond(200, '{"left":true}', {"Content-Type": "application/json; charset=UTF-8"});
    assert.equal(promise.state(), "resolved");
    assert.deepEqual(value, {left: true});
    requestHttp({url: "/leave"}).done(data => { value = data; });
    requests[2].respond(200, "left", {"Content-Type": "text/plain"});
    assert.equal(value, "left");
});


test("HTTP error messages preserve timeout, server detail, and fallback wording", () => {
    assert.equal(getHttpErrorMessage({statusText: "timeout", responseText: "ignored"}),
        "JATOS server not responding");
    assert.equal(getHttpErrorMessage({statusText: "Forbidden", responseText: "Access denied"}),
        "Forbidden: Access denied");
    assert.equal(getHttpErrorMessage({statusText: "error", responseText: ""}),
        "error: Error during Ajax call to JATOS server.");
});
