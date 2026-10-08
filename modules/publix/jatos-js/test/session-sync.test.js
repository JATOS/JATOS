import assert from "node:assert/strict";
import test from "node:test";
import {createSessionSync} from "../src/session-sync.js";
import {createSessionApi} from "../src/session-api.js";

function setup(t) {
    const timers = new Map();
    let id = 0;
    let requests = 0;
    t.mock.method(globalThis, "setTimeout", (callback, delay) => {
        timers.set(++id, {callback, delay});
        return id;
    });
    t.mock.method(globalThis, "clearTimeout", id => timers.delete(id));
    const sync = createSessionSync({requestFullSession: () => requests++, getTimeout: () => 1000});
    function fire(delay) {
        const [id, timer] = [...timers].find(([, timer]) => timer.delay === delay);
        timers.delete(id);
        timer.callback();
    }
    return {sync, timers, fire, requests: () => requests};
}

test("synchronizer owns data and version and calls observers only after ordered application", t => {
    const {sync, timers} = setup(t);
    const observed = [];
    const observe = () => observed.push([sync.getData().score, sync.getVersion()]);
    sync.receive({version: 4, fullSession: {score: 4}}, observe);
    sync.receive({version: 6, patches: [{op: "replace", path: "/score", value: 6}]}, observe);
    assert.deepEqual(observed, [[4, 4]]);
    sync.receive({version: 5, patches: [{op: "replace", path: "/score", value: 5}]}, observe);
    assert.deepEqual(observed, [[4, 4], [5, 5], [6, 6]]);
    sync.receive({version: 5, fullSession: {score: -1}}, observe);
    assert.equal(observed.length, 3);
    assert.equal(timers.size, 0);
});

test("version-only observations request a full session through the injected transport", t => {
    const {sync, fire, requests, timers} = setup(t);
    sync.receive({version: 1, fullSession: {score: 1}});
    sync.observeVersion(3);
    let ready = false;
    sync.waitFor(3, error => { assert.equal(error, undefined); ready = true; });
    assert.equal(sync.getVersion(), 1);
    assert.equal(requests(), 0);
    fire(250);
    assert.equal(requests(), 1);
    assert.equal(ready, false);
    sync.receive({version: 3, fullSession: {score: 3}});
    assert.equal(ready, true);
    assert.equal(timers.size, 0);
});

test("cancel retains data while reset clears it, and both stop pending synchronization", t => {
    const {sync, timers} = setup(t);
    sync.receive({version: 1, fullSession: {score: 1}});
    const errors = [];
    sync.waitFor(2, error => errors.push(error));
    sync.cancel();
    assert.equal(sync.getData().score, 1);
    assert.equal(sync.getVersion(), 1);
    sync.waitFor(2, error => errors.push(error));
    sync.reset();
    assert.deepEqual(sync.getData(), {});
    assert.equal(sync.getVersion(), null);
    assert.equal(errors.length, 2);
    assert.ok(errors.every(error => typeof error === "string"));
    assert.equal(timers.size, 0);
});

test("session API reads live cloned data and delegates writes without changing local state", t => {
    const {sync} = setup(t);
    sync.receive({version: 1, fullSession: {nested: {score: 1}}});
    const writes = [];
    const result = {};
    const api = createSessionApi(sync.getData, (...args) => { writes.push(args); return result; });
    api.get("nested").score = 99;
    assert.equal(api.find("/nested/score"), 1);
    const success = () => {};
    const failure = () => {};
    assert.equal(api.set("score", 2, success, failure), result);
    assert.deepEqual(writes, [[{op: "add", path: "/score", value: 2}, success, failure]]);
    assert.equal(api.get("score"), undefined);
    sync.receive({version: 2, fullSession: {score: 2}});
    assert.deepEqual(api.getAll(), {score: 2});
});
