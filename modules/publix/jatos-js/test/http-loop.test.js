import assert from "node:assert/strict";
import test from "node:test";
import {createHttpLoop} from "../src/http-loop.js";

function setup(t) {
    let worker;
    const descriptor = Object.getOwnPropertyDescriptor(globalThis, "Worker");
    globalThis.Worker = class {
        messages = [];
        constructor() { worker = this; }
        addEventListener(type, callback) { this.callback = callback; }
        postMessage(message) { this.messages.push(message); }
        respond(index, status) {
            const request = this.messages[index];
            this.callback({data: {requestId: request.id, status, method: request.method, url: request.url}});
        }
    };
    t.after(() => {
        if (descriptor) Object.defineProperty(globalThis, "Worker", descriptor);
        else delete globalThis.Worker;
    });
    const loop = createHttpLoop({isInitialized: () => true});
    loop.start();
    return {loop, worker};
}

for (const status of [200, 500]) {
    for (const callbackKind of ["request", "promise"]) {
        test(`HTTP queue becomes idle after a throwing ${callbackKind} callback (${status})`, t => {
            const {loop, worker} = setup(t);
            const error = new Error("callback failed");
            const events = [];
            const callback = () => { events.push("callback"); throw error; };
            const deferred = loop.send({method: "GET", url: "/test"},
                callbackKind === "request" && status === 200 ? callback : undefined,
                callbackKind === "request" && status !== 200 ? callback : undefined);
            if (callbackKind === "promise") deferred[status === 200 ? "done" : "fail"](callback);
            loop.whenIdle(() => events.push("idle"));
            assert.throws(() => worker.respond(0, status), value => value === error);
            assert.equal(deferred.state(), status === 200 ? "resolved" : "rejected");
            assert.equal(loop.isBusy(), false);
            assert.deepEqual(events, ["callback", "idle"]);
            loop.send({method: "GET", url: "/next"});
            assert.equal(loop.isBusy(), true);
            worker.respond(1, 200);
            assert.equal(loop.isBusy(), false);
        });
    }
    test(`HTTP queue stays busy for work added by a throwing callback (${status})`, t => {
        const {loop, worker} = setup(t);
        const error = new Error("callback failed");
        let idleCalls = 0;
        const callback = () => {
            loop.send({method: "GET", url: "/next"});
            throw error;
        };
        loop.send({method: "GET", url: "/test"},
            status === 200 ? callback : undefined, status !== 200 ? callback : undefined);
        loop.whenIdle(() => idleCalls++);
        assert.throws(() => worker.respond(0, status), value => value === error);
        assert.equal(loop.isBusy(), true);
        assert.equal(idleCalls, 0);
        worker.respond(1, 200);
        assert.equal(loop.isBusy(), false);
        assert.equal(idleCalls, 1);
    });
}
