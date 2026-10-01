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

import {installLoggingApi} from "./logging.js";
import {installResultDataApi} from "./result-data.js";
import {createHttpLoop} from "./http-loop.js";
import {createChannels} from "./channels.js";
import {createBrowserUi} from "./browser-ui.js";
import {createInitialization} from "./initialization.js";
import {
    installStudyRunApi,
    isInvalidComponentPosition
} from "./study-run.js";

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
     * State booleans (flags). If true jatos.js is in this state. Several states can be true
     * at the same time.
     */
    let startingComponent = false;
    let endingStudy = false;
    let studyRunInvalid = false;
    const browserUi = createBrowserUi(jatos);

    const httpLoop = createHttpLoop({
        isInitialized: () => initialization.isInitialized()
    });

    const channels = createChannels(jatos, {
        getURL,
        getAjaxErrorMsg,
        showIdOverlay: browserUi.showIdOverlay,
        isEndingStudy: () => endingStudy,
        isStartingComponent: () => startingComponent,
        isStudyRunInvalid: () => studyRunInvalid,
        setStudyRunInvalid: value => { studyRunInvalid = value; }
    });

    const initialization = createInitialization(jatos, {
        getURL,
        getAjaxErrorMsg,
        showIdOverlay: browserUi.showIdOverlay,
        httpLoop,
        channels
    });
    jatos.onLoad(browserUi.onLoad);
    initialization.start();

    installResultDataApi(jatos, {
        getURL,
        isInitialized: () => initialization.isInitialized(),
        isInvalidComponentPosition: pos => isInvalidComponentPosition(jatos.componentList, pos),
        isStudyRunInvalid: () => studyRunInvalid,
        sendToHttpLoop: httpLoop.send
    });

    installStudyRunApi(jatos, {
        removeBeforeUnloadWarning: browserUi.removeBeforeUnloadWarning,
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

    installLoggingApi(jatos, {
        getURL,
        isInitialized: () => initialization.isInitialized(),
        sendToHttpLoop: httpLoop.send
    });

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
