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
