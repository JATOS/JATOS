/** @typedef {import("./jatos-promise.js").JatosPromise} JatosPromise */

import {callMany} from "./utils/callbacks.js";
import {rejectedPromise, isDeferredPending} from "./jatos-promise.js";

/**
 * Creates internal state for one study run; never exposed on the public jatos API.
 * Study-run operations set starting/ending, channels set invalid, and result-data
 * only reads invalid. Flags can overlap and remain set after request failure.
 * Initialization owns readiness separately.
 */
export function createStudyRunState() {
    return {starting: false, ending: false, invalid: false};
}

export function installStudyRunApi(jatos, dependencies) {
    const {
        studyRunState,
        removeBeforeUnloadWarning,
        getURL,
        httpLoop,
        isInitialized,
        stopStudyRun
    } = dependencies;

    /**
     * If you want to just write into the study session, this function is
     * not what you need. If you want to write something into the study
     * session, just write into the 'jatos.studySessionData' object.
     *
     * This function sets the study session data and sends it to the
     * JATOS server for safe storage. This is done automatically whenever
     * a component finishes. But sometimes it is necessary to trigger this
     * manually, e.g. in a very long-running component one might want to
     * store the session intermediately.
     *
     * @param {object} studySessionData - Object to be submitted
     * @param {Function} [onSuccess] - Called after this function is finished
     * @param {Function} [onFail] - Called if the request fails
     * @return {JatosPromise}
     */
    jatos.setStudySessionData = function (studySessionData, onSuccess, onFail) {
        jatos.studySessionData = studySessionData;
        const studySessionDataStr = JSON.stringify(studySessionData);
        const request = {
            url: getURL("../studySessionData"),
            data: studySessionDataStr,
            method: "POST",
            contentType: "text/plain; charset=UTF-8",
            timeout: jatos.httpTimeout,
            retry: jatos.httpRetry,
            retryWait: jatos.httpRetryWait
        };
        return httpLoop.send(request, onSuccess, onFail).promise();
    };

    /**
     * Starts the component with the given ID. Before it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * Either without message:
     * @param {string|number} componentIdOrUuid - ID or UUID of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {Function} [onError] - Called if starting fails
     *
     * Or with message:
     * @param {string|number} componentIdOrUuid - ID or UUID of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {string} [message] - Message that should be logged (max 255 chars)
     * @param {Function} [onError] - Called if starting fails
     */
    jatos.startComponent = function (componentIdOrUuid, resultData, param3, param4) {
        if (!isInitialized()) {
            console.error("jatos.js not yet initialized");
            return;
        }

        let message, onError, componentUuid;
        if (typeof componentIdOrUuid === 'number') {
            componentUuid = jatos.componentList.find(c => c.id === componentIdOrUuid).uuid;
        } else {
            componentUuid = componentIdOrUuid;
        }
        if (typeof param3 === 'string') {
            message = param3;
            onError = param4;
        } else if (typeof param3 === 'function') {
            onError = param3;
        }

        if (studyRunState.invalid) {
            callMany("Can't start component. This study run is invalid.", onError, console.warn);
            return;
        }
        if (studyRunState.starting) {
            callMany("Can start only one component at the same time", onError, console.warn);
            return;
        }
        if (studyRunState.ending) {
            callMany("Can't start component if study already ended.", onError, console.warn);
            return;
        }
        // If this is a single component run initiated by the JATOS GUI, end the run.
        const isSingleComponentRun = jatos.jatosRun === "RUN_COMPONENT_FINISHED";
        if (isSingleComponentRun) {
            if (resultData) {
                jatos.endStudy(resultData, true, message);
            } else {
                jatos.endStudy(true, message);
            }
            return;
        }

        studyRunState.starting = true;

        // Send result data and study session data before starting next component
        if (resultData) jatos.appendResultData(resultData);
        jatos.setStudySessionData(jatos.studySessionData);

        const start = function () {
            removeBeforeUnloadWarning();

            let url = getURL("../" + componentUuid + "/start");
            if (message) url = url + "?" + jatos.jQuery.param({ "message": message });
            window.location.href = url;
        };

        // Wait for http-loop-worker.js to finish
        if (httpLoop.isBusy()) {
            setTimeout(jatos.showOverlay, 1000, jatos.waitSendDataOverlayConfig);
        }
        httpLoop.whenIdle(start);
    };

    /**
     * Starts the component with the given position (position of the first
     * component of a study is 1). Before this it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * Either without message:
     * @param {number} componentPos - Position of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {Function} [onError] - Called if starting fails
     *
     * Or with message:
     * @param {number} componentPos - Position of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {string} [message] - Message that should be logged (max 255 chars)
     * @param {Function} [onError] - Called if starting fails
     */
    jatos.startComponentByPos = function (componentPos, resultData, param3, param4) {
        if (isInvalidComponentPosition(jatos.componentList, componentPos)) {
            let onError;
            if (typeof param3 === 'function') onError = param3;
            else if (typeof param4 === 'function') onError = param4;
            callMany("Component position does not exist", onError, console.error);
            return;
        }
        const componentUuid = jatos.componentList[componentPos - 1].uuid;
        jatos.startComponent(componentUuid, resultData, param3, param4);
    };

    /**
     * Starts the component with the given title. If there are multiple components with an
     * identical title it starts the one with the lowest position. Before this it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * Either without message:
     * @param {string} title - Title of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {Function} [onError] - Called if starting fails
     *
     * Or with message:
     * @param {string} title - Title of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {string} [message] - Message that should be logged (max 255 chars)
     * @param {Function} [onError] - Called if starting fails
     */
    jatos.startComponentByTitle = function (title, resultData, param3, param4) {
        const component = jatos.componentList.find(component => component.title === title);
        if (!component) {
            let onError;
            if (typeof param3 === 'function') onError = param3;
            else if (typeof param4 === 'function') onError = param4;
            callMany(`Component with title ${title} does not exist`, onError, console.error);
            return;
        }
        const componentUuid = component.uuid;
        jatos.startComponent(componentUuid, resultData, param3, param4);
    };

    /**
     * Starts the next active component of this study. The component's order is
     * determined by their position. If the current component is already the
     * last one it finishes the study. Before this it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * Either without message:
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {Function} [onError] - Called if starting fails
     *
     * Or with message:
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {string} [message] - Message that should be logged (max 255 chars)
     * @param {Function} [onError] - Called if starting fails
     */
    jatos.startNextComponent = function (resultData, param2, param3) {
        let message;
        if (typeof param2 === 'string') {
            message = param2;
        }

        // If this is the last component end study
        const lastActiveComponent = jatos.componentList.slice().reverse()
            .find(function (component) { return component.active; });
        if (jatos.componentPos >= lastActiveComponent.position) {
            if (resultData) {
                jatos.endStudy(resultData, true, message);
            } else {
                jatos.endStudy(true, message);
            }
            return;
        }
        // Start next active component
        for (let i = jatos.componentPos; i < jatos.componentList.length; i++) {
            if (jatos.componentList[i].active) {
                const nextComponentUuid = jatos.componentList[i].uuid;
                jatos.startComponent(nextComponentUuid, resultData, param2, param3);
                break;
            }
        }
    };

    /**
     * Starts the last component of this study or if it's inactive the component
     * with the highest position that is active. Before this it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * Either without message:
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {Function} [onError] - Called if starting fails
     *
     * Or with message:
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {string} [message] - Message that should be logged (max 255 chars)
     * @param {Function} [onError] - Called if starting fails
     */
    jatos.startLastComponent = function (resultData, param2, param3) {
        const lastActiveComponent = jatos.componentList.reverse().find(c => c.active);
        jatos.startComponent(lastActiveComponent.uuid, resultData, param2, param3);
    };

    /**
     * Aborts study. All previously submitted data will be deleted. It does not
     * redirect to another page.
     *
     * @param {string} [message] - Message that should be logged
     * @param {Function} [onSuccess] - Called in case of successful submit
     * @param {Function} [onError] - Called in case of error
     * @return {JatosPromise}
     */
    jatos.abortStudyWithoutRedirect = function (message, onSuccess, onError) {
        if (!isInitialized()) {
            const errorMsg = "jatos.js not yet initialized.";
            callMany(errorMsg, onError, console.error);
            return rejectedPromise(errorMsg);
        }
        if (studyRunState.invalid) {
            const errorMsg = "Can't abort study. This study run is invalid.";
            callMany(errorMsg, onError, console.warn);
            return rejectedPromise(errorMsg);
        }
        if (studyRunState.ending) {
            const errorMsg = "Can end/abort study only once.";
            callMany(errorMsg, onError, console.warn);
            return rejectedPromise(errorMsg);
        }
        studyRunState.ending = true;

        var url = getURL("../abort");
        if (typeof message != 'undefined') {
            url = url + "?message=" + message;
        }
        var request = {
            url: url,
            method: "GET",
            timeout: jatos.httpTimeout,
            retry: jatos.httpRetry,
            retryWait: jatos.httpRetryWait
        };
        jatos.showBeforeUnloadWarning(false);
        var deferred = httpLoop.send(request, onSuccess, onError);
        setTimeout(function () {
            if (httpLoop.isBusy() && isDeferredPending(deferred)) {
                jatos.showOverlay(jatos.waitSendDataOverlayConfig);
            }
        }, 1000);
        deferred.done(function () {
            removeBeforeUnloadWarning();
            stopStudyRun();
        });
        deferred.always(jatos.removeOverlays);

        return deferred.promise();
    };

    /**
     * DEPRECATED - Kept for backward compatibility. Use jatos.abortStudyWithoutRedirect instead.
     */
    jatos.abortStudyAjax = function (message, onSuccess, onError) {
        return jatos.abortStudyWithoutRedirect(message, onSuccess, onError);
    }

    /**
     * Aborts study and redirects to another URL. All previously submitted data
     * will be deleted. The first parameter is the URL and the other up to 3 parameters
     * are the same as in jatos.abortStudyWithoutRedirect.
     */
    jatos.abortStudyAndRedirect = function (url, message, onSuccess, onError) {
        jatos.abortStudyWithoutRedirect(message, onSuccess, onError).done(function () {
            window.location.href = url;
        });
    };

    /**
     * Aborts study and optionally redirects to an end page. All previously submitted data
     * will be deleted.
     *
     * @param {string} [message] - Message that should be logged
     * @param {boolean} [showEndPage=true] - If true it will redirect to an end page
     *          (either the JATOS default one or the one that is configured in the
     *          study properties) after the study is finished. If false it stays on the
     *          current page. Alternatively jatos.abortStudyAndRedirect can be used to
     *          redirect to another page.
     */
    jatos.abortStudy = function (message, showEndPage = true) {
        if (studyRunState.invalid) {
            console.warn("Can't abort study. This study run is invalid.");
            return;
        }

        if (!showEndPage) {
            return jatos.abortStudyWithoutRedirect(message);
        }

        // In an iframe run initiated by the JATOS GUI (worker type 'Jatos'), end the run without
        // redirecting and notify the parent frame.
        const isInIframe = window.self !== window.top;
        if (isInIframe && jatos.workerType === "Jatos" && parent.onIframeComplete) {
            return jatos.abortStudyWithoutRedirect(message)
                .done(() => parent.onIframeComplete(jatos.urlQueryParameters.frameId, jatos.studyId));
        }

        if (studyRunState.ending) {
            console.warn("Can end/abort study only once");
            return;
        }
        studyRunState.ending = true;

        function abort() {
            removeBeforeUnloadWarning();

            var url = getURL("../abort");
            if (typeof message == 'undefined') {
                window.location.href = url;
            } else {
                window.location.href = url + "?message=" + message;
            }
        }

        // Wait for httpLoop.js to finish
        if (httpLoop.isBusy()) {
            setTimeout(jatos.showOverlay, 1000, jatos.waitSendDataOverlayConfig);
        }
        httpLoop.whenIdle(abort);
    };

    /**
     * Ends study without redirecting to another page, e.g. the JATOS end page.
     *
     * Either without result data:
     * @param {boolean} [successful=true] - Whether the study finished successfully
     * @param {string} [message] - Message to be logged (max 255 chars)
     * @param {Function} [onSuccess] - Called on successful request
     * @param {Function} [onError] - Called in case of error
     *
     * Or with result data:
     * @param {(Object|string)} [resultData] - Result data to be sent back to the JATOS server
     * @param {boolean} [successful=true] - Whether the study finished successfully
     * @param {string} [message] - Message to be logged (max 255 chars)
     * @param {Function} [onSuccess] - Called on successful request
     * @param {Function} [onError] - Called in case of error
     *
     * @return {JatosPromise}
     */
    jatos.endStudyWithoutRedirect = function (param1, param2, param3, param4, param5) {
        if (!isInitialized()) {
            const errorMsg = "jatos.js not yet initialized.";
            console.error(errorMsg);
            return rejectedPromise(errorMsg);
        }

        var resultData, successful, message, onSuccess, onError;
        if (typeof param1 === 'string' || typeof param1 === 'object') {
            resultData = param1;
            successful = param2;
            message = param3;
            onSuccess = param4;
            onError = param5;
        } else if (typeof param1 === 'boolean') {
            successful = param1;
            message = param2;
            onSuccess = param3;
            onError = param4;
        }

        if (studyRunState.invalid) {
            const errorMsg = "Can't end study. This study run is invalid.";
            callMany(errorMsg, onError, console.warn);
            return rejectedPromise(errorMsg);
        }
        if (studyRunState.ending) {
            const errorMsg = "Can end/abort study only once.";
            callMany(errorMsg, onError, console.warn);
            return rejectedPromise(errorMsg);
        }
        studyRunState.ending = true;

        // Before finish send result data
        if (resultData) jatos.appendResultData(resultData);

        var url = getURL("../end");
        if (typeof successful == 'boolean' && typeof message == 'string') {
            url = url + "?" + jatos.jQuery.param({
                "successful": successful,
                "message": message
            });
        } else if (typeof successful == 'boolean' && typeof message != 'string') {
            url = url + "?" + jatos.jQuery.param({
                "successful": successful
            });
        } else if (typeof successful != 'boolean' && typeof message == 'string') {
            url = url + "?" + jatos.jQuery.param({
                "message": message
            });
        }
        var request = {
            url: url,
            method: "GET",
            timeout: jatos.httpTimeout,
            retry: jatos.httpRetry,
            retryWait: jatos.httpRetryWait
        };
        jatos.showBeforeUnloadWarning(false);
        var deferred = httpLoop.send(request, onSuccess, onError);
        setTimeout(function () {
            if (httpLoop.isBusy() && isDeferredPending(deferred)) {
                jatos.showOverlay(jatos.waitSendDataOverlayConfig);
            }
        }, 1000);
        deferred.done(function () {
            removeBeforeUnloadWarning();
            stopStudyRun();
        });
        deferred.always(jatos.removeOverlays);

        return deferred.promise();
    };

    /**
     * DEPRECATED - Kept for backward compatibilty. Use jatos.endStudyWithoutRedirect instead.
     */
    jatos.endStudyAjax = function (param1, param2, param3, param4, param5) {
        return jatos.endStudyWithoutRedirect(param1, param2, param3, param4, param5);
    }

    /**
     * Ends study and redirects to another URL. The first parameter is the URL and the
     * other up to 5 parameters are the same as in jatos.endStudyWithoutRedirect.
     */
    jatos.endStudyAndRedirect = function (url, param1, param2, param3, param4, param5) {
        jatos.endStudyWithoutRedirect(param1, param2, param3, param4, param5).done(function () {
            window.location.href = url;
        });
    };

    /**
     * Ends study and optionally redirects to an end page.
     *
     * Either without result data:
     * @param {boolean} [successful=true] - Whether the study finished successfully
     * @param {string} [message] - Message to be logged (max 255 chars)
     * @param {boolean} [showEndPage=true] - If true redirect to end page; if false stay on current page
     *
     * Or with result data:
     * @param {(Object|string)} [resultData] - Result data to be sent back to the JATOS server
     * @param {boolean} [successful=true] - Whether the study finished successfully
     * @param {string} [message] - Message to be logged (max 255 chars)
     * @param {boolean} [showEndPage=true] - If true redirect to end page; if false stay on current page
     */
    jatos.endStudy = function (param1, param2, param3, param4) {
        if (!isInitialized()) {
            console.error("jatos.js not yet initialized");
            return;
        }
        if (studyRunState.invalid) {
            console.warn("Can't end study. This study run is invalid.");
            return;
        }

        var resultData, successful, message, showEndPage;
        if (typeof param1 === 'string' || typeof param1 === 'object') {
            resultData = param1;
            successful = param2;
            message = param3;
            showEndPage = param4;
        } else if (typeof param1 === 'boolean') {
            successful = param1;
            message = param2;
            showEndPage = param3;
        }

        if (typeof showEndPage !== "undefined" && !showEndPage) {
            if (resultData) {
                return jatos.endStudyWithoutRedirect(resultData, successful, message);
            } else {
                return jatos.endStudyWithoutRedirect(successful, message);
            }
        }

        // In an iframe run initiated by the JATOS GUI (worker type 'Jatos'), end the run without
        // redirecting and notify the parent frame.
        const isInIframe = window.self !== window.top;
        if (isInIframe && jatos.workerType === "Jatos" && parent.onIframeComplete) {
            const endIframe = () => {
                parent.onIframeComplete(jatos.urlQueryParameters.frameId, jatos.studyId);
            };
            if (resultData) {
                jatos.endStudyWithoutRedirect(resultData, successful, message).done(endIframe);
            } else {
                jatos.endStudyWithoutRedirect(successful, message).done(endIframe);
            }
            return;
        }

        if (studyRunState.ending) {
            console.warn("Can end/abort study only once");
            return;
        }
        studyRunState.ending = true;

        // Before finish send result data
        if (resultData) jatos.appendResultData(resultData);

        function end() {
            removeBeforeUnloadWarning();

            var url = getURL("../end");
            if (typeof successful == 'boolean' && typeof message == 'string') {
                url = url + "?" + jatos.jQuery.param({
                    "successful": successful,
                    "message": message
                });
            } else if (typeof successful == 'boolean' && typeof message != 'string') {
                url = url + "?" + jatos.jQuery.param({
                    "successful": successful
                });
            } else if (typeof successful != 'boolean' && typeof message == 'string') {
                url = url + "?" + jatos.jQuery.param({
                    "message": message
                });
            }
            window.location.href = url;
        }

        // Wait for httpLoop.js to finish
        if (httpLoop.isBusy()) {
            setTimeout(jatos.showOverlay, 1000, jatos.waitSendDataOverlayConfig);
        }
        httpLoop.whenIdle(end);
    };
}

export function isInvalidComponentPosition(componentList, pos) {
    return pos <= 0 || pos > componentList.length;
}
