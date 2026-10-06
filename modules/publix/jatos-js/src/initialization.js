import {getHttpErrorMessage} from "./http-transport.js";

import {createDeferred} from "./jatos-promise.js";

/** Owns startup, readiness callbacks, and the study-run heartbeat worker. */
export function createInitialization(jatos, dependencies) {
    const {requestHttp, getURL, showIdOverlay, httpLoop, channels} = dependencies;

    let initialized = false;
    let initializationError;
    const errorCallbacks = [];
    let jatosOnLoadEventFired = false;
    const jatosOnLoadEvent = new Event("jatosOnLoad");
    // The study-run heartbeat is separate from batch/group channel heartbeats.
    let heartbeatWorker;
    function initJatos() {

        // Use our private Deferred to preserve startup scheduling and failure behavior.
        createDeferred().resolve().promise()
            .then(function () {
                // Get studyResultUuid from URL path
                jatos.studyResultUuid = window.location.pathname.split("/").reverse()[2];
                readIdCookie();
                // Start heartbeat.js (the general one - not the channel one)
                heartbeatWorker = new Worker("jatos-publix/javascripts/heartbeat.js");
                heartbeatWorker.postMessage([jatos.studyResultUuid]);
                // Start the background HTTP request queue
                httpLoop.start();
            })
            .then(getInitData)
            .then(showIdOverlay)
            .then(() => channels.openBatchChannelWithRetry())
            .done(function () {
                initialized = true;
                errorCallbacks.length = 0;
                readyForOnLoad();
            })
            .fail(function (error) {
                initializationError = error || "JATOS initialization failed";
                heartbeatWorker?.terminate();
                httpLoop.terminate();
                console.error("JATOS initialization failed:", initializationError);
                errorCallbacks.splice(0).forEach(callback => callback(initializationError));
            });
    }

    /**
     * Reads JATOS ID cookies, finds the right one with the correct studyResultUuid
     * and stores all key-value pairs into the global jatos object.
     */
    function readIdCookie() {
        const idCookieName = "JATOS_ID";
        const cookieRow = document.cookie
            .split('; ')
            .filter(row => row.includes(idCookieName))
            .find(row => row.includes(jatos.studyResultUuid));
        if (!cookieRow) {
            console.error('readIdCookie: JATOS ID cookie for current studyResultUuid not found.');
            return;
        }

        const equalsIndex = cookieRow.indexOf('=');
        const idCookieValue = cookieRow.substring(equalsIndex + 1);
        if (!idCookieValue) {
            console.error(`readIdCookie: JATOS ID cookie value is empty for cookie "${cookieRow}".`);
            return;
        }

        const cookieParams = new URLSearchParams(idCookieValue);
        cookieParams.forEach((value, key) => jatos[key] = value);
        jatos.componentPos = parseInt(jatos.componentPos, 10);
    }

    /**
     * Gets the study's session data, the study's properties, and the
     * component's properties from the JATOS server and stores them in
     * jatos.studySessionData, jatos.studyProperties, and
     * jatos.componentProperties. Additionally it sets jatos.batchInput,
     * jatos.studyInput, and jatos.componentInput.
     */
    function getInitData() {
        return requestHttp({
            url: getURL("initData"),
            retry: {times: jatos.httpRetry, timeout: jatos.httpRetryWait},
            method: "GET",
            dataType: 'json',
            timeout: jatos.httpTimeout,
            success: setInitData,
            error: (err) => console.error(getHttpErrorMessage(err))
        });
    }

    /**
     * Puts the init data into jatos variables
     */
    function setInitData(initData) {
        // Batch properties
        jatos.batchProperties = initData.batchProperties;
        if (typeof jatos.batchProperties.batchInput != 'undefined' &&
            jatos.studyProperties.studyInput !== null) {
            jatos.batchJsonInput = JSON.parse(jatos.batchProperties.batchInput);
        } else {
            jatos.batchJsonInput = {};
        }
        jatos.batchInput = jatos.batchJsonInput;
        delete jatos.batchProperties.batchInput;

        // Study session data
        try {
            jatos.studySessionData = JSON.parse(initData.studySessionData);
        } catch (e) {
            console.error(e.stack || e);
        }

        // Study properties
        jatos.studyProperties = initData.studyProperties;
        if (typeof jatos.studyProperties.studyInput != 'undefined' &&
            jatos.studyProperties.studyInput !== null) {
            jatos.studyJsonInput = JSON.parse(jatos.studyProperties.studyInput);
        } else {
            jatos.studyJsonInput = {};
        }
        jatos.studyInput = jatos.studyJsonInput;
        delete jatos.studyProperties.studyInput;

        // Study's component list and study length
        jatos.componentList = initData.componentList;
        jatos.studyLength = initData.componentList.length;

        // Component properties
        jatos.componentProperties = initData.componentProperties;
        if (typeof jatos.componentProperties.componentInput != 'undefined' &&
            jatos.componentProperties.componentInput !== null) {
            jatos.componentJsonInput = JSON.parse(jatos.componentProperties.componentInput);
        } else {
            jatos.componentJsonInput = {};
        }
        jatos.componentInput = jatos.componentJsonInput;
        delete jatos.componentProperties.componentInput;

        // Query string parameters of the URL that started the study
        jatos.urlQueryParameters = initData.urlQueryParameters;
        jatos.frameId = jatos.urlQueryParameters.frameId || undefined;
        jatos.studyCode = initData.studyCode;
    }

    /**
     * Defines a listener (a callback function) that will be called
     * when jatos.js finished its initialisation (e.g. channels are
     * open and init data loaded). It's possible to define several
     * listeners (called in the order as defined). If the
     * jatosOnLoad event was already fired, the callback is called
     * right away.
     * Success callbacks run only after init data and the batch session are available.
     * @param {function} callback - callback function
     * @param {function} [onError] - Called if startup fails, including batch opening timeout
     */
    jatos.onLoad = function (callback, onError) {
        if (initializationError !== undefined) {
            if (typeof onError === "function") onError(initializationError);
            return;
        }
        if (!initialized && typeof onError === "function") errorCallbacks.push(onError);
        if (!jatosOnLoadEventFired) {
            window.addEventListener("jatosOnLoad", callback);
            readyForOnLoad();
        } else {
            callback();
        }
    };

    /**
     * Lowercase alias retained for compatibility with the conventional `onload` spelling.
     */
    jatos.onload = jatos.onLoad;

    /**
     * Calls onLoadCallback if it already exists and jatos.js is initialised.
     * Uses the existing DOM event to preserve onLoad callback timing.
     */
    function readyForOnLoad() {
        if (!jatosOnLoadEventFired && initialized) {
            jatosOnLoadEventFired = true;
            window.dispatchEvent(jatosOnLoadEvent);
        }
    }

    /**
     * A web worker used in jatos.js to send periodic requests back to the
     * JATOS server. With this function one can set the period with which the
     * heartbeat is sent.
     *
     * @param {number} heartbeatPeriod - Period in milliseconds
     */
    jatos.setHeartbeatPeriod = function (heartbeatPeriod) {
        if (typeof heartbeatPeriod == 'number' && heartbeatWorker) {
            heartbeatWorker.postMessage([jatos.studyResultUuid, heartbeatPeriod]);
        }
    };

    return {
        start: initJatos,
        isInitialized: () => initialized,
        terminateHeartbeat: () => heartbeatWorker.terminate()
    };
}
