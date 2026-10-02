import assert from "node:assert/strict";
import test from "node:test";
import {runInNewContext} from "node:vm";
import {fileURLToPath} from "node:url";
import {build} from "esbuild";

for (const minify of [false, true]) {
    test(`heartbeat worker preserves scheduling and messages (minify=${minify})`, async () => {
        const result = await build({
            entryPoints: [fileURLToPath(new URL("../src/workers/heartbeat.js", import.meta.url))],
            bundle: true, format: "iife", platform: "browser", target: "es2020",
            minify, write: false, logLevel: "silent"
        });
        const requests = [];
        const timers = [];
        const context = {
            onmessage: null,
            XMLHttpRequest: class {
                constructor() { requests.push(this); }
                open(method, url) { this.method = method; this.url = url; }
                setRequestHeader(name, value) { this.header = [name, value]; }
                send() { this.sent = true; }
            },
            setTimeout: (callback, delay) => timers.push({callback, delay})
        };
        runInNewContext(result.outputFiles[0].text, context);
        assert.equal(requests.length, 0);
        context.onmessage({data: ["run-uuid"]});
        assert.equal(requests.length, 1);
        assert.equal(requests[0].method, "POST");
        assert.equal(requests[0].url, "../../../../run-uuid/heartbeat");
        assert.deepEqual(requests[0].header, ["Content-Type", "text/plain"]);
        assert.equal(requests[0].sent, true);
        assert.equal(timers.length, 0);
        requests[0].onload();
        assert.equal(timers[0].delay, 60000);
        timers.shift().callback();
        assert.equal(requests.length, 2);

        // Updating the configuration does not start another heartbeat loop.
        context.onmessage({data: ["next-uuid", 1234]});
        assert.equal(requests.length, 2);
        requests[1].onload();
        assert.equal(timers[0].delay, 1234);
        timers.shift().callback();
        assert.equal(requests[2].url, "../../../../next-uuid/heartbeat");
    });
}
