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

/* global jsonpatch */

import {call, callMany, callWithArgs} from "./utils/callbacks.js";
import {cloneJsonObj} from "./utils/clone-json.js";
import {createLegacyPromiseCompatibility, isDeferredPending} from "./jatos-promise.js";
import {installResultDataApi} from "./result-data.js";
import {createHttpLoop} from "./http-loop.js";
import {
    installStudyRunApi,
    isInvalidComponentPosition
} from "./study-run.js";

/**
 * An awaitable, jQuery-compatible promise facade returned by asynchronous
 * jatos.js functions. It is a thenable, but it is not a native `Promise`.
 *
 * `done`, `fail`, and `always` preserve the legacy synchronous behavior when
 * registered after settlement. The facade does not expose `resolve` or `reject`.
 *
 * @typedef {Object} JatosPromise
 * @property {function(...Function): JatosPromise} done Adds success handlers.
 * @property {function(...Function): JatosPromise} fail Adds failure handlers.
 * @property {function(...Function): JatosPromise} always Adds settlement handlers.
 * @property {function(Function=, Function=): JatosPromise} then Returns a chained JatosPromise.
 * @property {function(Function=): JatosPromise} catch Returns a chained JatosPromise.
 * @property {function(Function=, Function=): JatosPromise} pipe Legacy alias for transformation.
 * @property {function(...Function): JatosPromise} progress Adds progress handlers.
 * @property {function(): JatosPromise} promise Returns this read-only facade.
 * @property {function(): ("pending"|"resolved"|"rejected")} state Returns its settlement state.
 */

jatos = {};
window.jatos = jatos; // Make jatos available in the window object for backward compatibility

const {createDeferred, rejectedPromise} = createLegacyPromiseCompatibility();

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
     * Group member ID is unique for this member (it is actually identical with the
     * study result ID)
     */
    jatos.groupMemberId = null;
    /**
     * Unique ID of this group
     */
    jatos.groupResultId = null;
    /**
     * Member IDs of the current members of the group result
     */
    jatos.groupMembers = [];
    /**
     * Member IDs of the currently open group channels. Don't confuse with internal
     * groupChannel variable.
     */
    jatos.groupChannels = [];
    /**
     * Group state: can be STARTED, FINISHED, or FIXED
     */
    let groupState = null;
    /**
     * Group session data: shared in between members of the group
     */
    let groupSessionData = {};
    /**
     * Batch session data: shared in between study runs of the same batch
     */
    let batchSessionData = {};
    /**
     * How long in ms should jatos.js wait for an answer after message was sent via
     * a group or batch channel.
     */
    jatos.channelSendingTimeoutTime = 10000;
    /**
     * Waiting time in ms between channel heartbeats
     */
    jatos.channelHeartbeatInterval = 10000;
    /**
     * Waiting time in ms for JATOS answer to a channel heartbeat ('pong')
     */
    jatos.channelHeartbeatTimeoutTime = 10000;
    /**
     * Waiting time in ms between checking if channels are closed unexpectedly
     */
    jatos.channelClosedCheckInterval = 2000;
    /**
     * Min and max waiting time between channel reopening attempts
     */
    jatos.channelOpeningBackoffTimeMin = 1000;
    jatos.channelOpeningBackoffTimeMax = 120000; // 2 min
    /**
     * Config of the overlay that is shown when the component ended but
     * the httpLoop still has requests to send. See function jatos.showOverlay
     * for config options.
     */
    jatos.waitSendDataOverlayConfig = {
        text: "Sending data. Please wait."
    };
    /**
     * All batch/group session actions currently waiting for an response.
     * Maps sessionActionId -> timeout object
     */
    const batchSessionTimeouts = {};
    const groupSessionTimeouts = {};
    /**
     * Channel timeout and interval objects
     */
    let groupFixedTimeout;
    let batchChannelHeartbeatTimer;
    let groupChannelHeartbeatTimer;
    let batchChannelHeartbeatTimeoutTimers = [];
    let groupChannelHeartbeatTimeoutTimers = [];
    let batchChannelClosedCheckTimer;
    let groupChannelClosedCheckTimer;
    /**
     * Version of the current group/batch session data. The version is
     * used to prevent concurrent changes of the data. Can be switch on/off
     * by flags *SessionVersioning.
     */
    let batchSessionVersion;
    let groupSessionVersion;
    /**
     * Number of batch/group session updates so far. Used to generate the
     * sessionActionId.
     */
    let batchSessionCounter = 0;
    let groupSessionCounter = 0;
    /**
     * If versioning is set to true all batch/group session data patches are
     * accompanied by a version. On the JATOS server side only the a patch with
     * the current version (as stored in the database) is applied. If there are
     * multiple concurrent patches only the first one is applied.
     * If versioning is turned off all patches arriving at the JATOS server are
     * applied right away without checking the version. This is faster but can
     * lead to unintended session data changes.
     */
    jatos.batchSessionVersioning = true;
    jatos.groupSessionVersioning = true;
    /**
     * Batch channel WebSocket: exchange date between study runs of a batch
     */
    let batchChannel;
    /**
     * Group channel WebSocket to exchange messages between workers of a group.
     * Not to be confused with 'jatos.groupChannels'. Accessible only by jatos.js.
     */
    let groupChannel;
    /**
     * Object with group channel callbacks (details in jatos.joinGroup)
     */
    let groupChannelCallbacks;
    /**
     * WebSocket support by the browser is needed for group channel.
     */
    const webSocketSupported = 'WebSocket' in window;
    /**
     * Web worker initialized in initJatos() that sends a periodic request
     * back to the JATOS server. Don't confuse with channel heartbeats.
     */
    let heartbeatWorker;
    /**
     * State booleans (flags). If true jatos.js is in this state. Several states can be true
     * at the same time.
     */
    let initialized = false;
    let jatosOnLoadEventFired = false;
    let batchChannelAlive = false;
    let startingComponent = false;
    let endingStudy = false;
    let studyRunInvalid = false;
    /**
     * jQuery.Deferred objects: can hold state pending, resolved, or rejected
     */
    let openingBatchChannelDeferred;
    let sendingBatchSessionDeferred;
    let openingGroupChannelDeferred;
    let sendingGroupSessionDeferred;
    let sendingGroupFixedDeferred;
    let reassigningGroupDeferred;
    let leavingGroupDeferred;
    /**
     * Event fired when jatos.js is initialized (e.g. init data loaded and channels opened)
     */
    const jatosOnLoadEvent = new Event("jatosOnLoad");
    /**
     * Events fired when the batch channel heartbeat works or fails
     */
    const batchChannelAliveEvent = new Event("batchChannelAlive");
    const batchChannelDeadEvent = new Event("batchChannelDead");
    /**
     * Callback function defined via jatos.onBatchSession
     */
    let onJatosBatchSession;
    /**
     * Flag that determines if the 'beforeunload' warning should be shown
     * by the browser to the worker if they attempt to reload or close the
     * broser (tab)
     */
    let showBeforeUnloadWarning = true;

    const httpLoop = createHttpLoop({
        createDeferred,
        isInitialized: () => initialized
    });

    // Load jatos.js's jQuery and put it in jatos.jQuery to avoid conflicts with
    // a component's jQuery version. Afterwards call initJatos.
    jatos.jQuery = {};
    getScript('jatos-publix/javascripts/jquery-3.7.1.min.js', function () {
        jatos.jQuery = jQuery.noConflict(true);
        jatos.jQuery.ajaxSetup({
            cache: true
        });
        initJatos();
    });

    /**
     * Adds a <script> element into HTML's head and call success function when loaded
     */
    function getScript(url, onSuccess) {
        const script = document.createElement('script');
        script.src = url;
        const head = document.getElementsByTagName('head')[0];
        let done = false;
        script.onload = script.onreadystatechange = function () {
            if (!done && (!this.readyState || this.readyState === 'loaded' ||
                this.readyState === 'complete')) {
                done = true;
                onSuccess();
                script.onload = script.onreadystatechange = null;
                head.removeChild(script);
            }
        };
        head.appendChild(script);
    }

    /**
     * Initialising jatos.js
     */
    function initJatos() {

        // "There is a natural order to this world, and those who try to upend it do not fare well."
        // 1) Load additional scripts
        // 2) Do more init stuff that doesn't involve HTTP requests
        // 3) Get init data from JATOS server
        // 4) Try to open the batch channel
        // 5) Call readyForOnLoad
        jatos.jQuery.when(
            // Load jQuery plugin to retry ajax calls: https://github.com/johnkpaul/jquery-ajax-retry
            jatos.jQuery.getScript("jatos-publix/javascripts/jquery.ajax-retry.min.js"),
            // Load JSON Patch library https://github.com/Starcounter-Jack/JSON-Patch
            jatos.jQuery.getScript("jatos-publix/javascripts/fast-json-patch.min.js")
        )
            .then(function () {
                // Get studyResultUuid from URL path
                jatos.studyResultUuid = window.location.pathname.split("/").reverse()[2];
                readIdCookie();
                // Start heartbeat.js (the general one - not the channel one)
                heartbeatWorker = new Worker("jatos-publix/javascripts/heartbeat.js");
                heartbeatWorker.postMessage([jatos.studyResultUuid]);
                // Start httpLoop.js
                httpLoop.start();
            })
            .then(getInitData)
            .then(showIdOverlay)
            .then(openBatchChannelWithRetry)
            .always(function () {
                initialized = true;
                readyForOnLoad();
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
        return jatos.jQuery.ajax({
            url: getURL("initData"),
            type: "GET",
            dataType: 'json',
            timeout: jatos.httpTimeout,
            success: setInitData,
            error: (err) => console.error(getAjaxErrorMsg(err))
        }).retry({
            times: jatos.httpRetry,
            timeout: jatos.httpRetryWait
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
            jatos.batchJsonInput = jatos.jQuery
                .parseJSON(jatos.batchProperties.batchInput);
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
            jatos.studyJsonInput = jatos.jQuery
                .parseJSON(jatos.studyProperties.studyInput);
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
            jatos.componentJsonInput = jatos.jQuery
                .parseJSON(jatos.componentProperties.componentInput);
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
     * @param {function} callback - callback function
     */
    jatos.onLoad = function (callback) {
        if (!jatosOnLoadEventFired) {
            window.addEventListener("jatosOnLoad", callback);
            readyForOnLoad();
        } else {
            callback();
        }
    };

    /**
     * Just for convenience. People are used to 'onload' all lower case
     */
    jatos.onload = jatos.onLoad;

    /**
     * Calls onLoadCallback if it already exists and jatos.js is initialised.
     * We can't use Deferred since jQuery might not be defined yet.
     */
    function readyForOnLoad() {
        if (!jatosOnLoadEventFired && initialized) {
            jatosOnLoadEventFired = true;
            window.dispatchEvent(jatosOnLoadEvent);
        }
    }

    /**
     * Adds the callback function to the batchChannelAlive event listener. The batchChannelAlive event gets fired
     * when jatos.js establishes a connection to JATOS which is detected by the heartbeat in the batch channel.
     */
    jatos.onConnected = function (callback) {
        window.addEventListener("batchChannelAlive", callback);
    }

    /**
     * Adds the callback function to the batchChannelDead event listener. The batchChannelDead event gets fired
     * when jatos.js loses its connection to JATOS which is detected by the heartbeat in the batch channel.
     */
    jatos.onDisconnected = function (callback) {
        window.addEventListener("batchChannelDead", callback);
    }

    /**
     * Returns true if jatos.js established a connection to JATOS which is detected by the heartbeat in the batch
     * channel. False otherwise.
     */
    jatos.isConnected = function () {
        return batchChannelAlive;
    }

    /**
     * Open batch channel with retry and exponential backoff
     */
    function openBatchChannelWithRetry(backoffTime) {
        if (typeof backoffTime !== "number") backoffTime = jatos.channelOpeningBackoffTimeMin;
        return openBatchChannel().fail(function () {
            if (backoffTime < jatos.channelOpeningBackoffTimeMax) backoffTime *= 2;
            setTimeout(function () {
                openBatchChannelWithRetry(backoffTime);
            }, backoffTime);
        });
    }

    /**
     * Opens the WebSocket for the batch channel which is used to get and
     * update the batch session data.
     */
    function openBatchChannel() {
        if (!webSocketSupported) {
            const errorMsg = "This browser does not support WebSockets. Can't open batch channel.";
            console.warn(errorMsg);
            return rejectedPromise(errorMsg);
        }
        // WebSocket's readyState:
        //		CONNECTING 0 The connection is not yet open.
        //		OPEN       1 The connection is open and ready to communicate.
        //		CLOSING    2 The connection is in the process of closing.
        //		CLOSED     3 The connection is closed or couldn't be opened.
        if (batchChannel && batchChannel.readyState !== batchChannel.CLOSED) {
            return rejectedPromise("Can't open a WebSocket that is not in readyState CLOSED.");
        }
        if (endingStudy || startingComponent) {
            const errorMsg = "Won't open batch channel because study is about to move to the next component or finish.";
            console.info(errorMsg);
            return rejectedPromise(errorMsg);
        }
        if (studyRunInvalid) {
            const errorMsg = "Can't open batch channel. This study run is invalid.";
            console.warn(errorMsg);
            return rejectedPromise(errorMsg);
        }
        if (isDeferredPending(openingBatchChannelDeferred)) {
            const errorMsg = "Can open only one batch channel.";
            console.warn(errorMsg);
            return rejectedPromise(errorMsg);
        }
        openingBatchChannelDeferred = createDeferred();

        const channel = new WebSocket(
            ((window.location.protocol === "https:") ? "wss://" : "ws://") +
            window.location.host + jatos.urlBasePath + "publix/" + jatos.studyResultUuid + "/batch/open");
        batchChannel = channel;
        // Capture this attempt so delayed callbacks cannot affect a replacement channel or deferred.
        const openingDeferred = openingBatchChannelDeferred;
        channel.onopen = function () {
            if (batchChannel !== channel) return;
            channel.send('{"action":"READY"}');
            batchChannelHeartbeat();
            batchChannelClosedCheck();
            // The actual batch channel opening is done when we have the
            // current version of the batch session
        };
        channel.onmessage = function (event) {
            if (batchChannel !== channel) return;
            handleBatchMsg(event.data);
        };
        channel.onerror = function () {
            if (batchChannel !== channel) return;
            console.error("Batch channel error");
            openingDeferred.reject();
        };
        // Some browsers call it with leaving/reloading the page
        // Called with closing the WebSocket intentionally
        // Called with network error, after ws.onerror
        channel.onclose = function () {
            if (batchChannel !== channel) return;
            setBatchChannelDead();
            clearBatchChannel();
            openingDeferred.reject();
        };

        return openingBatchChannelDeferred.promise();
    }

    /**
     * Closes the batch channel, cleans channel objects and timers and reopens
     * the channel.
     */
    function reopenBatchChannel() {
        if (isDeferredPending(openingBatchChannelDeferred)) return;
        if (batchChannel instanceof WebSocket) batchChannel.close();
        clearBatchChannel();
        openBatchChannelWithRetry();
    }

    /**
     * Periodically sends a heartbeat in the batch channel. This is supposed
     * to keep the WebSocket open in routers. This heartbeat is additional
     * to the ping/pong heartbeat of the underlying WebSocket. For each
     * heartbeat ping we set a timeout until when the pong has to be received.
     * If no pong arrived the batch channel will be closed and reopened.
     */
    function batchChannelHeartbeat() {
        clearInterval(batchChannelHeartbeatTimer);
        batchChannelHeartbeatTimer = setInterval(function () {
            if (batchChannel.readyState === batchChannel.OPEN) {
                batchChannel.send('{"heartbeat":"ping"}');
                const timeout = setTimeout(handleBatchChannelHeartbeatFail,
                    jatos.channelHeartbeatTimeoutTime);
                batchChannelHeartbeatTimeoutTimers.push(timeout);
            }
        }, jatos.channelHeartbeatInterval);
    }

    /**
     * Batch channel is dead: set batchChannelAlive flag,
     * fire batchChannelDeadEvent and reopen batch channel
     */
    function handleBatchChannelHeartbeatFail() {
        console.warn("Batch channel heartbeat fail");
        setBatchChannelDead();
        reopenBatchChannel();
    }

    /**
     * Periodically checks whether the batch channel is closed and if yes
     * reopens it. We don't rely on WebSocket's onClose callback (we could
     * just put reopenBatchChannel() in there) because it's not always called
     * and additionally sometimes called (unwanted) in case of a page
     * reload/closing.
     */
    function batchChannelClosedCheck() {
        clearInterval(batchChannelClosedCheckTimer);
        batchChannelClosedCheckTimer = setInterval(function () {
            if (batchChannel.readyState === batchChannel.CLOSED) {
                console.info("Batch channel closed");
                clearInterval(batchChannelClosedCheckTimer);
                setBatchChannelDead();
                reopenBatchChannel();
            }
        }, jatos.channelClosedCheckInterval);
    }

    function clearBatchChannel() {
        batchSessionData = {};
        batchSessionVersion = null;
        clearBatchChannelHeartbeatTimeoutTimers();
        clearInterval(batchChannelHeartbeatTimer);
        // Don't clear batchChannelClosedCheckTimer here
    }

    function clearBatchChannelHeartbeatTimeoutTimers() {
        batchChannelHeartbeatTimeoutTimers.forEach(function (timeout) {
            clearTimeout(timeout);
        });
        batchChannelHeartbeatTimeoutTimers = [];
    }

    /**
     * Handles a batch msg received via the batch channel
     */
    function handleBatchMsg(msg) {
        let batchMsg;
        try {
            batchMsg = JSON.parse(msg);
        } catch (error) {
            console.error(error);
            return;
        }
        if (typeof batchMsg.heartbeat != 'undefined' && batchMsg.heartbeat === 'pong') {
            // Batch channel is alive:  clear all heartbeat timeouts
            // and set batchChannelAlive flag and fire batchChannelAliveEvent
            clearBatchChannelHeartbeatTimeoutTimers();
            setBatchChannelAlive();
            return;
        }
        if (typeof batchMsg.patches != 'undefined') {
            const patchResults = jsonpatch.applyPatch(batchSessionData, batchMsg.patches);
            if (patchResults && patchResults.newDocument !== undefined) {
                batchSessionData = patchResults.newDocument;
            }
        }
        if (typeof batchMsg.data != 'undefined') {
            if (batchMsg.data === null) {
                batchSessionData = {};
            } else {
                batchSessionData = batchMsg.data;
            }
        }
        if (typeof batchMsg.version != 'undefined') {
            batchSessionVersion = batchMsg.version;
            if (isDeferredPending(openingBatchChannelDeferred)) {
                // Batch channel opening is only done when we have the batch session version
                console.info("Batch channel opened");
                openingBatchChannelDeferred.resolve();
            }
        }
        if (typeof batchMsg.action != 'undefined') {
            handleBatchAction(batchMsg);
        }
    }

    /**
     * Handles a batch action message received via the batch channel
     */
    function handleBatchAction(batchMsg) {
        switch (batchMsg.action) {
            case "OPENED":
                setBatchChannelAlive();
                break;
            case "SESSION":
                // Call onJatosBatchSession with JSON Patch's path and
                // op (operation) for each patch
                batchMsg.patches.forEach(function (patch) {
                    callWithArgs(onJatosBatchSession, patch.path, patch.op);
                });
                break;
            case "SESSION_ACK":
                if (batchSessionTimeouts.hasOwnProperty(batchMsg.id)) {
                    batchSessionTimeouts[batchMsg.id].cancel("Batch session update successful");
                } else {
                    console.error("Batch session got 'SESSION_ACK' with nonexistent ID " + batchMsg.id);
                }
                break;
            case "SESSION_FAIL":
                if (batchSessionTimeouts.hasOwnProperty(batchMsg.id)) {
                    const errorMsg = batchMsg.errorMsg || "Batch session update failed";
                    batchSessionTimeouts[batchMsg.id].trigger(errorMsg);
                } else {
                    console.error("Batch session got 'SESSION_FAIL' with nonexistent ID " + batchMsg.id);
                }
                break;
            case "CLOSED":
                clearInterval(batchChannelClosedCheckTimer);
                setBatchChannelDead();
                studyRunInvalid = true;
                console.info("Batch channel closed by JATOS server");
                jatos.showOverlay({ text: "This study run is invalid.", showImg: false });
                break;
            case "ERROR":
                console.error(batchMsg.errorMsg);
                break;
        }
    }

    /**
     * Marks the batch channel as alive and fires the corresponding event once.
     */
    function setBatchChannelAlive() {
        if (!batchChannelAlive) {
            batchChannelAlive = true;
            window.dispatchEvent(batchChannelAliveEvent);
        }
    }

    /**
     * Marks the batch channel as dead and fires the corresponding event once.
     */
    function setBatchChannelDead() {
        if (batchChannelAlive) {
            batchChannelAlive = false;
            window.dispatchEvent(batchChannelDeadEvent);
        }
    }

    /**
     * Object contains all batch session functions
     */
    jatos.batchSession = {};

    /**
     * Getter for a field in the batch session data. Takes a name
     * and returns the matching value, or undefined if the name does not
     * correspond to an existing field. Works only on the first
     * level of the object tree. For all other levels use
     * jatos.batchSession.find. Gets the object from the
     * locally stored copy of the session and does not call
     * the server.
     * @param {string} name - name of the field
     * @return {object}
     */
    jatos.batchSession.get = function (name) {
        const obj = jsonpatch.getValueByPointer(batchSessionData, "/" + name);
        return cloneJsonObj(obj);
    };

    /**
     * Returns the complete batch session data (might be bad performance-wise)
     * Gets the object from the locally stored copy of the session
     * and does not call the server.
     * @return {object}
     */
    jatos.batchSession.getAll = function () {
        const obj = jatos.batchSession.find("");
        return cloneJsonObj(obj);
    };

    /**
     * Getter for a field in the batch session data. Takes a
     * JSON Pointer and returns the matching value, or undefined if
     * the pointer does not correspond to an existing field. Gets the
     * object from the locally stored copy of the session
     * and does not call the server.
     * @param {string} path - JSON pointer path
     * @return {object}
     */
    jatos.batchSession.find = function (path) {
        const obj = jsonpatch.getValueByPointer(batchSessionData, path);
        return cloneJsonObj(obj);
    };

    /**
     * This function defines the JSON Patch test operation but it
     * does not use the 'test' operation of the JSON patch
     * implementation, but uses the JSON pointer implementation
     * instead.
     * @param {string} path - JSON pointer path to be tested
     * @param {object} value - value to be tested
     * @return {boolean}
     */
    jatos.batchSession.test = function (path, value) {
        const obj = jsonpatch.getValueByPointer(batchSessionData, path);
        return obj === value;
    };

    /**
     * Check if the field under the given path exists.
     * @param {string} path - JSON pointer path
     * @return {boolean}
     */
    jatos.batchSession.defined = function (path) {
        return !jatos.batchSession.test(path, undefined);
    };

    /**
     * JSON Patch add operation
     * @param {string} path - JSON pointer path
     * @param {object} value - value to be stored
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.batchSession.add = function (path, value, onSuccess, onFail) {
        const patch = generatePatch("add", path, value, null);
        return sendBatchSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * Like JSON Patch add operation, but instead of a path accepts
     * a name of the field to be stored. Works only on the first level
     * of the object tree.
     * @param {string} name - name of the field
     * @param {object} value - value to be stored
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.batchSession.set = function (name, value, onSuccess, onFail) {
        const patch = generatePatch("add", "/" + name, value, null);
        return sendBatchSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * Replaces the whole session data (might be bad performance-wise)
     * @param {object} value - value to be stored in the session
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.batchSession.setAll = function (value, onSuccess, onFail) {
        return jatos.batchSession.replace("", value, onSuccess, onFail);
    };

    /**
     * JSON Patch remove operation
     * @param {string} path - JSON pointer path to the field that should be removed
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.batchSession.remove = function (path, onSuccess, onFail) {
        const patch = generatePatch("remove", path, null, null);
        return sendBatchSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * Clears the batch session data.
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.batchSession.clear = function (onSuccess, onFail) {
        const patch = generatePatch("replace", "", {}, null);
        return sendBatchSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * JSON Patch replace operation
     * @param {string} path - JSON pointer path
     * @param {object} value - value to be replaced with
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.batchSession.replace = function (path, value, onSuccess, onFail) {
        const patch = generatePatch("replace", path, value, null);
        return sendBatchSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * JSON Patch copy operation
     * @param {string} from - JSON pointer path to the origin
     * @param {string} path - JSON pointer path to the target
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.batchSession.copy = function (from, path, onSuccess, onFail) {
        const patch = generatePatch("copy", path, null, from);
        return sendBatchSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * JSON Patch move operation
     * @param {string} from - JSON pointer path to the origin
     * @param {string} path - JSON pointer path to the target
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.batchSession.move = function (from, path, onSuccess, onFail) {
        const patch = generatePatch("move", path, null, from);
        return sendBatchSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * Generates an abstract JSON Patch
     */
    function generatePatch(op, path, value, from) {
        const patch = {};
        patch.op = op;
        if (path !== null) {
            patch.path = path;
        }
        if (value !== null) {
            patch.value = value;
        }
        if (from !== null) {
            patch.from = from;
        }
        return patch;
    }

    /**
     * Sends JSON Patch(es) via the batch channel to JATOS and subsequently to all
     * other study currently running in this batch. The parameter 'patches' can be a
     * a single patch object or an array of patch objects.
     */
    function sendBatchSessionPatch(patches, onSuccess, onFail) {
        if (!batchChannel || batchChannel.readyState !== batchChannel.OPEN) {
            const errorMsg = `Can't send batch session patch. No open batch channel. Patch: ${patches.op} ${patches.path}.`;
            callMany(errorMsg, onFail, console.error);
            return rejectedPromise(errorMsg);
        }
        if (jatos.batchSessionVersioning && isDeferredPending(sendingBatchSessionDeferred)) {
            const errorMsg = `Can send only one batch session patch at a time. Patch: ${patches.op} ${patches.path}.`;
            callMany(errorMsg, onFail, console.error);
            return rejectedPromise(errorMsg);
        }
        if (studyRunInvalid) {
            const errorMsg = `Can't send batch session patch. This study run is invalid. Patch: ${patches.op} ${patches.path}.`;
            callMany(errorMsg, onFail, console.warn);
            return rejectedPromise(errorMsg);
        }

        const deferred = createDeferred();
        if (jatos.batchSessionVersioning) sendingBatchSessionDeferred = deferred;

        const sessionActionId = batchSessionCounter++;
        const msgObj = {};
        msgObj.action = "SESSION";
        msgObj.id = sessionActionId;
        msgObj.patches = (patches.constructor === Array) ? patches : [patches];
        msgObj.version = batchSessionVersion;
        msgObj.versioning = !!jatos.batchSessionVersioning;
        try {
            batchChannel.send(JSON.stringify(msgObj));
            // Setup timeout: How long to wait for an answer from JATOS.
            setChannelSendingTimeoutAndPromiseResolution(deferred, batchSessionTimeouts,
                sessionActionId, onSuccess, onFail);
        } catch (error) {
            callMany(error, onFail, console.error);
            deferred.reject();
        }
        return deferred.promise();
    }

    /**
     * A web worker used in jatos.js to send periodic requests back to the
     * JATOS server. With this function one can set the period with which the
     * heartbeat is send.
     *
     * @param {number} heartbeatPeriod - in milliseconds (Integer)
     */
    jatos.setHeartbeatPeriod = function (heartbeatPeriod) {
        if (typeof heartbeatPeriod == 'number' && heartbeatWorker) {
            heartbeatWorker.postMessage([jatos.studyResultUuid, heartbeatPeriod]);
        }
    };

    /**
     * Defines callback function to be called if an patch for the batch session was received
     */
    jatos.onBatchSession = function (onBatchSession) {
        onJatosBatchSession = onBatchSession;
    };

    /**
     * DEPRECATED - Instead use the specific function's error callbacks or Promise functions
     *
     * Defines callback function to be called if jatos.js produces an error.
     */
    jatos.onError = function (onError) {
        console.warn("jatos.onError is abolished - use the specific function's error callback or Promise function");
    };

    installResultDataApi(jatos, {
        createDeferred,
        getURL,
        isInitialized: () => initialized,
        isInvalidComponentPosition: pos => isInvalidComponentPosition(jatos.componentList, pos),
        isStudyRunInvalid: () => studyRunInvalid,
        rejectedPromise,
        sendToHttpLoop: httpLoop.send
    });

    installStudyRunApi(jatos, {
        beforeUnloadWarning,
        getURL,
        httpLoop,
        isEndingStudy: () => endingStudy,
        isInitialized: () => initialized,
        isStartingComponent: () => startingComponent,
        isStudyRunInvalid: () => studyRunInvalid,
        setStartingComponent: value => { startingComponent = value; },
        setEndingStudy: value => { endingStudy = value; },
        rejectedPromise,
        stopStudyRun: () => {
            heartbeatWorker.terminate();
            httpLoop.terminate();
            clearInterval(batchChannelClosedCheckTimer);
            clearInterval(groupChannelClosedCheckTimer);
        }
    });

    /**
     * Tries to join a group (actually a GroupResult) in the JATOS server and if it
     * succeeds opens the group channel's WebSocket.
     *
     * @param {object} callbacks - Defining callback functions for group events. All
     *		callbacks are optional. These callbacks functions can be:
     *		onOpen: to be called when the group channel is successfully opened
     *		onClose: to be called when the group channel is closed
     *		onError(errorMsg): to be called if an error during opening of the group
     *			channel's WebSocket occurs or if an error is received via the
     *			group channel (e.g. the group session data couldn't be updated).
     *		onMessage(msg): to be called if a message from another group member is
     *			received. It gets the message as a parameter.
     *		onMemberJoin(memberId): to be called when another member (not the worker
     *			running this study) joined the group. It gets the group member ID as
     *			a parameter.
     *		onMemberOpen(memberId): to be called when another member (not the worker
     *			running this study) opened a group channel. It gets the group member
     *			ID as a parameter.
     *		onMemberLeave(memberId): to be called when another member (not the worker
     *			running his study) left the group. It gets the group member ID as
     *			a parameter.
     *		onMemberClose(memberId): to be called when another member (not the worker
     *			running this study) closed his group channel. It gets the group
     *			member ID as a parameter.
     *		onGroupSession(path): to be called when the group session is updated. It gets
     *			a JSON Pointer as a parameter that points to the changed object within
     *			the session.
     *		onUpdate(): Combines several other callbacks. It's called if one of the
     *			following is called: onMemberJoin, onMemberOpen, onMemberLeave,
     *			onMemberClose, or onGroupSession.
     * @return {JatosPromise}
     */
    jatos.joinGroup = function (callbacks) {
        groupChannelCallbacks = callbacks ? callbacks : {};
        // Try open only once - no retry like with batch channel or with openGroupChannelWithRetry
        // Any retry has to be implemented in the component's JS.
        return openGroupChannel();
    };

    function openGroupChannel() {
        if (!webSocketSupported) {
            const errorMsg = "This browser does not support WebSockets.";
            callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
            return rejectedPromise(errorMsg);
        }
        // WebSocket's readyState:
        //		CONNECTING 0 The connection is not yet open.
        //		OPEN       1 The connection is open and ready to communicate.
        //		CLOSING    2 The connection is in the process of closing.
        //		CLOSED     3 The connection is closed or couldn't be opened.
        if (groupChannel && groupChannel.readyState !== groupChannel.CLOSED) {
            return rejectedPromise("Can't open a WebSocket that is not in readyState CLOSED.");
        }
        if (endingStudy || startingComponent) {
            const errorMsg = "Won't open group channel because study is about to move to the next component or finish.";
            callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
            return rejectedPromise(errorMsg);
        }
        if (studyRunInvalid) {
            const errorMsg = "Can't open group channel. This study run is invalid.";
            callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
            return rejectedPromise(errorMsg);
        }
        if (isDeferredPending(openingGroupChannelDeferred)) {
            const errorMsg = "Can open only one group channel";
            callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
            return rejectedPromise(errorMsg);
        }
        if (isDeferredPending(leavingGroupDeferred)) {
            const errorMsg = "Can't open group channel while leaving a group";
            callMany(errorMsg, console.error, groupChannelCallbacks.onError);
            return rejectedPromise(errorMsg);
        }
        if (isDeferredPending(reassigningGroupDeferred)) {
            const errorMsg = "Can't open group channel while reassigning a group";
            callMany(errorMsg, console.error, groupChannelCallbacks.onError);
            return rejectedPromise(errorMsg);
        }

        openingGroupChannelDeferred = createDeferred();
        groupChannel = new WebSocket(
            ((window.location.protocol === "https:") ? "wss://" : "ws://") +
            window.location.host + jatos.urlBasePath + "publix/" + jatos.studyResultUuid + "/group/join");
        groupChannel.onopen = function () {
            groupChannel.send('{"action":"READY"}');
            groupChannelHeartbeat();
            groupChannelClosedCheck();
            // The actual group channel opening is done when we have the current
            // version of the group session
        };
        groupChannel.onmessage = function (event) {
            handleGroupMsg(event.data);
        };
        groupChannel.onerror = function () {
            callMany("Group channel error", console.error, groupChannelCallbacks.onError);
            openingGroupChannelDeferred.reject();
        };
        groupChannel.onclose = function () {
            clearGroupChannel();
            call(groupChannelCallbacks.onClose);
            openingGroupChannelDeferred.reject();
        };

        return openingGroupChannelDeferred.promise();
    }

    /**
     * Open group channel with retry and exponential backoff
     */
    function openGroupChannelWithRetry(backoffTime) {
        if (typeof backoffTime !== "number") backoffTime = jatos.channelOpeningBackoffTimeMin;
        openGroupChannel().fail(function () {
            if (backoffTime < jatos.channelOpeningBackoffTimeMax) backoffTime *= 2;
            setTimeout(function () {
                openGroupChannelWithRetry(backoffTime);
            }, backoffTime);
        });
    }

    /**
     * Closes the group channel, cleans channel objects and timers and reopens
     * the channel.
     */
    function reopenGroupChannel() {
        if (isDeferredPending(openingGroupChannelDeferred) ||
            isDeferredPending(reassigningGroupDeferred) ||
            isDeferredPending(leavingGroupDeferred)) {
            return;
        }
        if (groupChannel && groupChannel.readyState !== groupChannel.CLOSED) {
            groupChannel.close();
        }
        clearGroupChannel();
        openGroupChannelWithRetry();
    }

    /**
     * Periodically sends a heartbeat in the group channel. This is supposed
     * to keep the WebSocket open in routers. This heartbeat is additional
     * to the ping/pong heartbeat of the underlying WebSocket. For each
     * heartbeat ping we set a timeout until when the pong has to be received.
     * If no pong arrived the group channel will be closed and reopened.
     */
    function groupChannelHeartbeat() {
        clearInterval(groupChannelHeartbeatTimer);
        groupChannelHeartbeatTimer = setInterval(function () {
            if (groupChannel.readyState === groupChannel.OPEN) {
                groupChannel.send('{"heartbeat":"ping"}');
                const timeout = setTimeout(function () {
                    callMany("Group channel heartbeat fail", groupChannelCallbacks.onError, console.warn);
                    reopenGroupChannel();
                }, jatos.channelHeartbeatTimeoutTime);
                groupChannelHeartbeatTimeoutTimers.push(timeout);
            }
        }, jatos.channelHeartbeatInterval);
    }

    /**
     * Periodically checks whether the group channel is closed and if yes
     * reopens it. We don't rely on WebSocket's onClose callback (we could
     * just put reopenGroupChannel() in there) because it's not always called
     * and additionally sometimes called (unwanted) in case of a page
     * reload/closing.
     */
    function groupChannelClosedCheck() {
        clearInterval(groupChannelClosedCheckTimer);
        groupChannelClosedCheckTimer = setInterval(function () {
            if (groupChannel.readyState === groupChannel.CLOSED) {
                callMany("Group channel closed", console.info, groupChannelCallbacks.onError);
                clearInterval(groupChannelClosedCheckTimer);
                reopenGroupChannel();
            }
        }, jatos.channelClosedCheckInterval);
    }

    function clearGroupChannelHeartbeatTimeoutTimers() {
        groupChannelHeartbeatTimeoutTimers.forEach(function (timeout) {
            clearTimeout(timeout);
        });
        groupChannelHeartbeatTimeoutTimers = [];
    }

    function clearGroupChannel() {
        jatos.groupMemberId = null;
        jatos.groupResultId = null;
        jatos.groupMembers = [];
        jatos.groupChannels = [];
        groupSessionData = {};
        groupSessionVersion = null;
        groupState = null;
        clearGroupChannelHeartbeatTimeoutTimers();
        clearInterval(groupChannelHeartbeatTimer);
        // Don't clear groupChannelClosedCheckTimer here
    }

    /**
     * A group message from the JATOS server can be an action, a message from an
     * other group member, a heartbeat, or an error. An action usually comes
     * with the current group variables (members, channels, group session data
     * etc.). A group message from the JATOS server is always in JSON format.
     */
    function handleGroupMsg(msg) {
        let groupMsg;
        try {
            groupMsg = JSON.parse(msg);
        } catch (error) {
            callMany(error, groupChannelCallbacks.onError, console.error);
            return;
        }
        if (typeof groupMsg.heartbeat != 'undefined') {
            // Group channel is alive - clear all heartbeat timeouts
            clearGroupChannelHeartbeatTimeoutTimers();
            return;
        }
        updateGroupVars(groupMsg);
        // Now handle the action and map them to callbacks that were given as
        // parameter to joinGroup
        callGroupActionCallbacks(groupMsg);
        // Handle onMessage callback
        if (groupMsg.msg && groupChannelCallbacks.onMessage) {
            groupChannelCallbacks.onMessage(groupMsg.msg);
        }
    }

    /**
     * Update the group variables that usually come with an group action
     */
    function updateGroupVars(groupMsg) {
        if (typeof groupMsg.groupState != 'undefined') {
            groupState = groupMsg.groupState;
        }
        if (typeof groupMsg.groupResultId != 'undefined') {
            jatos.groupResultId = groupMsg.groupResultId.toString();
            showIdOverlay();
            // Group member ID is equal to study result ID
            jatos.groupMemberId = jatos.studyResultId;
        }
        // OPENED contains the complete member list. JOINED and LEFT are subsequent deltas.
        if (groupMsg.action === 'OPENED' && typeof groupMsg.members != 'undefined') {
            jatos.groupMembers = groupMsg.members;
        } else if (typeof groupMsg.memberId != 'undefined' && groupMsg.action === 'JOINED'
                && !jatos.groupMembers.includes(groupMsg.memberId)) {
            jatos.groupMembers.push(groupMsg.memberId);
        } else if (typeof groupMsg.memberId != 'undefined' && groupMsg.action === 'LEFT') {
            jatos.groupMembers = jatos.groupMembers.filter(function (memberId) {
                return memberId !== groupMsg.memberId;
            });
        }
        // The initial OPENED message contains the cluster-wide open channels.
        // CHANNEL_OPENED and CHANNEL_CLOSED are subsequent deltas.
        if (groupMsg.action === 'OPENED' && typeof groupMsg.channels != 'undefined') {
            jatos.groupChannels = groupMsg.channels;
        } else if (typeof groupMsg.memberId != 'undefined' && groupMsg.action === 'CHANNEL_OPENED'
                && !jatos.groupChannels.includes(groupMsg.memberId)) {
            jatos.groupChannels.push(groupMsg.memberId);
        } else if (typeof groupMsg.memberId != 'undefined'
                && (groupMsg.action === 'CHANNEL_CLOSED' || groupMsg.action === 'CLOSED')) {
            jatos.groupChannels = jatos.groupChannels.filter(function (memberId) {
                return memberId !== groupMsg.memberId;
            });
        }
        if (typeof groupMsg.sessionPatches != 'undefined') {
            const patchResults = jsonpatch.applyPatch(groupSessionData, groupMsg.sessionPatches);
            if (patchResults && patchResults.newDocument !== undefined) {
                groupSessionData = patchResults.newDocument;
            }
        }
        if (typeof groupMsg.sessionData != 'undefined') {
            if (groupMsg.sessionData === null) {
                groupSessionData = {};
            } else {
                groupSessionData = groupMsg.sessionData;
            }
        }
        if (typeof groupMsg.sessionVersion != 'undefined') {
            groupSessionVersion = groupMsg.sessionVersion;
            if (isDeferredPending(openingGroupChannelDeferred)) {
                // Group joining is only done after the session version is received
                console.info("Group channel opened");
                openingGroupChannelDeferred.resolve();
            }
        }
    }

    function callGroupActionCallbacks(groupMsg) {
        if (!groupMsg.action) {
            return;
        }
        switch (groupMsg.action) {
            case "OPENED":
                // This client's own group channel was initialized.
                callWithArgs(groupChannelCallbacks.onOpen, groupMsg.memberId);
                break;
            case "CLOSED":
                clearInterval(groupChannelClosedCheckTimer);
                console.info("Group channel closed by JATOS server");
                break;
            case "CHANNEL_OPENED":
                callWithArgs(groupChannelCallbacks.onMemberOpen, groupMsg.memberId);
                call(groupChannelCallbacks.onUpdate);
                break;
            case "CHANNEL_CLOSED":
                callWithArgs(groupChannelCallbacks.onMemberClose, groupMsg.memberId);
                call(groupChannelCallbacks.onUpdate);
                break;
            case "JOINED":
                // onMemberJoin
                // Some member joined (it should not happen, but check the group member ID
                // (aka study result ID) is not the one of the joined member)
                if (groupMsg.memberId !== jatos.groupMemberId) {
                    callWithArgs(groupChannelCallbacks.onMemberJoin, groupMsg.memberId);
                    call(groupChannelCallbacks.onUpdate);
                }
                break;
            case "LEFT":
                // onMemberLeave
                // Some member left (it should not happen, but check the group member ID
                // (aka study result ID) is not the one of the left member)
                if (groupMsg.memberId !== jatos.groupMemberId) {
                    callWithArgs(groupChannelCallbacks.onMemberLeave, groupMsg.memberId);
                    call(groupChannelCallbacks.onUpdate);
                }
                break;
            case "SESSION":
                // onGroupSession
                // Got updated group session data and version.
                // Call onGroupSession with JSON Patch's path
                // and op (operation) for each patch.
                groupMsg.sessionPatches.forEach(function (patch) {
                    callWithArgs(groupChannelCallbacks.onGroupSession, patch.path, patch.op);
                });
                call(groupChannelCallbacks.onUpdate);
                break;
            case "FIXED":
                // The group is now fixed (no new members)
                if (groupFixedTimeout) {
                    groupFixedTimeout.cancel();
                }
                call(groupChannelCallbacks.onUpdate);
                break;
            case "SESSION_ACK":
                if (groupSessionTimeouts.hasOwnProperty(groupMsg.sessionActionId)) {
                    groupSessionTimeouts[groupMsg.sessionActionId].cancel("Group session update successful");
                } else {
                    console.warn("Group session got 'SESSION_ACK' with nonexistent ID " + groupMsg.sessionActionId);
                }
                break;
            case "SESSION_FAIL":
                if (groupSessionTimeouts.hasOwnProperty(groupMsg.sessionActionId)) {
                    const errorMsg = groupMsg.errorMsg || "Group session update failed";
                    groupSessionTimeouts[groupMsg.sessionActionId].trigger(errorMsg);
                } else {
                    console.warn("Group session got 'SESSION_FAIL' with nonexistent ID " + groupMsg.sessionActionId);
                }
                break;
            case "ERROR":
                callMany(groupMsg.errorMsg, groupChannelCallbacks.onError, console.error);
                break;
        }
    }

    jatos.getGroupState = function () {
        return groupState;
    };

    jatos.isGroupFixed = function () {
        return groupState === "FIXED";
    };

    /**
     * Object contains all group session functions
     */
    jatos.groupSession = {};

    /**
     * Getter for a field in the group session data. Takes a name
     * and returns the matching value, or undefined if the name does not
     * correspond to an existing field. Works only on the first
     * level of the object tree. For all other levels use
     * jatos.groupSession.find. Gets the object from the
     * locally stored copy of the group session and does not call
     * the server.
     * @return {object}
     */
    jatos.groupSession.get = function (name) {
        const obj = jsonpatch.getValueByPointer(groupSessionData, "/" + name);
        return cloneJsonObj(obj);
    };

    /**
     * Returns the complete group session data (might be bad performance-wise)
     * Gets the object from the locally stored copy of the group session and
     * does not call the server.
     * @return {object}
     */
    jatos.groupSession.getAll = function () {
        const obj = jatos.groupSession.find("");
        return cloneJsonObj(obj);
    };

    /**
     * Getter for a field in the group session data. Takes a
     * JSON Pointer and returns the matching value, or undefined if the pointer
     * does not correspond to an existing field. Gets the object from the
     * locally stored copy of the group session and does not call the server.
     * @return {object}
     */
    jatos.groupSession.find = function (path) {
        const obj = jsonpatch.getValueByPointer(groupSessionData, path);
        return cloneJsonObj(obj);
    };

    /**
     * This function defines the JSON Patch test operation but it
     * does not use the 'test' operation of the JSON patch
     * implementation but uses the JSON pointer implementation
     * instead.
     * @param {string} path - JSON pointer path to be tested
     * @param {object} value - value to be tested
     * @return {boolean}
     */
    jatos.groupSession.test = function (path, value) {
        const obj = jsonpatch.getValueByPointer(groupSessionData, path);
        return obj === value;
    };

    /**
     * Check if the field under the given path is exists.
     * @param {string} path - JSON pointer path
     * @return {boolean}
     */
    jatos.groupSession.defined = function (path) {
        return !jatos.groupSession.test(path, undefined);
    };

    /**
     * JSON Patch add operation
     * @param {string} path - JSON pointer path
     * @param {object} value - value to be stored
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.groupSession.add = function (path, value, onSuccess, onFail) {
        const patch = generatePatch("add", path, value, null);
        return sendGroupSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * Like JSON Patch add operation, but instead of a path accepts
     * a name, thus works only on the first level of the object tree.
     * @param {string} name - name of the field
     * @param {object} value - value to be stored
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.groupSession.set = function (name, value, onSuccess, onFail) {
        const patch = generatePatch("add", "/" + name, value, null);
        return sendGroupSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * Replaces the whole session data (might be bad performance-wise)
     * @param {object} value - value to be stored in the session
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.groupSession.setAll = function (value, onSuccess, onFail) {
        return jatos.groupSession.replace("", value, onSuccess, onFail);
    };

    /**
     * JSON Patch remove operation
     * @param {string} path - JSON pointer path to the field that should be removed
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.groupSession.remove = function (path, onSuccess, onFail) {
        const patch = generatePatch("remove", path, null, null);
        return sendGroupSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * Clears the group session data.
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.groupSession.clear = function (onSuccess, onFail) {
        const patch = generatePatch("replace", "", {}, null);
        return sendGroupSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * JSON Patch replace operation
     * @param {string} path - JSON pointer path
     * @param {object} value - value to be replaced with
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.groupSession.replace = function (path, value, onSuccess, onFail) {
        const patch = generatePatch("replace", path, value, null);
        return sendGroupSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * JSON Patch copy operation
     * @param {string} from - JSON pointer path to the origin
     * @param {string} path - JSON pointer path to the target
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.groupSession.copy = function (from, path, onSuccess, onFail) {
        const patch = generatePatch("copy", path, null, from);
        return sendGroupSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * JSON Patch move operation
     * @param {string} from - JSON pointer path to the origin
     * @param {string} path - JSON pointer path to the target
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    jatos.groupSession.move = function (from, path, onSuccess, onFail) {
        const patch = generatePatch("move", path, null, from);
        return sendGroupSessionPatch(patch, onSuccess, onFail);
    };

    /**
     * Sends a JSON Patch via the group channel to JATOS and subsequently to all
     * other study currently running in this group. The parameter 'patches' can be a
     * a single patch object or an array of patch objects.
     */
    function sendGroupSessionPatch(patches, onSuccess, onFail) {
        if (!groupChannel || groupChannel.readyState !== groupChannel.OPEN) {
            const errorMsg = `Can't send group session patch. No open group channel. Patch: ${patches.op} ${patches.path}.`;
            callMany(errorMsg, onFail, console.error);
            return rejectedPromise(errorMsg);
        }
        if (jatos.groupSessionVersioning && isDeferredPending(sendingGroupSessionDeferred)) {
            const errorMsg = `Can send only one group session patch at a time. Patch: ${patches.op} ${patches.path}.`;
            callMany(errorMsg, onFail, console.error);
            return rejectedPromise(errorMsg);
        }
        if (studyRunInvalid) {
            const errorMsg = `Can't send group session patch. This study run is invalid. Patch: ${patches.op} ${patches.path}.`;
            callMany(errorMsg, onFail, console.warn);
            return rejectedPromise(errorMsg);
        }

        const deferred = createDeferred();
        if (jatos.groupSessionVersioning) sendingGroupSessionDeferred = deferred;

        const sessionActionId = groupSessionCounter++;
        const msgObj = {};
        msgObj.action = "SESSION";
        msgObj.sessionActionId = sessionActionId;
        msgObj.sessionPatches = (patches.constructor === Array) ? patches : [patches];
        msgObj.sessionVersion = groupSessionVersion;
        msgObj.sessionVersioning = !!jatos.groupSessionVersioning;
        try {
            groupChannel.send(JSON.stringify(msgObj));
            // Setup timeout: How long to wait for an answer from JATOS.
            setChannelSendingTimeoutAndPromiseResolution(deferred, groupSessionTimeouts,
                sessionActionId, onSuccess, onFail);
        } catch (error) {
            callMany(error, onFail, console.error);
            deferred.reject();
        }
        return deferred.promise();
    }

    /**
     * Ask the JATOS server to fix this group.
     * @param {Function} [onSuccess] - Called if the fixing was successful
     * @param {Function} [onFail] - Called if the fixing failed
     * @return {JatosPromise}
     */
    jatos.setGroupFixed = function (onSuccess, onFail) {
        if (!groupChannel || groupChannel.readyState !== groupChannel.OPEN) {
            const errorMsg = "Can't fix group. No open group channel.";
            callMany(errorMsg, onFail, console.error);
            return rejectedPromise(errorMsg);
        }
        if (isDeferredPending(sendingGroupFixedDeferred)) {
            const errorMsg = "Can fix group only once.";
            callMany(errorMsg, onFail, console.warn);
            return rejectedPromise(errorMsg);
        }
        if (studyRunInvalid) {
            const errorMsg = "Can't fix group. This study run is invalid.";
            callMany(errorMsg, onFail, console.warn);
            return rejectedPromise(errorMsg);
        }

        sendingGroupFixedDeferred = createDeferred();
        const msgObj = {};
        msgObj.action = "FIXED";
        try {
            groupChannel.send(JSON.stringify(msgObj));
            // Setup timeout: How long to wait for an answer from JATOS.
            setGroupFixedTimeoutAndPromiseResolution(sendingGroupFixedDeferred,
                onSuccess, onFail);
        } catch (error) {
            callMany(error, onFail, console.error);
            sendingGroupFixedDeferred.reject();
        }
        return sendingGroupFixedDeferred.promise();
    };

    function setGroupFixedTimeoutAndPromiseResolution(deferred, onSuccess, onFail) {
        const timeoutId = setTimeout(() => {
            callWithArgs(onFail, "Timeout sending message");
            deferred.reject("Timeout sending message");
        }, jatos.channelSendingTimeoutTime);

        // Create a new timeout object with a cancel function
        groupFixedTimeout = {
            cancel: () => {
                clearTimeout(timeoutId);
                callWithArgs(onSuccess, "success");
                deferred.resolve("success");
            }
        };

        // Always clean up and delete the timeout obj after the deferred is resolved
        deferred.always(() => { groupFixedTimeout = null });
    }

    /**
     * Returns true if this study run joined a group and false otherwise. It doesn't
     * necessarily mean that we have an open group channel. We can have joined a
     * group in a prior component. If you want to check for an open group channel
     * use jatos.hasOpenGroupChannel.
     */
    jatos.hasJoinedGroup = function () {
        return jatos.groupResultId !== null;
    };

    /**
     * Returns true if we currently have an open group channel and false otherwise.
     * Since you can't open a group channel without joining a group, it also means
     * that we joined a group.
     */
    jatos.hasOpenGroupChannel = function () {
        return groupChannel && groupChannel.readyState === groupChannel.OPEN;
    };

    /**
     * @return {boolean} True if the group has reached the maximum amount of active
     *         members like specified in the batch properties. It's not necessary
     *         that each member has an open group channel.
     */
    jatos.isMaxActiveMemberReached = function () {
        if (!jatos.batchProperties || jatos.batchProperties.maxActiveMembers === null) {
            return false;
        } else {
            return jatos.groupMembers.length >= jatos.batchProperties.maxActiveMembers;
        }
    };

    /**
     * @return {boolean} True if the group has reached the maximum amount of active
     *         members like specified in the batch properties and each member has an
     *         open group channel.
     */
    jatos.isMaxActiveMemberOpen = function () {
        if (!jatos.batchProperties || jatos.batchProperties.maxActiveMembers === null) {
            return false;
        } else {
            return jatos.groupChannels.length >= jatos.batchProperties.maxActiveMembers;
        }
    };

    /**
     * @return {boolean} True if all active members of the group have an open group
     *         channel. It's not necessary that the group has reached its minimum
     *         or maximum active member size.
     */
    jatos.isGroupOpen = function () {
        if (groupChannel && groupChannel.readyState === groupChannel.OPEN) {
            return jatos.groupMembers.length === jatos.groupChannels.length;
        } else {
            return false;
        }
    };

    /**
     * Sends a message to all group members if group channel is open.
     *
     * @param {object} msg - Any JavaScript object
     */
    jatos.sendGroupMsg = function (msg) {
        if (groupChannel && groupChannel.readyState === groupChannel.OPEN) {
            const msgObj = {};
            msgObj.msg = msg;
            groupChannel.send(JSON.stringify(msgObj));
        }
    };

    /**
     * Sends a message to a single group member specified with the given member ID
     * (only if group channel is open).
     *
     * @param {string} recipient - Recipient's group member ID
     * @param {object} msg - Any JavaScript object
     */
    jatos.sendGroupMsgTo = function (recipient, msg) {
        if (groupChannel && groupChannel.readyState === groupChannel.OPEN) {
            const msgObj = {};
            msgObj.recipient = recipient;
            msgObj.msg = msg;
            groupChannel.send(JSON.stringify(msgObj));
        }
    };

    /**
     * Asks the JATOS server to reassign this study run to a different group.
     * Successful reassigning reuses the current group channel (and WebSocket) -
     * it does not close the channel and opens a new one.
     *
     * @param {Function} [onSuccess] - Called if the reassignment was successful
     * @param {Function} [onFail] - Called if the reassignment was unsuccessful
     * @return {JatosPromise}
     */
    jatos.reassignGroup = function (onSuccess, onFail) {
        if (isDeferredPending(openingGroupChannelDeferred)) {
            const errorMsg = "Can't reassign a group if not joined yet.";
            callMany(errorMsg, console.error, onFail);
            return rejectedPromise(errorMsg);
        }
        if (isDeferredPending(leavingGroupDeferred)) {
            const errorMsg = "Can't reassign a group during leaving.";
            callMany(errorMsg, console.error, onFail);
            return rejectedPromise(errorMsg);
        }
        if (isDeferredPending(reassigningGroupDeferred)) {
            const errorMsg = "Can't reassign a group twice at the same time.";
            callMany(errorMsg, console.warn, onFail);
            return rejectedPromise(errorMsg);
        }
        if (!groupChannel || groupChannel.readyState !== groupChannel.OPEN) {
            const errorMsg = "Can't reassign group. Group channel not open.";
            callMany(errorMsg, console.error, onFail);
            return rejectedPromise(errorMsg);
        }
        if (studyRunInvalid) {
            const errorMsg = "Can't reassign group. This study run is invalid.";
            callMany(errorMsg, console.warn, onFail);
            return rejectedPromise(errorMsg);
        }

        reassigningGroupDeferred = createDeferred();
        jatos.jQuery.ajax({
            url: getURL("../group/reassign"),
            processData: false,
            type: "GET",
            timeout: jatos.httpTimeout,
            statusCode: {
                200: function () {
                    // Successful reassignment (keeps the same WebSocket)
                    call(onSuccess);
                    reassigningGroupDeferred.resolve();
                },
                204: function () {
                    // Unsuccessful reassignment
                    call(onFail);
                    reassigningGroupDeferred.reject();
                }
            },
            error: function (err) {
                const errMsg = getAjaxErrorMsg(err);
                callMany(errMsg, console.error, onFail);
                reassigningGroupDeferred.reject(errMsg);
            }
        });
        return reassigningGroupDeferred.promise();
    };

    /**
     * Tries to leave the group (actually a GroupResult) it has previously joined.
     * The group channel WebSocket is not closed in this function - it's closed from
     * the JATOS' side.
     *
     * @param {Function} [onSuccess] - Called after the group is left
     * @param {Function} [onError] - Called in case of error
     * @return {JatosPromise}
     */
    jatos.leaveGroup = function (onSuccess, onError) {
        if (isDeferredPending(openingGroupChannelDeferred)) {
            const errorMsg = "Can't leave group if not joined yet.";
            callMany(errorMsg, onError, console.error);
            return rejectedPromise(errorMsg);
        }
        if (isDeferredPending(reassigningGroupDeferred)) {
            const errorMsg = "Can't leave group during reassigning.";
            callMany(errorMsg, onError, console.error);
            return rejectedPromise(errorMsg);
        }
        if (isDeferredPending(leavingGroupDeferred)) {
            const errorMsg = "Can leave only once.";
            callMany(errorMsg, onError, console.warn);
            return rejectedPromise(errorMsg);
        }
        if (studyRunInvalid) {
            const errorMsg = "Can't leave group. This study run is invalid.";
            callMany(errorMsg, onError, console.warn);
            return rejectedPromise(errorMsg);
        }

        leavingGroupDeferred = createDeferred();
        jatos.jQuery.ajax({
            url: getURL("../group/leave"),
            processData: false,
            type: "GET",
            timeout: jatos.httpTimeout,
            success: function (response) {
                clearInterval(groupChannelClosedCheckTimer);
                callWithArgs(onSuccess, response);
                leavingGroupDeferred.resolve(response);
            },
            error: function (err) {
                var errMsg = getAjaxErrorMsg(err);
                callMany(errMsg, onError, console.error);
                leavingGroupDeferred.reject(errMsg);
            }
        }).retry({
            times: jatos.httpRetry,
            timeout: jatos.httpRetryWait
        });
        return leavingGroupDeferred.promise();
    };

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
        if (!initialized) return;

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

    /**
     * Sets a timeout and puts an object with two functions, 'cancel' and 'trigger'
     * into the given sessionTimeouts
     */
    function setChannelSendingTimeoutAndPromiseResolution(deferred, sessionTimeouts,
                                                          sessionActionId, onSuccess, onFail) {
        var timeoutId = setTimeout(function () {
            callWithArgs(onFail, "Timeout sending session patch");
            deferred.reject("Timeout sending session patch");
        }, jatos.channelSendingTimeoutTime);

        // Create a new timeout object with two functions: 1) to cancel
        // the timeout and 2) to trigger the timeout prematurely
        sessionTimeouts[sessionActionId] = {
            cancel: function (msg) {
                clearTimeout(timeoutId);
                callWithArgs(onSuccess, msg);
                deferred.resolve(msg);
            },
            trigger: function (msg) {
                clearTimeout(timeoutId);
                callWithArgs(onFail, msg);
                deferred.reject(msg);
            }
        };

        // Always clean up and delete the timeout obj after the deferred is resolved
        deferred.always(function () { delete sessionTimeouts[sessionActionId]; });
    }

})();
