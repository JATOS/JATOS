import assert from "node:assert/strict";
import jqueryFactory from "jquery";
import test from "node:test";
import {JSDOM} from "jsdom";
import {encodeQuery} from "../src/utils/query.js";

test("navigation query encoding matches the pinned jQuery for scalar parameters", t => {
    const dom = new JSDOM("", {runScripts: "outside-only"});
    t.after(() => dom.window.close());
    const jquery = jqueryFactory(dom.window);
    for (const parameters of [
        {successful: false, message: "all done"},
        {message: "a+b & café / ? # = !'()*~"},
        {"special key": "value", empty: "", absent: undefined, nil: null, count: 0}
    ]) {
        assert.equal(encodeQuery(parameters), jquery.param(parameters));
    }
    assert.equal(encodeQuery({message: "all done"}), "message=all%20done");
});
