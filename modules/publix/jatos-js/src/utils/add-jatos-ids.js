/**
 * Adds JATOS IDs, study/component/study-result UUIDs, titles, study code,
 * worker type, and component position to the given object. Call after jatos.onLoad.
 * Existing fields with the same names are overwritten.
 *
 * @param {Object} jatos - Current JATOS metadata
 * @param {Object} [obj={}] - Object to which the IDs will be added
 * @returns {Object} The same object passed in with IDs added
 */
export function addJatosIds(jatos, obj = {}) {
    obj.studyCode = jatos.studyCode;
    obj.studyId = jatos.studyId;
    obj.studyUuid = jatos.studyProperties.uuid;
    obj.studyTitle = jatos.studyProperties.title;
    obj.batchId = jatos.batchId;
    obj.batchTitle = jatos.batchProperties.title;
    obj.componentId = jatos.componentId;
    obj.componentUuid = jatos.componentProperties.uuid;
    obj.componentPos = jatos.componentPos;
    obj.componentTitle = jatos.componentProperties.title;
    obj.workerId = jatos.workerId;
    obj.workerType = jatos.workerType;
    obj.studyResultId = jatos.studyResultId;
    obj.studyResultUuid = jatos.studyResultUuid;
    obj.componentResultId = jatos.componentResultId;
    obj.groupResultId = jatos.groupResultId;
    obj.groupMemberId = jatos.groupMemberId;
    return obj;
}
