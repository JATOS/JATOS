/** @typedef {import("./jatos-promise.js").JatosPromise} JatosPromise */

import {call, callMany, callWithArgs} from "./utils/callbacks.js";
import {createSessionApi, applySessionUpdate} from "./channel-session.js";
import {createDeferred, rejectedPromise, isDeferredPending} from "./jatos-promise.js";

/** Installs the batch and group APIs and owns their channel state. */
export function createChannels(jatos, dependencies) {
    const {
        requestHttp,
        studyRunState,
        getURL,
        getAjaxErrorMsg,
        showIdOverlay
    } = dependencies;

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
     * All batch/group session actions currently waiting for an response.
     * Maps sessionActionId -> timeout object
     */
    const batchSessionTimeouts = {};
    const groupSessionTimeouts = {};
    /**
     * Channel timeout and interval objects
     */
    let groupFixedTimeout;
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
     * Batch heartbeat timers. A missed heartbeat marks the connection dead and
     * reopens the batch channel.
     */
    const batchHeartbeat = createHeartbeat(() => batchChannel, handleBatchChannelHeartbeatFail);
    /**
     * Group heartbeat timers. A missed heartbeat notifies the group's error
     * callback and reopens the group channel.
     */
    const groupHeartbeat = createHeartbeat(() => groupChannel, () => {
        callMany("Group channel heartbeat fail", groupChannelCallbacks.onError, console.warn);
        reopenGroupChannel();
    });

    /**
     * WebSocket support by the browser is needed for group channel.
     */
    const webSocketSupported = 'WebSocket' in window;
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
     * Events fired when the batch channel heartbeat works or fails
     */
    const batchChannelAliveEvent = new Event("batchChannelAlive");
    const batchChannelDeadEvent = new Event("batchChannelDead");
    /**
     * Callback function defined via jatos.onBatchSession
     */
    let onJatosBatchSession;
    let batchChannelAlive = false;

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
        if (studyRunState.ending || studyRunState.starting) {
            const errorMsg = "Won't open batch channel because study is about to move to the next component or finish.";
            console.info(errorMsg);
            return rejectedPromise(errorMsg);
        }
        if (studyRunState.invalid) {
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
            batchHeartbeat.start();
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
     * Schedules application-level pings and tracks all unanswered ping timeouts.
     * Each channel owns an instance; failure handling stays channel-specific.
     */
    function createHeartbeat(getChannel, onFailure) {
        let interval;
        let timeouts = [];

        function start() {
            clearInterval(interval);
            interval = setInterval(() => {
                const channel = getChannel();
                if (channel.readyState === channel.OPEN) {
                    channel.send('{"heartbeat":"ping"}');
                    timeouts.push(setTimeout(onFailure, jatos.channelHeartbeatTimeoutTime));
                }
            }, jatos.channelHeartbeatInterval);
        }

        function acknowledge() {
            timeouts.forEach(timeout => clearTimeout(timeout));
            timeouts = [];
        }

        function stop() {
            acknowledge();
            clearInterval(interval);
        }

        return {start, acknowledge, stop};
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
        batchHeartbeat.stop();
        // Don't clear batchChannelClosedCheckTimer here
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
            batchHeartbeat.acknowledge();
            setBatchChannelAlive();
            return;
        }
        batchSessionData = applySessionUpdate(batchSessionData, batchMsg.patches, batchMsg.data);
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
                studyRunState.invalid = true;
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

    jatos.batchSession = createSessionApi(() => batchSessionData, sendBatchSessionPatch);

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
        if (studyRunState.invalid) {
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
     * Defines callback function to be called if an patch for the batch session was received
     */
    jatos.onBatchSession = function (onBatchSession) {
        onJatosBatchSession = onBatchSession;
    };

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
        if (studyRunState.ending || studyRunState.starting) {
            const errorMsg = "Won't open group channel because study is about to move to the next component or finish.";
            callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
            return rejectedPromise(errorMsg);
        }
        if (studyRunState.invalid) {
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
            groupHeartbeat.start();
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

    function clearGroupChannel() {
        jatos.groupMemberId = null;
        jatos.groupResultId = null;
        jatos.groupMembers = [];
        jatos.groupChannels = [];
        groupSessionData = {};
        groupSessionVersion = null;
        groupState = null;
        groupHeartbeat.stop();
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
            groupHeartbeat.acknowledge();
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
        groupSessionData = applySessionUpdate(groupSessionData, groupMsg.sessionPatches, groupMsg.sessionData);
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

    jatos.groupSession = createSessionApi(() => groupSessionData, sendGroupSessionPatch);

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
        if (studyRunState.invalid) {
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
        if (studyRunState.invalid) {
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
        if (studyRunState.invalid) {
            const errorMsg = "Can't reassign group. This study run is invalid.";
            callMany(errorMsg, console.warn, onFail);
            return rejectedPromise(errorMsg);
        }

        reassigningGroupDeferred = createDeferred();
        requestHttp({
            url: getURL("../group/reassign"),
            method: "GET",
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
        if (studyRunState.invalid) {
            const errorMsg = "Can't leave group. This study run is invalid.";
            callMany(errorMsg, onError, console.warn);
            return rejectedPromise(errorMsg);
        }

        leavingGroupDeferred = createDeferred();
        requestHttp({
            url: getURL("../group/leave"),
            retry: {times: jatos.httpRetry, timeout: jatos.httpRetryWait},
            method: "GET",
            timeout: jatos.httpTimeout,
            success: function (response) {
                clearInterval(groupChannelClosedCheckTimer);
                callWithArgs(onSuccess, response);
                leavingGroupDeferred.resolve(response);
            },
            error: function (err) {
                const errMsg = getAjaxErrorMsg(err);
                callMany(errMsg, onError, console.error);
                leavingGroupDeferred.reject(errMsg);
            }
        });
        return leavingGroupDeferred.promise();
    };

    /**
     * Sets a timeout and puts an object with two functions, 'cancel' and 'trigger'
     * into the given sessionTimeouts
     */
    function setChannelSendingTimeoutAndPromiseResolution(deferred, sessionTimeouts,
                                                          sessionActionId, onSuccess, onFail) {
        const timeoutId = setTimeout(function () {
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

    return {
        openBatchChannelWithRetry,
        // Preserve the existing end/abort cleanup: only stop closed-channel checks.
        stopClosedChecks: () => {
            clearInterval(batchChannelClosedCheckTimer);
            clearInterval(groupChannelClosedCheckTimer);
        }
    };
}
