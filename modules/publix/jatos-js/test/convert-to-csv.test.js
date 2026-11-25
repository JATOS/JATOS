import assert from "node:assert/strict";
import test from "node:test";
import {convertToCSV} from "../src/utils/convert-to-csv.js";

test("collects headers in first-seen order and preserves scalar and empty values", () => {
    assert.equal(convertToCSV([{b: 0, a: false}, {c: "", b: null}, {a: undefined}]),
        'b,a,c\r\n0,false,""\r\n"","",""\r\n"","",""');
    assert.equal(convertToCSV([]), "");
    assert.equal(convertToCSV([], ",", true), "");
    assert.equal(convertToCSV([{}, {}]), "\r\n\r\n");
});

test("escapes headers and cells with separators, quotes and line breaks", () => {
    for (const value of ["a,b", 'a"b', "a\rb", "a\nb", "a\u2028b", "a\u2029b", ""]) {
        const escaped = '"' + value.replace(/"/g, '""') + '"';
        assert.equal(convertToCSV([{[value]: value}]), escaped + "\r\n" + escaped);
    }
    assert.equal(convertToCSV([{name: "café"}], ";", true), "\uFEFFname\r\ncafé");
    assert.equal(convertToCSV([{a: "x;y"}], ";"), 'a\r\n"x;y"');
    assert.equal(convertToCSV([{a: "x|", b: "y"}], "||"), '"a"||"b"\r\n"x|"||"y"');
});

test("serializes objects and arrays as JSON fields", () => {
    assert.equal(convertToCSV([{object: {x: 1}, array: [1, "a"]}]),
        'object,array\r\n"{""x"":1}","[1,""a""]"');
});

test("uses only own enumerable headers and own cell values", () => {
    const row = Object.assign(Object.create({a: "inherited", hidden: 1}), {b: 2});
    const nullPrototype = Object.assign(Object.create(null), {a: 3});
    assert.equal(convertToCSV([{a: 1}, row, nullPrototype]), 'a,b\r\n1,""\r\n"",2\r\n3,""');
});

test("rejects invalid arguments and rows even for empty input", () => {
    for (const value of [null, undefined, {}, "rows", 1]) {
        assert.throws(() => convertToCSV(value), TypeError);
    }
    for (const separator of [null, 1, "", '"', "\r", "\n", "a\nb"]) {
        assert.throws(() => convertToCSV([], separator), TypeError);
    }
    for (const bom of [null, 0, "true"]) {
        assert.throws(() => convertToCSV([], ",", bom), TypeError);
    }
    for (const row of [null, undefined, [], "row", 1, true, () => {}]) {
        assert.throws(() => convertToCSV([{}, row]), /Row at index 1/);
    }
    assert.throws(() => convertToCSV(new Array(1)), /Row at index 0/);
});

test("reports unsupported cells and JSON failures with their location", () => {
    for (const value of [1n, Symbol("cell"), () => {}, {toJSON: () => undefined}]) {
        assert.throws(() => convertToCSV([{cell: value}]), {
            name: "TypeError", message: 'Unsupported value at row index 0, column "cell"'
        });
    }
    const circular = {};
    circular.self = circular;
    for (const value of [circular, {nested: 1n}]) {
        assert.throws(() => convertToCSV([{cell: value}]), error => {
            assert.equal(error.message, 'Cannot serialize value at row index 0, column "cell" as JSON');
            assert.ok(error instanceof TypeError);
            assert.ok(error.cause instanceof TypeError);
            return true;
        });
    }
});
