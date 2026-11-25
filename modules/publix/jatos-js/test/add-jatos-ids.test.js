import assert from "node:assert/strict";
import test from "node:test";
import {addJatosIds} from "../src/utils/add-jatos-ids.js";

test("adds all existing metadata and requested UUIDs to the supplied object", () => {
    const jatos = {
        studyCode: "code", studyId: "1", batchId: "2", componentId: "3",
        componentPos: 4, workerId: "5", studyResultId: "6", studyResultUuid: "run-uuid",
        componentResultId: "7", groupResultId: "8", groupMemberId: "9",
        workerType: "GeneralMultiple",
        studyProperties: {title: "Study", uuid: "study-uuid"},
        batchProperties: {title: "Batch"},
        componentProperties: {title: "Component", uuid: "component-uuid"}
    };
    const obj = {score: 42, studyId: "old", studyUuid: "old", workerType: "old"};
    assert.equal(addJatosIds(jatos, obj), obj);
    assert.deepEqual(obj, {
        score: 42, studyCode: "code", studyId: "1", studyUuid: "study-uuid", studyTitle: "Study",
        batchId: "2", batchTitle: "Batch", componentId: "3", componentUuid: "component-uuid",
        componentPos: 4, componentTitle: "Component", workerId: "5", studyResultId: "6",
        workerType: "GeneralMultiple",
        studyResultUuid: "run-uuid", componentResultId: "7", groupResultId: "8", groupMemberId: "9"
    });
    const first = addJatosIds(jatos);
    assert.notEqual(first, addJatosIds(jatos));
    jatos.componentProperties = {title: "Next", uuid: "next-uuid"};
    jatos.groupResultId = "10";
    const next = addJatosIds(jatos);
    assert.equal(next.componentUuid, "next-uuid");
    assert.equal(next.componentTitle, "Next");
    assert.equal(next.groupResultId, "10");
    assert.equal(first.componentUuid, "component-uuid");
});

test("preserves undefined metadata when IDs are unavailable", () => {
    const jatos = {studyProperties: {}, batchProperties: {}, componentProperties: {}};
    const obj = addJatosIds(jatos);
    for (const key of ["studyUuid", "componentUuid", "studyResultUuid", "groupResultId", "groupMemberId", "workerType"]) {
        assert.ok(Object.hasOwn(obj, key));
        assert.equal(obj[key], undefined);
    }
});
