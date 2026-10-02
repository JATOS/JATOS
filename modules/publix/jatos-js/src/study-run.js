import {encodeQuery} from "./utils/query.js";

/** @typedef {import("./jatos-promise.js").JatosPromise} JatosPromise */

import {callMany, callWithArgs} from "./utils/callbacks.js";
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
     * @param {Function} [onError] - Called if the request fails
     * @returns {JatosPromise}
     */
    jatos.setStudySessionData = function (studySessionData, onSuccess, onError) {
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
        return httpLoop.send(request, onSuccess, onError).promise();
    };

    /**
     * Starts the component with the given ID. Before it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * @param {string|number} componentIdOrUuid - ID or UUID of the component to start
     * @param {Object|string} [resultData] - Result data to be sent back to JATOS
     * @param {string|Function} [messageOrOnError] - Log message (max 255 chars) or error callback
     * @param {Function} [onError] - Error callback when a message is supplied
     */
    jatos.startComponent = function (componentIdOrUuid, resultData, messageOrOnError, onError) {
        if (!isInitialized()) {
            console.error("jatos.js not yet initialized");
            return;
        }

        let componentUuid;
        let message;
        if (typeof componentIdOrUuid === 'number') {
            componentUuid = jatos.componentList.find(c => c.id === componentIdOrUuid).uuid;
        } else {
            componentUuid = componentIdOrUuid;
        }
        ({message, onError} = normalizeStartComponentArguments(messageOrOnError, onError));

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
            if (message) url = url + "?" + encodeQuery({ "message": message });
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
     * @param {number} componentPos - Position of the component to start
     * @param {Object|string} [resultData] - Result data to be sent back to JATOS
     * @param {string|Function} [messageOrOnError] - Log message (max 255 chars) or error callback
     * @param {Function} [onError] - Error callback when a message is supplied
     */
    jatos.startComponentByPos = function (componentPos, resultData, messageOrOnError, onError) {
        if (isInvalidComponentPosition(jatos.componentList, componentPos)) {
            onError = parseLookupErrorCallback(messageOrOnError, onError);
            callMany("Component position does not exist", onError, console.error);
            return;
        }
        const componentUuid = jatos.componentList[componentPos - 1].uuid;
        jatos.startComponent(componentUuid, resultData, messageOrOnError, onError);
    };

    /**
     * Starts the component with the given title. If there are multiple components with an
     * identical title, it starts the one with the lowest position. Before this it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * @param {string} title - Title of the component to start
     * @param {Object|string} [resultData] - Result data to be sent back to JATOS
     * @param {string|Function} [messageOrOnError] - Log message (max 255 chars) or error callback
     * @param {Function} [onError] - Error callback when a message is supplied
     */
    jatos.startComponentByTitle = function (title, resultData, messageOrOnError, onError) {
        const component = jatos.componentList.find(component => component.title === title);
        if (!component) {
            onError = parseLookupErrorCallback(messageOrOnError, onError);
            callMany(`Component with title ${title} does not exist`, onError, console.error);
            return;
        }
        const componentUuid = component.uuid;
        jatos.startComponent(componentUuid, resultData, messageOrOnError, onError);
    };

    /**
     * Starts the next active component of this study. The component's order is
     * determined by their position. If the current component is already the
     * last one it finishes the study. Before this it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * @param {Object|string} [resultData] - Result data to be sent back to JATOS
     * @param {string|Function} [messageOrOnError] - Log message (max 255 chars) or error callback
     * @param {Function} [onError] - Error callback when a message is supplied
     */
    jatos.startNextComponent = function (resultData, messageOrOnError, onError) {
        const {message} = normalizeStartComponentArguments(messageOrOnError, onError);

        // If this is the last component, end study
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
                jatos.startComponent(nextComponentUuid, resultData, messageOrOnError, onError);
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
     * @param {Object|string} [resultData] - Result data to be sent back to JATOS
     * @param {string|Function} [messageOrOnError] - Log message (max 255 chars) or error callback
     * @param {Function} [onError] - Error callback when a message is supplied
     */
    jatos.startLastComponent = function (resultData, messageOrOnError, onError) {
        const lastActiveComponent = jatos.componentList.slice().reverse().find(c => c.active);
        jatos.startComponent(lastActiveComponent.uuid, resultData, messageOrOnError, onError);
    };

    /**
     * Aborts study. All previously submitted data will be deleted. It does not
     * redirect to another page.
     *
     * @param {string} [message] - Message that should be logged
     * @param {Function} [onSuccess] - Called in case of successful submit
     * @param {Function} [onError] - Called in case of error
     * @returns {JatosPromise}
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

        return sendStudyCompletion(getAbortStudyUrl(message), onSuccess, onError);
    };

    /**
     * @deprecated Use `jatos.abortStudyWithoutRedirect` instead.
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

        redirectWhenIdle(() => getAbortStudyUrl(message));
    };

    /**
     * Ends study without redirecting to another page, e.g. the JATOS end page.
     *
     * Pass (successful, message, ...) or (resultData, successful, message, ...).
     * @param {Object|string|boolean} [resultDataOrSuccessful] - Result data or success flag
     * @param {boolean|string} [successfulOrMessage] - Success flag with data; otherwise log message
     * @param {string|Function} [messageOrOnSuccess] - Log message with data; otherwise success callback
     * @param {Function} [onSuccessOrOnError] - Success callback with data; otherwise error callback
     * @param {Function} [onError] - Error callback when result data is supplied
     * @returns {JatosPromise}
     */
    jatos.endStudyWithoutRedirect = function (resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError) {
        if (!isInitialized()) {
            const errorMsg = "jatos.js not yet initialized.";
            console.error(errorMsg);
            return rejectedPromise(errorMsg);
        }

        let resultData, successful, message, onSuccess;
        ({resultData, successful, message, onSuccess, onError} = normalizeEndStudyArguments(
            resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError));

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

        return sendStudyCompletion(getEndStudyUrl(successful, message), onSuccess, onError);
    };

    /**
     * @deprecated Use `jatos.endStudyWithoutRedirect` instead.
     */
    jatos.endStudyAjax = function (resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError) {
        return jatos.endStudyWithoutRedirect(resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError);
    }

    /**
     * Ends study and redirects to another URL. The first parameter is the URL and the
     * other up to 5 parameters are the same as in jatos.endStudyWithoutRedirect.
     */
    jatos.endStudyAndRedirect = function (url, resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError) {
        jatos.endStudyWithoutRedirect(resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError).done(function () {
            window.location.href = url;
        });
    };

    /**
     * Ends study and optionally redirects to an end page.
     *
     * Pass (successful, message, ...) or (resultData, successful, message, ...).
     * @param {Object|string|boolean} [resultDataOrSuccessful] - Result data or success flag
     * @param {boolean|string} [successfulOrMessage] - Success flag with data; otherwise log message
     * @param {string|boolean} [messageOrShowEndPage] - Log message with data; otherwise end-page flag
     * @param {boolean} [showEndPage=true] - End-page flag when result data is supplied
     */
    jatos.endStudy = function (resultDataOrSuccessful, successfulOrMessage, messageOrShowEndPage, showEndPage) {
        if (!isInitialized()) {
            console.error("jatos.js not yet initialized");
            return;
        }
        if (studyRunState.invalid) {
            console.warn("Can't end study. This study run is invalid.");
            return;
        }

        let resultData, successful, message;
        ({resultData, successful, message, showEndPage} = normalizeEndStudyPageArguments(
            resultDataOrSuccessful, successfulOrMessage, messageOrShowEndPage, showEndPage));

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

        redirectWhenIdle(() => getEndStudyUrl(successful, message));
    };

    function getAbortStudyUrl(message) {
        const url = getURL("../abort");
        return typeof message === "undefined" ? url : url + "?" + encodeQuery({message: String(message)});
    }

    function getEndStudyUrl(successful, message) {
        const url = getURL("../end");
        const query = {};
        if (typeof successful === "boolean") query.successful = successful;
        if (typeof message === "string") query.message = message;
        const encodedQuery = encodeQuery(query);
        return encodedQuery ? url + "?" + encodedQuery : url;
    }

    // Finish a successful cleanup before user callbacks. On failure, only remove overlays;
    // keep the run in ending state because the server may have completed the request.
    function sendStudyCompletion(url, onSuccess, onError) {
        const request = {
            url,
            method: "GET",
            timeout: jatos.httpTimeout,
            retry: jatos.httpRetry,
            retryWait: jatos.httpRetryWait
        };
        jatos.showBeforeUnloadWarning(false);
        const deferred = httpLoop.send(request, (...args) => {
            removeBeforeUnloadWarning();
            stopStudyRun();
            jatos.removeOverlays();
            callWithArgs(onSuccess, ...args);
        }, onError);
        setTimeout(function () {
            if (httpLoop.isBusy() && isDeferredPending(deferred)) {
                jatos.showOverlay(jatos.waitSendDataOverlayConfig);
            }
        }, 1000);
        deferred.fail(jatos.removeOverlays);

        return deferred.promise();
    }

    // Resolve the URL only after queued work finishes, as in the public APIs.
    function redirectWhenIdle(getRedirectUrl) {
        if (httpLoop.isBusy()) {
            setTimeout(jatos.showOverlay, 1000, jatos.waitSendDataOverlayConfig);
        }
        httpLoop.whenIdle(() => {
            removeBeforeUnloadWarning();
            window.location.href = getRedirectUrl();
        });
    }

}

export function isInvalidComponentPosition(componentList, pos) {
    return pos <= 0 || pos > componentList.length;
}

// Preserve the existing positional conventions, including ignored arguments.
function normalizeStartComponentArguments(messageOrOnError, onError) {
    if (typeof messageOrOnError === "string") return {message: messageOrOnError, onError};
    if (typeof messageOrOnError === "function") return {onError: messageOrOnError};
    return {};
}

// Lookup failures historically accept a fourth-position callback even without a message.
function parseLookupErrorCallback(messageOrOnError, onError) {
    if (typeof messageOrOnError === "function") return messageOrOnError;
    if (typeof onError === "function") return onError;
}

function hasResultDataArgument(value) {
    // Includes null, as in the existing API; truthiness is checked later before sending.
    return typeof value === "string" || typeof value === "object";
}

function normalizeEndStudyArguments(resultDataOrSuccessful, successfulOrMessage,
    messageOrOnSuccess, onSuccessOrOnError, onError) {
    if (hasResultDataArgument(resultDataOrSuccessful)) {
        return {resultData: resultDataOrSuccessful, successful: successfulOrMessage,
            message: messageOrOnSuccess, onSuccess: onSuccessOrOnError, onError};
    }
    if (typeof resultDataOrSuccessful === "boolean") {
        return {successful: resultDataOrSuccessful, message: successfulOrMessage,
            onSuccess: messageOrOnSuccess, onError: onSuccessOrOnError};
    }
    return {};
}

function normalizeEndStudyPageArguments(resultDataOrSuccessful, successfulOrMessage,
    messageOrShowEndPage, showEndPage) {
    if (hasResultDataArgument(resultDataOrSuccessful)) {
        return {resultData: resultDataOrSuccessful, successful: successfulOrMessage,
            message: messageOrShowEndPage, showEndPage};
    }
    if (typeof resultDataOrSuccessful === "boolean") {
        return {successful: resultDataOrSuccessful, message: successfulOrMessage,
            showEndPage: messageOrShowEndPage};
    }
    return {};
}
