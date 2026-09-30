/*!
 * jatos.js (JATOS JavaScript Library)
 * http://www.jatos.org
 * Licensed under Apache License 2.0
 *
 * Uses plugin jquery.ajax-retry:
 * https://github.com/johnkpaul/jquery-ajax-retry
 * Copyright (c) 2012 John Paul
 * Licensed under the MIT license.
 *
 * Uses Starcounter-Jack/JSON-Patch:
 * https://github.com/Starcounter-Jack/JSON-Patch
 * Copyright (c) 2017-2022 Joachim Wester
 * Licensed under the MIT license.
 */

import {installResultDataApi} from "./result-data.js";
import {createHttpLoop} from "./http-loop.js";
import {createChannels} from "./channels.js";
import {createInitialization} from "./initialization.js";
import {
    installStudyRunApi,
    isInvalidComponentPosition
} from "./study-run.js";

/**
 * An awaitable, jQuery-compatible promise facade returned by asynchronous
 * jatos.js functions. It is a thenable, but it is not a native `Promise`.
 *
 * `done`, `fail`, and `always` preserve the jQuery-compatible synchronous behavior when
 * registered after settlement. The facade does not expose `resolve` or `reject`.
 *
 * @typedef {Object} JatosPromise
 * @property {function(...Function): JatosPromise} done Adds success handlers.
 * @property {function(...Function): JatosPromise} fail Adds failure handlers.
 * @property {function(...Function): JatosPromise} always Adds settlement handlers.
 * @property {function(Function=, Function=, Function=): JatosPromise} then Returns a chained JatosPromise.
 * @property {function(Function=): JatosPromise} catch Returns a chained JatosPromise.
 * @property {function(Function=, Function=, Function=): JatosPromise} pipe Applies synchronous jQuery-compatible filters.
 * @property {function(...Function): JatosPromise} progress Adds progress handlers.
 * @property {function(): JatosPromise} promise Returns this read-only facade.
 * @property {function(): ("pending"|"resolved"|"rejected")} state Returns its settlement state.
 */

jatos = {};
window.jatos = jatos; // Make jatos available in the window object for backward compatibility

// Encapsulate the whole library so nothing unintentional gets out (e.g. jQuery
// or functions or variables)
(function () {
    "use strict";

    /**
     * jatos.js version
     */
    jatos.version = "3.11.3";
    /**
     * How long in ms should JATOS wait before retrying the HTTP call.
     */
    jatos.httpTimeout = 30000;
    /**
     * How many times should jatos.js retry to send a failed HTTP call.
     */
    jatos.httpRetry = 5;
    /**
     * How long in ms should jatos.js wait between a failed HTTP call and a retry.
     */
    jatos.httpRetryWait = 1000;
    /**
     * Study input data is configured within the study properties in the GUI.
     * Note that 'studyJsonInput' is the deprecated name; use 'studyInput' instead.
     */
    jatos.studyJsonInput = {};
    jatos.studyInput = {};
    /**
     * Number of component this study has
     */
    jatos.studyLength = null;
    /**
     * All the properties (except study input) belonging to the study
     */
    jatos.studyProperties = {};
    /**
     * The study session data can be accessed and modified by every component of
     * this study
     */
    jatos.studySessionData = {};
    /**
     * List of components of this study with some basic info about them
     */
    jatos.componentList = [];
    /**
     * Component input data is configured within the component properties
     * in the GUI. Note that 'componentJsonInput' is the deprecated name; use
     * componentInput' instead.
     */
    jatos.componentJsonInput = {};
    jatos.componentInput = {};
    /**
     * Position of this component in this study (starts with 1)
     */
    jatos.componentPos = null;
    /**
     * All the properties (except component input) belonging to the component
     */
    jatos.componentProperties = {};
    /**
     * All properties of the batch (except batch input)
     */
    jatos.batchProperties = {};
    /**
     * Batch input data is configured within the batch properties in the GUI.
     * Note that 'batchJsonInput' is the deprecated name; use 'batchInput' instead.
     */
    jatos.batchJsonInput = {};
    jatos.batchInput = {};
    /**
     * Config of the overlay that is shown when the component ended but
     * the httpLoop still has requests to send. See function jatos.showOverlay
     * for config options.
     */
    jatos.waitSendDataOverlayConfig = {
        text: "Sending data. Please wait."
    };
    /**
     * State booleans (flags). If true jatos.js is in this state. Several states can be true
     * at the same time.
     */
    let startingComponent = false;
    let endingStudy = false;
    let studyRunInvalid = false;
    /**
     * Flag that determines if the 'beforeunload' warning should be shown
     * by the browser to the worker if they attempt to reload or close the
     * broser (tab)
     */
    let showBeforeUnloadWarning = true;

    const httpLoop = createHttpLoop({
        isInitialized: () => initialization.isInitialized()
    });

    const channels = createChannels(jatos, {
        getURL,
        getAjaxErrorMsg,
        showIdOverlay,
        isEndingStudy: () => endingStudy,
        isStartingComponent: () => startingComponent,
        isStudyRunInvalid: () => studyRunInvalid,
        setStudyRunInvalid: value => { studyRunInvalid = value; }
    });

    const initialization = createInitialization(jatos, {
        getURL,
        getAjaxErrorMsg,
        showIdOverlay,
        httpLoop,
        channels
    });
    initialization.start();

    /**
     * DEPRECATED - Instead use the specific function's error callbacks or Promise functions
     *
     * Defines callback function to be called if jatos.js produces an error.
     */
    jatos.onError = function (onError) {
        console.warn("jatos.onError is abolished - use the specific function's error callback or Promise function");
    };

    installResultDataApi(jatos, {
        getURL,
        isInitialized: () => initialization.isInitialized(),
        isInvalidComponentPosition: pos => isInvalidComponentPosition(jatos.componentList, pos),
        isStudyRunInvalid: () => studyRunInvalid,
        sendToHttpLoop: httpLoop.send
    });

    installStudyRunApi(jatos, {
        beforeUnloadWarning,
        getURL,
        httpLoop,
        isEndingStudy: () => endingStudy,
        isInitialized: () => initialization.isInitialized(),
        isStartingComponent: () => startingComponent,
        isStudyRunInvalid: () => studyRunInvalid,
        setStartingComponent: value => { startingComponent = value; },
        setEndingStudy: value => { endingStudy = value; },
        stopStudyRun: () => {
            initialization.terminateHeartbeat();
            httpLoop.terminate();
            channels.stopClosedChecks();
        }
    });

    /**
     * Returns the URL with protocol, host and port to the given path
     */
    function getURL(path) {
        return new URL(path, window.location.href).toString();
    }

    jatos.getHttpLoopCounter = function () {
        return httpLoop.getCounter();
    };

    /**
     * DEPRECATED - Use jatos.log instead
     *
     * Logs a message within the JATOS log on the server side.
     */
    jatos.logError = function (logErrorMsg) {
        console.warn("jatos.logError is abolished - use jatos.log instead");
    };

    /**
     * Logs a message within the JATOS log on the server side.
     */
    jatos.log = function (logMsg) {
        if (!initialization.isInitialized()) return;

        var request = {
            url: getURL("log"),
            method: "POST",
            data: logMsg,
            contentType: "text/plain; charset=UTF-8",
            timeout: jatos.httpTimeout,
            retry: jatos.httpRetry,
            retryWait: jatos.httpRetryWait
        };
        httpLoop.send(request);
    };

    /**
     * Convenience function that sends all 'error' and 'unhandledrejection'
     * events and console.error and console.warn calls to JATOS server log
     */
    jatos.catchAndLogErrors = function () {
        window.addEventListener('error', function (e) {
            jatos.log(`Via 'error' event in ${e.filename}:${e.lineno} - ${e.message}`);
        });
        window.addEventListener('unhandledrejection', function (e) {
            jatos.log(`Via 'unhandledrejection' event in ${e.filename}:${e.lineno} - ${e.message}`);
        });

        var errorLog = console.error;
        var warnLog = console.warn;
        console.error = function (message) {
            jatos.log("Via console.error - " + message);
            errorLog.apply(this, arguments);
        };
        console.warn = function (message) {
            jatos.log("Via console.warn - " + message);
            warnLog.apply(this, arguments);
        };
    };

    /**
     * Convenience function that adds all JATOS IDs (study ID, study title,
     * component ID, component position, component title, worker ID,
     * study result ID, component result ID, group result ID, group member ID)
     * to the given object.
     *
     * @param {Object} [obj={}] - Object to which the IDs will be added
     * @return {Object} The same object passed in with IDs added
     */
    jatos.addJatosIds = function (obj = {}) {
        obj.studyCode = jatos.studyCode;
        obj.studyId = jatos.studyId;
        obj.studyTitle = jatos.studyProperties.title;
        obj.batchId = jatos.batchId;
        obj.batchTitle = jatos.batchProperties.title;
        obj.componentId = jatos.componentId;
        obj.componentPos = jatos.componentPos;
        obj.componentTitle = jatos.componentProperties.title;
        obj.workerId = jatos.workerId;
        obj.studyResultId = jatos.studyResultId;
        obj.componentResultId = jatos.componentResultId;
        obj.groupResultId = jatos.groupResultId;
        obj.groupMemberId = jatos.groupMemberId;
        return obj;
    };

    /**
     * Warn worker with a popup that the component is not reloadable and leaving the page would end the study
     * Remember: This works only if at least one user action happend in the window (e.g. mouse click)
     * Check: https://developer.mozilla.org/en-US/docs/Web/API/Window/beforeunload_event
     */
    jatos.onLoad(function () {
        if (showBeforeUnloadWarning && !jatos.componentProperties.reloadable) {
            window.addEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        }
    });

    var beforeUnloadWarning = function (event) {
        event.preventDefault();
        // Most browsers do not show this message but a standardized one
        event.returnValue = "Are you sure you want to leave?";
    };

    /**
     * Adds or cancels warning popup that will be shown by the browser to the worker who
     * attempts to reload the page or close the browser (tab).
     *
     * @param {boolean} show - If true the warning will be shown - if false a
     * 		previously added warning will be canceled
     */
    jatos.showBeforeUnloadWarning = function (show) {
        showBeforeUnloadWarning = show;
        if (show) {
            window.addEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        } else {
            window.removeEventListener('beforeunload', beforeUnloadWarning, { capture: true });
        }
    };

    /**
     * Adds an overlay to the document that shows a text and an image underneath
     * in the center of the screen. By default the text is 'Please wait.' and the
     * image is a spinning wheel. If an element with the provided ID already exists
     * just the text content will be updated.
     *
     * @param {Object} [config] - Config object
     * @param {boolean} [config.show=true] - If true the overlay is shown
     * @param {boolean} [config.keep=false] - Keep the overlay when `jatos.removeOverlays` is called
     * @param {string} [config.text="Please wait"] - Text to be shown
     * @param {string} [config.imgUrl] - URL of the image (default is a spinning wheel)
     * @param {boolean} [config.showImg=true] - If true the image is shown
     * @param {string} [config.style] - Additional CSS styles
     * @param {string} [config.id] - Element ID
     * @param {string} [config.className] - Additional class name
     * @param {number} [config.timeout] - If set the overlay will be removed after the given milliseconds
     * @return {HTMLElement} The created element (or updated existing one)
     */
    jatos.showOverlay = function (config) {
        if (config && typeof config.show == "boolean" && !config.show) return;

        // If an element with the given ID already exists just update the text and return
        if (config && typeof config.id == "string") {
            const el = document.getElementById(config.id);
            if (el) {
                if (config && config.text) el.textContent = config.text;
                return el;
            }
        }

        // Create div
        const div = document.createElement('div');

        // Add style
        let divStyle = 'color: black;' +
            'font-family: Sans-Serif;' +
            'font-size: 30px;' +
            'letter-spacing: 2px;' +
            'opacity: 0.6;' +
            'text-shadow: -1px 0 white, 0 1px white, 1px 0 white, 0 -1px white;' +
            'z-index: 9999;' +
            'position: absolute;' +
            'left: 50%;' +
            'top: 50%;' +
            'transform: translate(-50%, -50%);' +
            'display: flex;' +
            'align-items: center;' +
            'justify-content: center;' +
            'flex-direction: column;';
        if (config && typeof config.style == "string") divStyle += ";" + config.style;
        div.style.cssText = divStyle;

        // Add ID and classes
        if (config && typeof config.id == "string") div.id = config.id;
        div.classList.add("jatosOverlay");
        if (config && typeof config.className == "string") div.classList.add(config.className);

        // Add Text
        div.textContent = config ? config.text : "Please wait";

        // Add image
        const showImg = (config && typeof config.showImg == "boolean") ? config.showImg : true;
        if (showImg) {
            var imgUrl = (config && typeof config.imgUrl == "string") ? config.imgUrl
                : "jatos-publix/images/waiting.gif";
            var waitingImg = document.createElement('img');
            waitingImg.src = imgUrl;
            waitingImg.style.marginTop = "10px";
            div.appendChild(waitingImg);
        }

        // Add data attribute 'keep'
        const keep = (config && typeof config.keep == "boolean") ? config.keep : false;
        div.setAttribute('data-keep', keep);

        // Set timeout
        if (config && typeof config.timeout == "number") {
            setTimeout(() => div.remove(), config.timeout);
        }

        document.body.appendChild(div);
        return div;
    };

    // Keep this for backward compatibility
    jatos.removeOverlay = () => jatos.removeOverlays();

    /**
     * Removes all overlays that have the class 'jatosOverlay' and the data attribute
     * 'keep' set to "false". If force is true it also removes the ones with the data
     * attribute 'keep' set to "true".
     * @param {boolean} [force=false] - If true, remove overlays even if they are marked to keep
     */
    jatos.removeOverlays = function (force) {
        document.querySelectorAll('.jatosOverlay').forEach(el => {
            if (el.dataset.keep === "false" || force) el.remove();
        });
    };

    /**
     * Uses an overlay to show some IDs if worker type is 'Jatos'
     */
    function showIdOverlay() {
        if (jatos.workerType !== "Jatos") return;

        const idObj = {};
        if (jatos.frameId) idObj["frame"] = jatos.frameId;
        if (jatos.workerId) idObj["worker"] = jatos.workerId;
        if (jatos.studyResultId) idObj["study result"] = jatos.studyResultId;
        if (jatos.groupResultId) idObj["group"] = jatos.groupResultId;

        const text = Object.entries(idObj).map(([key, value]) => `${key}: ${value}`).join("\n");
        jatos.showOverlay({
            id: "idOverlay",
            text: text,
            style: "position:fixed;top:unset;left:4px;bottom:4px;transform:unset;font-size:10px;letter-spacing:0px;white-space:pre;line-height:normal;letter-spacing:normal;word-spacing:normal;text-align:left;",
            keep: true,
            showImg: false
        });
    }

    /**
     * Adds a button to the document that if pressed calls jatos.abortStudy.
     * By default this button is in the bottom-right corner but this and
     * other properties can be configured.
     *
     * @param {Object} [config] - Config object
     * @param {string} [config.text] - Button text
     * @param {boolean} [config.confirm=true] - Ask the worker for confirmation before aborting
     * @param {string} [config.confirmText] - Confirmation text
     * @param {string} [config.tooltip] - Tooltip text
     * @param {string} [config.msg] - Message to be sent back to JATOS and logged
     * @param {string} [config.style] - Additional CSS styles for the button element
     * @param {Function} [config.action] - Function to call instead of `jatos.abortStudy`
     */
    jatos.addAbortButton = function (config) {
        var buttonText = (config && typeof config.text == "string") ?
            config.text : "Cancel";
        var confirm = (config && typeof config.confirm == "boolean") ?
            config.confirm : true;
        var confirmText = (config && typeof config.confirmText == "string") ?
            config.confirmText : "Do you really want to cancel this study?";
        var tooltip = (config && typeof config.tooltip == "string") ?
            config.tooltip : "Cancels this study and deletes all already submitted data";
        var msg = (config && typeof config.msg == "string") ?
            config.msg : "Worker decided to abort";
        var style = 'color:black;' +
            'font-family:Sans-Serif;' +
            'font-size:20px;' +
            'letter-spacing:2px;' +
            'position:fixed;' +
            'margin:2em 0 0 2em;' +
            'bottom:1em;' +
            'right:1em;' +
            'opacity:0.6;' +
            'z-index:9999;' +
            'cursor:pointer;' +
            'text-shadow:-1px 0 white, 0 1px white, 1px 0 white, 0 -1px white;';
        if (config && typeof config.style == "string") style += ";" + config.style;

        var text = document.createTextNode(buttonText);
        var buttonDiv = document.createElement('div');
        buttonDiv.appendChild(text);
        buttonDiv.style.cssText = style;
        buttonDiv.setAttribute("title", tooltip);
        buttonDiv.addEventListener("click", function () {
            if (!confirm || window.confirm(confirmText)) {
                if (config && typeof config.action == "function") {
                    config.action(msg);
                } else {
                    jatos.abortStudy(msg);
                }
            }
        });

        document.body.appendChild(buttonDiv);
    };

    /**
     * Takes a jQuery Ajax response and returns an error message.
     */
    function getAjaxErrorMsg(jqxhr) {
        if (jqxhr.statusText === 'timeout') {
            return "JATOS server not responding";
        } else {
            if (jqxhr.responseText) {
                return jqxhr.statusText + ": " + jqxhr.responseText;
            } else {
                return jqxhr.statusText + ": " + "Error during Ajax call to JATOS server.";
            }
        }
    }


})();
