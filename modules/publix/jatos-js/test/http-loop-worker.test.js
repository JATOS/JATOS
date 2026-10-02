import assert from "node:assert/strict";
import test from "node:test";
import {runInNewContext} from "node:vm";
import {fileURLToPath} from "node:url";
import {build} from "esbuild";

test("HTTP worker uploads files using native FormData and preserves queued text requests", async () => {
    const result = await build({
        entryPoints: [fileURLToPath(new URL("../src/workers/http-loop-worker.js", import.meta.url))],
        bundle: true, format: "iife", platform: "browser", target: "es2020",
        write: false, logLevel: "silent"
    });
    const requests = [];
    const replies = [];
    const context = {
        onmessage: null, FormData, Blob,
        self: {postMessage: message => replies.push(message)},
        XMLHttpRequest: class {
            headers = {};
            constructor() { requests.push(this); }
            open(method, url) { this.method = method; this.url = url; }
            setRequestHeader(name, value) { this.headers[name] = value; }
            send(body) { this.body = body; }
        }
    };
    runInNewContext(result.outputFiles[0].text, context);
    context.onmessage({data: {
        id: 0, method: "POST", url: "/upload", filename: "results.txt",
        blob: new Blob(["study results"], {type: "text/plain"})
    }});
    context.onmessage({data: {
        id: 1, method: "POST", url: "/data", data: "queued results", contentType: "text/plain"
    }});
    assert.equal(requests.length, 1);
    const upload = requests[0];
    assert.equal(upload.method, "POST");
    assert.equal(upload.url, "/upload");
    assert.ok(upload.body instanceof FormData);
    assert.deepEqual([...upload.body.keys()], ["file"]);
    const file = upload.body.get("file");
    assert.equal(file.name, "results.txt");
    assert.equal(file.type, "text/plain");
    assert.equal(await file.text(), "study results");
    // The browser must generate the multipart Content-Type and boundary itself.
    assert.equal(upload.headers["Content-Type"], undefined);
    assert.equal(upload.headers["X-Requested-With"], "XMLHttpRequest");
    upload.status = 200;
    upload.onload();
    assert.equal(replies[0].requestId, 0);
    assert.equal(requests.length, 2);
    assert.equal(requests[1].body, "queued results");
    assert.equal(requests[1].headers["Content-Type"], "text/plain");
});

async function createWorker() {
    const result = await build({
        entryPoints: [fileURLToPath(new URL("../src/workers/http-loop-worker.js", import.meta.url))],
        bundle: true, format: "iife", platform: "browser", target: "es2020",
        write: false, logLevel: "silent"
    });
    const requests = [], replies = [], timers = [];
    const context = {
        onmessage: null,
        console: {warn() {}, error() {}},
        self: {postMessage: message => replies.push({...message})},
        setTimeout: (callback, delay) => timers.push({callback, delay}),
        XMLHttpRequest: class {
            status = 0;
            statusText = "";
            responseText = "";
            constructor() { requests.push(this); }
            open(method, url) { this.method = method; this.url = url; }
            setRequestHeader() {}
            send(body) { this.body = body; }
        }
    };
    runInNewContext(result.outputFiles[0].text, context);
    return {requests, replies, timers,
        send: (id, options = {}) => context.onmessage({data: {
            id, method: "POST", url: `/request/${id}`, data: "results", ...options
        }})};
}

for (const failure of ["http", "network", "timeout"]) {
    test(`worker exhausts retries for ${failure} failures before advancing the queue`, async () => {
        const {send, requests, replies, timers} = await createWorker();
        send(0, {retry: 2, retryWait: 123, timeout: 456});
        send(1);
        for (let attempt = 0; attempt < 3; attempt++) {
            assert.equal(requests.length, attempt + 1);
            const xhr = requests[attempt];
            assert.equal(xhr.url, "/request/0");
            assert.equal(xhr.body, "results");
            assert.equal(xhr.timeout, 456);
            if (failure === "http") {
                Object.assign(xhr, {status: 503, statusText: "Unavailable", responseText: " busy "});
                xhr.onload();
            } else {
                xhr[failure === "timeout" ? "ontimeout" : "onerror"]();
            }
            if (attempt < 2) {
                assert.deepEqual(replies, []);
                assert.equal(requests.length, attempt + 1);
                assert.equal(timers.length, 1);
                assert.equal(timers[0].delay, 123);
                timers.shift().callback();
            }
        }
        assert.equal(timers.length, 0);
        assert.deepEqual(replies, [{requestId: 0, url: "/request/0", method: "POST",
            ...(failure === "timeout" ? {error: "timeout"} : failure === "http"
                ? {status: 503, statusText: "Unavailable", error: "busy"}
                : {status: 0, statusText: "", error: null})}]);
        assert.equal(requests.length, 4);
        assert.equal(requests[3].url, "/request/1");
        requests[3].status = 200;
        requests[3].onload();
        assert.deepEqual(replies[1], {requestId: 1, status: 200});
        send(2);
        assert.equal(requests[4].url, "/request/2");
    });
}

for (const [label, status, options] of [
    ["HTTP 400", 400, {retry: 5}],
    ["HTTP 413", 413, {retry: 5}],
    ["zero retries", 500, {retry: 0}],
    ["omitted retries", 500, {}]
]) {
    test(`worker does not retry ${label}`, async () => {
        const {send, requests, replies, timers} = await createWorker();
        send(0, options);
        send(1);
        Object.assign(requests[0], {status, statusText: "Error", responseText: " rejected "});
        requests[0].onload();
        assert.equal(timers.length, 0);
        assert.equal(requests.length, 2);
        assert.equal(requests[1].url, "/request/1");
        assert.deepEqual(replies, [{requestId: 0, url: "/request/0", method: "POST",
            status, statusText: "Error", error: "rejected"}]);
    });
}

test("worker stops retrying after success and reports only the successful outcome", async () => {
    const {send, requests, replies, timers} = await createWorker();
    send(0, {retry: 3, retryWait: 10});
    requests[0].onerror();
    // Work arriving during the retry delay must not overtake the retry.
    send(1);
    assert.equal(requests.length, 1);
    timers.shift().callback();
    assert.equal(requests[1].url, "/request/0");
    requests[1].status = 200;
    requests[1].onload();
    assert.deepEqual(replies, [{requestId: 0, status: 200}]);
    assert.equal(timers.length, 0);
    assert.equal(requests[2].url, "/request/1");
});
