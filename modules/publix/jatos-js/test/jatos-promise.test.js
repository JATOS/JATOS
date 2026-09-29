import assert from "node:assert/strict";
import test from "node:test";

import {createLegacyPromiseCompatibility} from "../src/jatos-promise.js";

const {createDeferred, rejectedPromise} = createLegacyPromiseCompatibility();

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

    deferred.done((...values) => {
        args = values;
        events.push("done-1");
    });
    deferred.always(() => events.push("always"));
    deferred.done(() => events.push("done-2"));
    deferred.resolve("one", "two");
    deferred.reject("ignored");
    deferred.resolve("ignored");

    assert.equal(deferred.state(), "resolved");
    assert.deepEqual(args, ["one", "two"]);
    assert.deepEqual(events, ["done-1", "always", "done-2"]);

    let late = false;
    deferred.done(() => { late = true; });
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
