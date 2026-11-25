/*!
 * jatos.js (JATOS JavaScript Library)
 * http://www.jatos.org
 * Licensed under Apache License 2.0
 */

import {requestHttp} from "./http-transport.js";
import {onError, logError, log, catchAndLogErrors} from "./logging.js";
import {createResultDataApi} from "./result-data.js";
import {createHttpLoop} from "./http-loop.js";
import {createChannels} from "./channels.js";
import {createBrowserUi} from "./browser-ui.js";
import {createInitialization} from "./initialization.js";
import {
    createStudyRunState,
    createStudyRunApi,
    isInvalidComponentPosition
} from "./study-run.js";
import {addJatosIds} from "./utils/add-jatos-ids.js";
import {convertToCSV} from "./utils/convert-to-csv.js";

jatos = {};
window.jatos = jatos; // Make jatos available in the window object for backward compatibility

// Encapsulate the whole library so nothing unintentional gets out
(function () {
    "use strict";

    /**
     * jatos.js version
     */
    jatos.version = "3.11.3";
    /**
     * How long in ms should JATOS wait before retrying the HTTP call?
     */
    jatos.httpTimeout = 30000;
    /**
     * How many times should jatos.js retry to send a failed HTTP call?
     */
    jatos.httpRetry = 5;
    /**
     * How long in ms should jatos.js wait between a failed HTTP call and a retry?
     */
    jatos.httpRetryWait = 1000;
    /**
     * Study input data is configured within the study properties in the GUI.
     * Note that 'studyJsonInput' is the deprecated name; use 'studyInput' instead.
     */
    jatos.studyJsonInput = {};
    jatos.studyInput = {};
    /**
     * Number of components this study has
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

    const studyRunState = createStudyRunState();
    const browserUi = createBrowserUi(jatos);
    jatos.showBeforeUnloadWarning = browserUi.api.showBeforeUnloadWarning;
    jatos.showOverlay = browserUi.api.showOverlay;
    jatos.removeOverlay = browserUi.api.removeOverlay;
    jatos.removeOverlays = browserUi.api.removeOverlays;
    jatos.addAbortButton = browserUi.api.addAbortButton;

    const httpLoop = createHttpLoop({
        isInitialized: () => initialization.isInitialized()
    });

    const channels = createChannels(jatos, {
        requestHttp,
        studyRunState,
        getURL,
        showIdOverlay: browserUi.showIdOverlay,
    });
    jatos.onConnected = channels.api.onConnected;
    jatos.onDisconnected = channels.api.onDisconnected;
    jatos.isConnected = channels.api.isConnected;
    jatos.batchSession = channels.api.batchSession;
    jatos.onBatchSession = channels.api.onBatchSession;
    jatos.joinGroup = channels.api.joinGroup;
    jatos.getGroupState = channels.api.getGroupState;
    jatos.isGroupFixed = channels.api.isGroupFixed;
    jatos.groupSession = channels.api.groupSession;
    jatos.setGroupFixed = channels.api.setGroupFixed;
    jatos.hasJoinedGroup = channels.api.hasJoinedGroup;
    jatos.hasOpenGroupChannel = channels.api.hasOpenGroupChannel;
    jatos.isMaxActiveMemberReached = channels.api.isMaxActiveMemberReached;
    jatos.isMaxActiveMemberOpen = channels.api.isMaxActiveMemberOpen;
    jatos.isGroupOpen = channels.api.isGroupOpen;
    jatos.sendGroupMsg = channels.api.sendGroupMsg;
    jatos.sendGroupMsgTo = channels.api.sendGroupMsgTo;
    jatos.reassignGroup = channels.api.reassignGroup;
    jatos.leaveGroup = channels.api.leaveGroup;

    const initialization = createInitialization(jatos, {
        requestHttp,
        getURL,
        showIdOverlay: browserUi.showIdOverlay,
        httpLoop,
        channels
    });
    jatos.onLoad = initialization.api.onLoad;
    jatos.onload = initialization.api.onload;
    jatos.setHeartbeatPeriod = initialization.api.setHeartbeatPeriod;

    jatos.onLoad(browserUi.onLoad);
    initialization.start();

    const resultData = createResultDataApi(jatos, {
        studyRunState,
        getURL,
        isInitialized: () => initialization.isInitialized(),
        isInvalidComponentPosition: pos => isInvalidComponentPosition(jatos.componentList, pos),
        sendToHttpLoop: httpLoop.send
    });
    jatos.submitResultData = resultData.submitResultData;
    jatos.appendResultData = resultData.appendResultData;
    jatos.uploadResultFile = resultData.uploadResultFile;
    jatos.downloadResultFile = resultData.downloadResultFile;

    const studyRun = createStudyRunApi(jatos, {
        studyRunState,
        removeBeforeUnloadWarning: browserUi.removeBeforeUnloadWarning,
        getURL,
        httpLoop,
        isInitialized: () => initialization.isInitialized(),
        cancelChannelOpenings: channels.cancelChannelOpenings,
        stopStudyRun: () => {
            initialization.terminateHeartbeat();
            httpLoop.terminate();
            channels.stopClosedChecks();
        }
    });
    jatos.setStudySessionData = studyRun.setStudySessionData;
    jatos.startComponent = studyRun.startComponent;
    jatos.startComponentByPos = studyRun.startComponentByPos;
    jatos.startComponentByTitle = studyRun.startComponentByTitle;
    jatos.startNextComponent = studyRun.startNextComponent;
    jatos.startLastComponent = studyRun.startLastComponent;
    jatos.abortStudyWithoutRedirect = studyRun.abortStudyWithoutRedirect;
    jatos.abortStudyAjax = studyRun.abortStudyAjax;
    jatos.abortStudyAndRedirect = studyRun.abortStudyAndRedirect;
    jatos.abortStudy = studyRun.abortStudy;
    jatos.endStudyWithoutRedirect = studyRun.endStudyWithoutRedirect;
    jatos.endStudyAjax = studyRun.endStudyAjax;
    jatos.endStudyAndRedirect = studyRun.endStudyAndRedirect;
    jatos.endStudy = studyRun.endStudy;

    /**
     * Returns the URL with protocol, host and port to the given path
     */
    function getURL(path) {
        return new URL(path, window.location.href).toString();
    }

    jatos.getHttpLoopCounter = function () {
        return httpLoop.getCounter();
    };

    const loggingDependencies = {
        getURL,
        isInitialized: () => initialization.isInitialized(),
        sendToHttpLoop: httpLoop.send
    };
    jatos.onError = onError;
    jatos.logError = logError;
    jatos.log = logMsg => log(jatos, loggingDependencies, logMsg);
    jatos.catchAndLogErrors = () => catchAndLogErrors(jatos);

    jatos.convertToCSV = convertToCSV;
    jatos.addJatosIds = obj => addJatosIds(jatos, obj);

})();
