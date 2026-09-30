import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import {fileURLToPath} from "node:url";
import {build} from "esbuild";
import {JSDOM} from "jsdom";

const jquerySource = readFileSync(new URL("../../public/javascripts/jquery-3.7.1.min.js", import.meta.url), "utf8");
const patchSource = readFileSync(new URL("../../public/javascripts/fast-json-patch.min.js", import.meta.url), "utf8");

async function startBundle(t, minify) {
    const result = await build({
        entryPoints: [fileURLToPath(new URL("../src/index.js", import.meta.url))],
        bundle: true, format: "iife", platform: "browser", target: "es2020",
        minify, write: false, banner: {js: "var jatos;"}, logLevel: "silent"
    });
    const dom = new JSDOM("<!doctype html><html><head></head><body></body></html>", {
        url: "https://example.test/publix/run-uuid/component-uuid/start", runScripts: "outside-only"
    });
    t.after(() => dom.window.close());
    const {window} = dom;
    const workers = [];
    const sockets = [];
    window.Worker = class {
        messages = [];
        terminated = false;
        constructor(url) { this.url = url; workers.push(this); }
        addEventListener(type, handler) { assert.equal(type, "message"); this.handler = handler; }
        postMessage(message) { this.messages.push(message); }
        respond(request, status = 200) {
            this.handler({data: {requestId: request.id, status, statusText: status === 200 ? "OK" : "failure"}});
        }
        terminate() { this.terminated = true; }
    };
    window.WebSocket = class {
        OPEN = 1;
        CLOSED = 3;
        readyState = 0;
        sent = [];
        constructor(url) {
            this.url = url;
            sockets.push(this);
            window.setTimeout(() => {
                this.readyState = this.OPEN;
                this.onopen();
                this.receive(url.endsWith("/batch/open")
                    ? {action: "OPENED", version: 1, data: {}}
                    : {action: "OPENED", sessionVersion: 1, sessionData: {}, groupResultId: 8,
                        memberId: "self", members: ["self"], channels: ["self"], groupState: "STARTED"});
            });
        }
        send(message) { this.sent.push(JSON.parse(message)); }
        receive(message) { this.onmessage({data: JSON.stringify(message)}); }
        close() { this.readyState = this.CLOSED; this.onclose(); }
    };
    window.document.cookie = "JATOS_ID=studyResultUuid=run-uuid&componentPos=1&studyId=5&studyResultId=self&urlBasePath=%2F&workerType=GeneralMultiple";
    const pageJquery = {page: true};
    window.$ = window.jQuery = pageJquery;
    const beforeGlobals = new Set(Object.getOwnPropertyNames(window));
    window.eval(result.outputFiles[0].text);
    assert.deepEqual(Object.getOwnPropertyNames(window).filter(name => !beforeGlobals.has(name)), ["jatos"]);
    assert.equal(window.jQuery, pageJquery);
    assert.equal(window.$, pageJquery);
    assert.equal(window.Deferred, undefined);
    const {jatos} = window;
    const loaded = new Promise(resolve => jatos.onLoad(resolve));
    const bootstrap = window.document.querySelector("script");
    assert.ok(bootstrap.src.endsWith("/jatos-publix/javascripts/jquery-3.7.1.min.js"));

    // Load the real shipped jQuery. Stub only script transport, HTTP, and workers.
    window.eval(jquerySource);
    const jquery = window.jQuery;
    jquery.getScript = url => {
        if (url.endsWith("fast-json-patch.min.js")) window.eval(patchSource);
        return jquery.Deferred().resolve().promise();
    };
    jquery.ajax = options => {
        assert.ok(options.url.endsWith("/initData"));
        const deferred = jquery.Deferred().done(options.success).fail(options.error);
        window.setTimeout(() => deferred.resolve({
            batchProperties: {}, studySessionData: '{"score":1}', studyProperties: {},
            componentList: [{id: 1, uuid: "component-uuid", position: 1, active: true}],
            componentProperties: {reloadable: true}, urlQueryParameters: {}, studyCode: "code"
        }));
        return {retry: () => deferred.promise()};
    };
    bootstrap.onload();
    await loaded;
    assert.equal(window.jQuery, pageJquery);
    assert.equal(window.$, pageJquery);
    assert.equal(jatos.jQuery, jquery);
    assert.equal(jatos.isConnected(), true);
    assert.equal(jatos.studySessionData.score, 1);
    const httpWorker = workers.find(worker => worker.url.endsWith("http-loop-worker.js"));
    assert.ok(httpWorker);
    return {jatos, workers, sockets, httpWorker, window};
}

for (const minify of [false, true]) {
    for (const ending of ["end", "abort"]) {
        test(`${minify ? "minified" : "readable"} bundle: initialization, HTTP, channel acknowledgements and ${ending} cleanup`, {timeout: 5000}, async t => {
            const {jatos, workers, sockets, httpWorker, window} = await startBundle(t, minify);
            const warns = () => !window.dispatchEvent(new window.Event("beforeunload", {cancelable: true}));
            jatos.showBeforeUnloadWarning(true);
            assert.equal(warns(), true);
            const submitted = jatos.submitResultData({score: 2});
            assert.equal(submitted.resolve, undefined);
            assert.equal(submitted.state(), "pending");
            assert.equal(httpWorker.messages.at(-1).method, "PUT");
            httpWorker.respond(httpWorker.messages.at(-1));
            await submitted;

            let failureArgs;
            const failed = jatos.appendResultData("data");
            failed.fail((...args) => { failureArgs = args; });
            httpWorker.respond(httpWorker.messages.at(-1), 500);
            assert.equal(failed.state(), "rejected");
            assert.equal(failureArgs[1], 500);
            await assert.rejects(Promise.resolve(failed), error => typeof error === "string" && error.includes("500"));

            const session = jatos.setStudySessionData({score: 3});
            assert.ok(httpWorker.messages.at(-1).url.endsWith("/studySessionData"));
            assert.deepEqual(JSON.parse(httpWorker.messages.at(-1).data), {score: 3});
            httpWorker.respond(httpWorker.messages.at(-1));
            await session;

            const batch = sockets[0];
            const batchUpdate = jatos.batchSession.set("score", 4);
            const batchMessage = batch.sent.at(-1);
            batch.receive({action: "SESSION", patches: batchMessage.patches, version: 2});
            batch.receive({action: "SESSION_ACK", id: batchMessage.id});
            await batchUpdate;
            assert.equal(jatos.batchSession.get("score"), 4);

            await jatos.joinGroup();
            const group = sockets[1];
            const groupUpdate = jatos.groupSession.set("score", 5);
            const groupMessage = group.sent.at(-1);
            group.receive({action: "SESSION", sessionPatches: groupMessage.sessionPatches, sessionVersion: 2});
            group.receive({action: "SESSION_ACK", sessionActionId: groupMessage.sessionActionId});
            await groupUpdate;
            assert.equal(jatos.groupSession.get("score"), 5);

            const finished = ending === "end"
                ? jatos.endStudyWithoutRedirect(true, "finished")
                : jatos.abortStudyWithoutRedirect("cancelled");
            assert.equal(workers.some(worker => worker.terminated), false);
            assert.ok(httpWorker.messages.at(-1).url.includes(`/${ending}?`));
            httpWorker.respond(httpWorker.messages.at(-1));
            await finished;
            assert.equal(workers.every(worker => worker.terminated), true);
            assert.equal(finished.state(), "resolved");
            assert.equal(warns(), false);
        });
    }
}
