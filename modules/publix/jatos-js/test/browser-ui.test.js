import assert from "node:assert/strict";
import test from "node:test";
import {JSDOM} from "jsdom";
import {createBrowserUi} from "../src/browser-ui.js";

function setup(t, reloadable = false) {
    const dom = new JSDOM("<!doctype html><body></body>");
    const previous = {window: globalThis.window, document: globalThis.document};
    globalThis.window = dom.window;
    globalThis.document = dom.window.document;
    t.after(() => {
        dom.window.close();
        for (const [name, value] of Object.entries(previous)) {
            if (value === undefined) delete globalThis[name];
            else globalThis[name] = value;
        }
    });
    const jatos = {componentProperties: {reloadable}};
    const ui = createBrowserUi(jatos);
    const warns = () => !dom.window.dispatchEvent(new dom.window.Event("beforeunload", {cancelable: true}));
    return {jatos, ui, window: dom.window, document: dom.window.document, warns};
}

test("unload warning respects readiness, opt-out, and lifecycle cleanup", t => {
    const {jatos, ui, warns} = setup(t);
    assert.equal(warns(), false);
    jatos.showBeforeUnloadWarning(false);
    ui.onLoad();
    assert.equal(warns(), false);
    jatos.showBeforeUnloadWarning(true);
    assert.equal(warns(), true);
    ui.removeBeforeUnloadWarning();
    assert.equal(warns(), false);
    ui.onLoad();
    assert.equal(warns(), true);
    jatos.showBeforeUnloadWarning(false);
    assert.equal(warns(), false);
});

test("reloadable components do not automatically warn", t => {
    const {ui, warns} = setup(t, true);
    ui.onLoad();
    assert.equal(warns(), false);
});

test("overlays retain defaults, update by ID, and respect keep and force", t => {
    const {jatos, document} = setup(t);
    assert.equal(jatos.showOverlay({show: false}), undefined);
    const ordinary = jatos.showOverlay();
    assert.equal(ordinary.textContent, "Please wait");
    assert.equal(ordinary.querySelector("img").getAttribute("src"), "jatos-publix/images/waiting.gif");
    const kept = jatos.showOverlay({id: "status", text: "First", keep: true, showImg: false});
    assert.equal(jatos.showOverlay({id: "status", text: "Updated"}), kept);
    assert.equal(kept.textContent, "Updated");
    jatos.removeOverlay();
    assert.equal(ordinary.isConnected, false);
    assert.equal(kept.isConnected, true);
    jatos.removeOverlays(true);
    assert.equal(document.querySelectorAll(".jatosOverlay").length, 0);
});

test("overlay timeout removes only its own element", t => {
    const {jatos} = setup(t);
    let expire;
    t.mock.method(globalThis, "setTimeout", (callback, delay) => {
        assert.equal(delay, 100);
        expire = callback;
    });
    const timed = jatos.showOverlay({text: "Temporary", timeout: 100});
    const other = jatos.showOverlay();
    expire();
    assert.equal(timed.isConnected, false);
    assert.equal(other.isConnected, true);
});

test("ID overlay is restricted to Jatos workers and updates current IDs", t => {
    const {jatos, ui, document} = setup(t);
    ui.showIdOverlay();
    assert.equal(document.getElementById("idOverlay"), null);
    Object.assign(jatos, {workerType: "Jatos", workerId: 7, studyResultId: 8});
    ui.showIdOverlay();
    const overlay = document.getElementById("idOverlay");
    assert.equal(overlay.textContent, "worker: 7\nstudy result: 8");
    jatos.groupResultId = 9;
    ui.showIdOverlay();
    assert.equal(document.getElementById("idOverlay"), overlay);
    assert.ok(overlay.textContent.endsWith("group: 9"));
    jatos.removeOverlays();
    assert.equal(overlay.isConnected, true);
});

test("abort button honors confirmation and custom actions", t => {
    const {jatos, window, document} = setup(t);
    const aborted = [];
    jatos.abortStudy = message => aborted.push(message);
    window.confirm = () => false;
    jatos.addAbortButton();
    const button = document.body.lastElementChild;
    button.click();
    assert.deepEqual(aborted, []);
    window.confirm = message => {
        assert.equal(message, "Do you really want to cancel this study?");
        return true;
    };
    button.click();
    assert.deepEqual(aborted, ["Worker decided to abort"]);
    const custom = [];
    jatos.addAbortButton({confirm: false, text: "Stop", msg: "custom", action: message => custom.push(message)});
    document.body.lastElementChild.click();
    assert.deepEqual(custom, ["custom"]);
    assert.equal(aborted.length, 1);
});
