import assert from "node:assert/strict";
import test from "node:test";

import {createDeferred, rejectedPromise} from "../src/jatos-promise.js";


test("promise hides settlement methods and returns itself", () => {
    const deferred = createDeferred();
    const promise = deferred.promise();

    assert.equal(promise.promise(), promise);
    assert.equal(promise.resolve, undefined);
    assert.equal(promise.reject, undefined);
    assert.equal(typeof deferred.resolve, "function");
    assert.equal(typeof deferred.reject, "function");
});

test("resolve preserves arguments, callback order, and first settlement", () => {
    const deferred = createDeferred();
    const events = [];
    let args;
    const context = {};
    let receivedContext;

    deferred.done(function (...values) {
        receivedContext = this;
        args = values;
        events.push("done-1");
    });
    deferred.always(() => events.push("always"));
    deferred.done(() => events.push("done-2"));
    deferred.resolveWith(context, ["one", "two"]);
    deferred.reject("ignored");
    deferred.resolve("ignored");

    assert.equal(deferred.state(), "resolved");
    assert.deepEqual(args, ["one", "two"]);
    assert.equal(receivedContext, context);
    assert.deepEqual(events, ["done-1", "always", "done-2"]);

    let late = false;
    deferred.done(function (...values) {
        late = true;
        assert.equal(this, context);
        assert.deepEqual(values, ["one", "two"]);
    });
    assert.equal(late, true);
});

test("reject preserves arguments and first settlement", () => {
    const deferred = createDeferred();
    let args;

    deferred.fail((...values) => { args = values; });
    deferred.reject("reason", 409);
    deferred.resolve("ignored");
    deferred.reject("ignored");

    assert.equal(deferred.state(), "rejected");
    assert.deepEqual(args, ["reason", 409]);
});

test("then transforms values and adopts thenables", async () => {
    const deferred = createDeferred();
    const transformed = deferred.promise()
        .then((first, second) => first + second)
        .then(value => Promise.resolve(value * 2));

    deferred.resolve(2, 3);

    assert.equal(await transformed, 10);
});

test("catch transforms rejection and thrown handlers reject", async () => {
    assert.equal(await rejectedPromise("bad").catch(reason => reason + " recovered"),
        "bad recovered");

    const deferred = createDeferred();
    const thrown = deferred.promise().then(() => { throw new Error("boom"); });
    deferred.resolve();

    await assert.rejects(Promise.resolve(thrown), /boom/);
});

for (const settlement of ["resolve", "reject"]) {
    test(`adopting a JatosPromise can ${settlement} without endlessly scheduling callbacks`, t => {
        const tasks = [];
        t.mock.method(globalThis, "setTimeout", callback => tasks.push(callback));
        const source = createDeferred();
        const adopted = createDeferred();
        const chained = source.promise().then(() => adopted.promise());
        let received;
        chained[settlement === "resolve" ? "done" : "fail"]((...args) => { received = args; });
        source.resolve();
        adopted[settlement]("result", 42);

        for (let count = 0; tasks.length && count < 20; count++) tasks.shift()();

        assert.deepEqual(received, ["result", 42]);
        assert.equal(chained.state(), settlement === "resolve" ? "resolved" : "rejected");
        assert.equal(tasks.length, 0, "settled promise adoption must stop scheduling work");
    });
}

test("callbacks remain callbacks synchronous and then asynchronous", async () => {
    const deferred = createDeferred();
    const events = [];
    deferred.done(() => events.push("done"));
    const chained = deferred.then(() => events.push("then"));

    deferred.resolve();
    events.push("returned");
    assert.deepEqual(events, ["done", "returned"]);
    await chained;
    assert.deepEqual(events, ["done", "returned", "then"]);
});

test("progress callbacks remember notifications and lock on settlement", () => {
    const deferred = createDeferred();
    const context = {};
    const events = [];
    deferred.notifyWith(context, ["upload", 50]);
    deferred.progress(function (...args) { events.push({context: this, args}); });
    assert.equal(deferred.state(), "pending");
    deferred.resolve();
    deferred.notify("ignored");

    assert.equal(events.length, 1);
    assert.equal(events[0].context, context);
    assert.deepEqual(events[0].args, ["upload", 50]);
});

test("pipe filters synchronously and adopts Deferred results", () => {
    const source = createDeferred();
    const adopted = createDeferred();
    const events = [];
    const piped = source.pipe(value => {
        events.push(value);
        return adopted.promise();
    });
    piped.progress(value => events.push(value));
    piped.done(value => events.push(value));

    source.resolve("filter");
    assert.deepEqual(events, ["filter"]);
    assert.equal(piped.state(), "pending");
    adopted.notify("progress");
    adopted.resolve("result");
    assert.deepEqual(events, ["filter", "progress", "result"]);
    assert.equal(piped.state(), "resolved");
});
