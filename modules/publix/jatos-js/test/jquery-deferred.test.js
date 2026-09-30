import assert from "node:assert/strict";
import test from "node:test";
import {Deferred} from "../src/vendor/jquery-deferred.js";

// Smoke-test the extracted module's wiring, not the full upstream implementation.
test("extracted Deferred keeps callbacks synchronous and then asynchronous", async () => {
    const deferred = Deferred();
    const events = [];
    deferred.done(() => events.push("done"));
    const chained = deferred.then(() => events.push("then"));

    deferred.resolve();
    events.push("returned");
    assert.deepEqual(events, ["done", "returned"]);
    await chained;
    assert.deepEqual(events, ["done", "returned", "then"]);
});

test("extracted callbacks preserve context, multiple arguments, and memory", () => {
    const deferred = Deferred();
    const context = {};
    deferred.resolveWith(context, ["value", 42]);
    let received;
    deferred.done(function (...args) { received = {context: this, args}; });

    assert.equal(received.context, context);
    assert.deepEqual(received.args, ["value", 42]);
});

test("extracted progress callbacks remember notifications and lock on settlement", () => {
    const deferred = Deferred();
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

test("extracted pipe filters synchronously and adopts Deferred results", () => {
    const source = Deferred();
    const adopted = Deferred();
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
