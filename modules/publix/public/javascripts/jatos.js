/**
 * jatos.js (JATOS JavaScript Library)
 * http://www.jatos.org
 * Author Kristian Lange 2014 - 2022
 * Licensed under Apache License 2.0
 * 
 * Uses plugin jquery.ajax-retry:
 * https://github.com/johnkpaul/jquery-ajax-retry
 * Copyright (c) 2012 John Paul
 * Licensed under the MIT license.
 * 
 * Uses Starcounter-Jack/JSON-Patch:
 * https://github.com/Starcounter-Jack/JSON-Patch
 * Copyright (c) 2013, 2014 Joachim Wester
 * Licensed under the MIT license.
 * 
 * Uses jsonpointer.js:
 * https://github.com/alexeykuzmin/jsonpointer.js
 * Copyright (c) 2013 Alexey Kuzmin
 * Licensed under the MIT license.
 */

/* global jsonpatch, jsonpointer */

var jatos = {};

// Encapsulate the whole library so nothing unintentional gets out (e.g. jQuery
// or functions or variables)
(function () {
	"use strict";

	/**
	 * jatos.js version
	 */
	jatos.version = "3.7.3";
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
	 * The JSON data given to the study in the JATOS GUI
	 */
	jatos.studyJsonInput = {};
	/**
	 * Number of component this study has
	 */
	jatos.studyLength = null;
	/**
	 * All the properties (except studyJsonInput) belonging to the study
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
	 * The JSON data given to the component in the JATOS GUI
	 */
	jatos.componentJsonInput = {};
	/**
	 * Position of this component in this study (starts with 1)
	 */
	jatos.componentPos = null;
	/**
	 * All the properties (except componentJsonInput) belonging to the component
	 */
	jatos.componentProperties = {};
	/**
	 * All properties of the batch
	 */
	jatos.batchProperties = {};
	/**
	 * The JSON data given to the batch in the JATOS GUI
	 */
	jatos.batchJsonInput = {};
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
	 * Group session data: shared in between members of the group
	 */
	var groupSessionData = {};
	/**
	 * Batch session data: shared in between study runs of the same batch 
	 */
	var batchSessionData = {};
	/**
	 * How long in ms should jatos.js wait for an answer after message was sent via
	 * a group or batch channel.
	 */
	jatos.channelSendingTimeoutTime = 10000;
	/**
	 * Waiting time in ms between channel heartbeats
	 */
	jatos.channelHeartbeatInterval = 25000;
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
	jatos.waitSendDataOverlayConfig = { text: "Sending data. Please wait." };
	/**
	 * All batch/group session actions currently waiting for an response.
	 * Maps sessionActionId -> timeout object
	 */
	var batchSessionTimeouts = {};
	var groupSessionTimeouts = {};
	/**
	 * Channel timeout and interval objects
	 */
	var groupFixedTimeout;
	var batchChannelHeartbeatTimer;
	var groupChannelHeartbeatTimer;
	var batchChannelHeartbeatTimeoutTimers = [];
	var groupChannelHeartbeatTimeoutTimers = [];
	var batchChannelClosedCheckTimer;
	var groupChannelClosedCheckTimer;
	/**
	 * Version of the current group/batch session data. The version is
	 * used to prevent concurrent changes of the data. Can be switch on/off
	 * by flags *SessionVersioning.
	 */
	var batchSessionVersion;
	var groupSessionVersion;
	/**
	 * Number of batch/group session updates so far. Used to generate the
	 * sessionActionId.
	 */
	var batchSessionCounter = 0;
	var groupSessionCounter = 0;
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
	var batchChannel;
	/**
	 * Group channel WebSocket to exchange messages between workers of a group.
	 * Not to be confused with 'jatos.groupChannels'. Accessible only by jatos.js.
	 */
	var groupChannel;
	/**
	 * Object with group channel callbacks (details in jatos.joinGroup)
	 */
	var groupChannelCallbacks;
	/**
	 * WebSocket support by the browser is needed for group channel.
	 */
	var webSocketSupported = 'WebSocket' in window;
	/**
	 * Web worker initialized in initJatos() that sends a periodic Ajax request
	 * back to the JATOS server. Don't confuse with channel heartbeats.
	 */
	var heartbeatWorker;
	/**
	 * Web worker initialized in initJatos() that handles sending of result data,
	 * result files, study session data, and log messages.
	 */
	var httpLoop;
	/**
	 * Number of requests handled by the httpLoop worker so far.
	 */
	var httpLoopCounter = 0;
	/**
	 * All requests currently handled by the httpLoop worker are in here.
	 * Map of request IDs to jQuery.deferred objects.
	 */
	var waitingRequests = {};
	/**
	 * State booleans. If true jatos.js is in this state. Several states can be true
	 * at the same time.
	 */
	var initialized = false;
	var jatosOnLoadEventFired = false;
	var startingComponent = false;
	var endingStudy = false;
	/**
	 * jQuery.Deferred objects: can hold state pending, resolved, or rejected
	 */
	var openingBatchChannelDeferred;
	var sendingBatchSessionDeferred;
	var openingGroupChannelDeferred;
	var sendingGroupSessionDeferred;
	var sendingGroupFixedDeferred;
	var reassigningGroupDeferred;
	var leavingGroupDeferred;
	var httpLoopDeferred;
	/**
	 * Event fired when jatos.js is initialized (e.g. init data loaded and channels opened)
	 */
	var jatosOnLoadEvent = new Event("jatosOnLoad");
	/**
	 * Callback function defined via jatos.onBatchSession
	 */
	var onJatosBatchSession;
	/**
	 * Callback function if jatos.js produces an error, defined via jatos.onError.
	 */
	var onJatosError;
	/**
	 * Flag that determines if the 'beforeunload' warning should be shown
	 * by the browser to the worker if they attempt to reload or close the
	 * broser (tab)
	 */
	var showBeforeUnloadWarning = true;

	// Load jatos.js's jQuery and put it in jatos.jQuery to avoid conflicts with
	// a component's jQuery version. Afterwards call initJatos.
	jatos.jQuery = {};
	getScript('jatos-publix/javascripts/jquery-3.5.1.min.js', function () {
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
		var script = document.createElement('script');
		script.src = url;
		var head = document.getElementsByTagName('head')[0],
			done = false;
		script.onload = script.onreadystatechange = function () {
			if (!done && (!this.readyState || this.readyState == 'loaded' ||
				this.readyState == 'complete')) {
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
			jatos.jQuery.getScript("jatos-publix/javascripts/json-patch-duplex.min.js"),
			// Load JSON Pointer library https://github.com/alexeykuzmin/jsonpointer.js
			jatos.jQuery.getScript("jatos-publix/javascripts/jsonpointer.min.js")
		)
		.then(function () {
			// Get studyResultUuid from URL path
			jatos.studyResultUuid = window.location.pathname.split("/").reverse()[2];
			readIdCookie();
			// Start heartbeat.js (the general one - not the channel one)
            heartbeatWorker = new Worker("jatos-publix/javascripts/heartbeat.js");
			heartbeatWorker.postMessage([jatos.studyResultUuid]);
			// Start httpLoop.js
            httpLoop = new Worker("jatos-publix/javascripts/httpLoop.js");
			httpLoop.addEventListener('message', function (msg) { httpLoopListener(msg.data); }, false);
		})
		.then(getInitData)
		.then(openBatchChannelWithRetry)
		.always(function () {
			initialized = true;
			readyForOnLoad();
		});
	}

	/**
	 * Sends the given request to the httpLoop.js background worker.
	 */
	function sendToHttpLoop(request, onSuccess, onError) {
		if (!initialized) {
			console.error("jatos.js not yet initialized");
			return jatos.jQuery.Deferred().reject();
		}

		var deferred = jatos.jQuery.Deferred();
		deferred.done(function () {
			callFunctionIfExist(onSuccess);
		});
		deferred.fail(function (err) {
			callingOnError(onError, err);
		});

		var requestId = httpLoopCounter++;
		request.id = requestId;
		waitingRequests[request.id] = deferred;
		if (!isDeferredPending(httpLoopDeferred)) {
			httpLoopDeferred = jatos.jQuery.Deferred();
		}
		httpLoop.postMessage(request);

		return deferred;
	}

	/**
	 * Handles messages from the httpLoop.js background worker. Each message
	 * corresponds to a request send earlier to the worker.
	 */
	function httpLoopListener(msg) {
		// Handle request's deferred
		var deferred = waitingRequests[msg.requestId];
		delete waitingRequests[msg.requestId];
		if (msg.status == 200) {
			deferred.resolve();
		} else {
			var errMsg = [msg.status, msg.statusText, msg.error]
				.filter(function (s) { return s; }).join(", ");
			deferred.reject(msg.method + " to " + msg.url + " failed: " + errMsg);
		}

		// Handle httpLoop's deferred (are all requests done?)
		if (Object.keys(waitingRequests).length === 0 && isDeferredPending(httpLoopDeferred)) {
			httpLoopDeferred.resolve();
		}
	}

	/**
	 * Reads JATOS' ID cookies, finds the right one (same studyResultId)
	 * and stores all key-value pairs into jatos scope.
	 */
	function readIdCookie() {
		var idCookieName = "JATOS_IDS";
		var cookieArray = document.cookie.split(';');
		var fillJatos = function (key, value) {
			jatos[key] = value;
		};
		for (var i = 0; i < cookieArray.length; i++) {
			var cookie = cookieArray[i];
			// Remove leading spaces in cookie string
			while (cookie.charAt(0) === ' ') {
				cookie = cookie.substring(1, cookie.length);
			}
			if (cookie.indexOf(idCookieName) !== 0) {
				continue;
			}
			var cookieStr = cookie.substr(
				cookie.indexOf(idCookieName) + idCookieName.length + 3,
				cookie.length);
			var idArray = cookieStr.split("&");
			var idMap = getIdsFromCookie(idArray);
			if (idMap.studyResultUuid == jatos.studyResultUuid) {
				jatos.jQuery.each(idMap, fillJatos);
				// Convert component's position to int
				jatos.componentPos = parseInt(jatos.componentPos);
				break;
			}
		}
	}

	function getIdsFromCookie(idArray) {
		var idMap = {};
		idArray.forEach(function (entry) {
			var keyValuePair = entry.split("=");
			var value = decodeURIComponent(keyValuePair[1]);
			idMap[keyValuePair[0]] = value;
		});
		return idMap;
	}

	/**
	 * Gets the study's session data, the study's properties, and the
	 * component's properties from the JATOS server and stores them in
	 * jatos.studySessionData, jatos.studyProperties, and
	 * jatos.componentProperties. Additionally it stores study's JsonInput
	 * into jatos.studyJsonInput and component's JsonInput into
	 * jatos.componentJsonInput.
	 */
	function getInitData() {
		return jatos.jQuery.ajax({
			url: getURL("initData"),
			type: "GET",
			dataType: 'json',
			timeout: jatos.httpTimeout,
			success: setInitData,
			error: function (err) {
				callingOnError(null, getAjaxErrorMsg(err));
			}
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
		if (typeof jatos.batchProperties.jsonData != 'undefined') {
			jatos.batchJsonInput = jatos.jQuery
				.parseJSON(jatos.batchProperties.jsonData);
		} else {
			jatos.batchJsonInput = {};
		}
		delete jatos.batchProperties.jsonData;

		// Study session data
		try {
			jatos.studySessionData = JSON.parse(initData.studySessionData);
		} catch (e) {
			callingOnError(null, e.stack || e);
		}

		// Study properties
		jatos.studyProperties = initData.studyProperties;
		if (typeof jatos.studyProperties.jsonData != 'undefined' &&
			jatos.studyProperties.jsonData !== null) {
			jatos.studyJsonInput = jatos.jQuery
				.parseJSON(jatos.studyProperties.jsonData);
		} else {
			jatos.studyJsonInput = {};
		}
		delete jatos.studyProperties.jsonData;

		// Study's component list and study length
		jatos.componentList = initData.componentList;
		jatos.studyLength = initData.componentList.length;

		// Component properties
		jatos.componentProperties = initData.componentProperties;
		if (typeof jatos.componentProperties.jsonData != 'undefined' &&
			jatos.componentProperties.jsonData !== null) {
			jatos.componentJsonInput = jatos.jQuery
				.parseJSON(jatos.componentProperties.jsonData);
		} else {
			jatos.componentJsonInput = {};
		}
		delete jatos.componentProperties.jsonData;

		// Query string parameters of the URL that starts the study
		jatos.urlQueryParameters = initData.urlQueryParameters;

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
			callingOnError(null, "This browser does not support WebSockets. Can't open batch channel.");
			return rejectedPromise();
		}
		// WebSocket's readyState:
		//		CONNECTING 0 The connection is not yet open.
		//		OPEN       1 The connection is open and ready to communicate.
		//		CLOSING    2 The connection is in the process of closing.
		//		CLOSED     3 The connection is closed or couldn't be opened.
		if (batchChannel && batchChannel.readyState != batchChannel.CLOSED) {
			return rejectedPromise();
		}
		if (isDeferredPending(openingBatchChannelDeferred)) {
			callingOnError(null, "Can open only one batch channel.");
			return rejectedPromise();
		}
		openingBatchChannelDeferred = jatos.jQuery.Deferred();

		batchChannel = new WebSocket(
			((window.location.protocol === "https:") ? "wss://" : "ws://") +
			window.location.host + jatos.urlBasePath + "publix/" + jatos.studyResultUuid + "/batch/open");
		batchChannel.onopen = function () {
			batchChannelHeartbeat();
			batchChannelClosedCheck();
			// The actual batch channel opening is done when we have the 
			// current version of the batch session
		};
		batchChannel.onmessage = function (event) {
			handleBatchMsg(event.data);
		};
		batchChannel.onerror = function () {
			callingOnError(null, "Batch channel error");
			openingBatchChannelDeferred.reject();
		};
		// Some browsers call it with leaving/reloading the page
		// Called with closing the WebSocket intentionally 
		// Called with network error, after ws.onerror
		batchChannel.onclose = function () {
			clearBatchChannel();
			openingBatchChannelDeferred.reject();
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
			if (batchChannel.readyState == batchChannel.OPEN) {
				batchChannel.send('{"heartbeat":"ping"}');
				var timeout = setTimeout(function () {
					callingOnError(null, "Batch channel heartbeat fail");
					reopenBatchChannel();
				}, jatos.channelHeartbeatTimeoutTime);
				batchChannelHeartbeatTimeoutTimers.push(timeout);
			}
		}, jatos.channelHeartbeatInterval);
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
			if (batchChannel.readyState == batchChannel.CLOSED) {
				callingOnError(null, "Batch channel closed unexpectedly");
				clearInterval(batchChannelClosedCheckTimer);
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
		var batchMsg;
		try {
			batchMsg = JSON.parse(msg);
		} catch (error) {
			callingOnError(null, error);
			return;
		}
		if (typeof batchMsg.heartbeat != 'undefined') {
			// Batch channel is alive - clear all heartbeat timeouts
			clearBatchChannelHeartbeatTimeoutTimers();
			return;
		}
		if (typeof batchMsg.patches != 'undefined') {
			// Add to JSON-Patch for "remove" and "/" - clear all session data
			// Assumes the 'remove' operation is in the first JSON patch
			if (batchMsg.patches[0].op == "remove" &&
				batchMsg.patches[0].path == "/") {
				batchSessionData = {};
			} else {
				jsonpatch.apply(batchSessionData, batchMsg.patches);
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
			// Batch channel opening is only done when we have the batch session version
			openingBatchChannelDeferred.resolve();
		} else {
			callingOnError(null, "Batch channel opening failed: version missing");
			openingBatchChannelDeferred.reject();
		}
		if (typeof batchMsg.action != 'undefined') {
			handleBatchAction(batchMsg);
		}
	}

	/**
	 * Handels a batch action message received via the batch channel
	 */
	function handleBatchAction(batchMsg) {
		switch (batchMsg.action) {
			case "SESSION":
				// Call onJatosBatchSession with JSON Patch's path and 
				// op (operation) for each patch
				batchMsg.patches.forEach(function (patch) {
					callFunctionIfExist(onJatosBatchSession, patch.path, patch.op);
				});
				break;
			case "SESSION_ACK":
				if (batchSessionTimeouts.hasOwnProperty(batchMsg.id)) {
					batchSessionTimeouts[batchMsg.id].cancel();
				} else {
					callingOnError(null, "Batch session got 'SESSION_ACK' " +
						"with nonexistent ID " + batchMsg.id);
				}
				break;
			case "SESSION_FAIL":
				if (batchSessionTimeouts.hasOwnProperty(batchMsg.id)) {
					batchSessionTimeouts[batchMsg.id].trigger();
				} else {
					callingOnError(null, "Batch session got 'SESSION_FAIL' " +
						"with nonexistent ID " + batchMsg.id);
				}
				break;
			case "ERROR":
				callingOnError(null, batchMsg.errorMsg);
				break;
		}
	}

	/**
	 * Object contains all batch session functions
	 */
	jatos.batchSession = {};

	/**
	 * Getter for a field in the batch session data. Takes a name
	 * and returns the matching value. Works only on the first
	 * level of the object tree. For all other levels use
	 * jatos.batchSession.find. Gets the object from the
	 * locally stored copy of the session and does not call
	 * the server.
	 * @param {string} name - name of the field 
	 * @return {object}
	 */
	jatos.batchSession.get = function (name) {
		var obj = jsonpointer.get(batchSessionData, "/" + name);
		return cloneJsonObj(obj);
	};

	/**
	 * Returns the complete batch session data (might be bad performance-wise)
	 * Gets the object from the locally stored copy of the session
	 * and does not call the server.
	 * @return {object}
	 */
	jatos.batchSession.getAll = function () {
		var obj = jatos.batchSession.find("");
		return cloneJsonObj(obj);
	};

	/**	
	 * Getter for a field in the batch session data. Takes a
	 * JSON Pointer and returns the matching value. Gets the
	 * object from the locally stored copy of the session
	 * and does not call the server.
	 * @param {string} path - JSON pointer path
	 * @return {object}
	 */
	jatos.batchSession.find = function (path) {
		var obj = jsonpointer.get(batchSessionData, path);
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
		var obj = jsonpointer.get(batchSessionData, path);
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
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.batchSession.add = function (path, value, onSuccess, onFail) {
		var patch = generatePatch("add", path, value, null);
		return sendBatchSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * Like JSON Patch add operation, but instead of a path accepts
	 * a name of the field to be stored. Works only on the first level
	 * of the object tree.
	 * @param {string} name - name of the field 
	 * @param {object} value - value to be stored
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.batchSession.set = function (name, value, onSuccess, onFail) {
		var patch = generatePatch("add", "/" + name, value, null);
		return sendBatchSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * Replaces the whole session data (might be bad performance-wise)
	 * @param {object} value - value to be stored in the session
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.batchSession.setAll = function (value, onSuccess, onFail) {
		return jatos.batchSession.replace("", value, onSuccess, onFail);
	};

	/**
	 * JSON Patch remove operation
	 * @param {string} path - JSON pointer path to the field that should
	 *             be removed
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.batchSession.remove = function (path, onSuccess, onFail) {
		var patch = generatePatch("remove", path, null, null);
		return sendBatchSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * Clears the batch session data.
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.batchSession.clear = function (onSuccess, onFail) {
		var patch = generatePatch("remove", "/", null, null);
		return sendBatchSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * JSON Patch replace operation
	 * @param {string} path - JSON pointer path 
	 * @param {object} value - value to be replaced with
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.batchSession.replace = function (path, value, onSuccess, onFail) {
		var patch = generatePatch("replace", path, value, null);
		return sendBatchSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * JSON Patch copy operation
	 * @param {string} from - JSON pointer path to the origin 
	 * @param {string} path - JSON pointer path to the target
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.batchSession.copy = function (from, path, onSuccess, onFail) {
		var patch = generatePatch("copy", path, null, from);
		return sendBatchSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * JSON Patch move operation
	 * @param {string} from - JSON pointer path to the origin 
	 * @param {string} path - JSON pointer path to the target
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.batchSession.move = function (from, path, onSuccess, onFail) {
		var patch = generatePatch("move", path, null, from);
		return sendBatchSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * Generates an abstract JSON Patch
	 */
	function generatePatch(op, path, value, from) {
		var patch = {};
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
		if (!batchChannel || batchChannel.readyState != batchChannel.OPEN) {
			callingOnError(onFail, "No open batch channel");
			return rejectedPromise();
		}
		if (jatos.batchSessionVersioning && isDeferredPending(sendingBatchSessionDeferred)) {
			callingOnError(onFail, "Can send only one batch session patch at a time");
			return rejectedPromise();
		}

		var deferred = jatos.jQuery.Deferred();
		if (jatos.batchSessionVersioning) sendingBatchSessionDeferred = deferred;

		var sessionActionId = batchSessionCounter++;
		var msgObj = {};
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
			callingOnError(onFail, error);
			deferred.reject();
		}
		return deferred.promise();
	}

	/**
	 * A web worker used in jatos.js to send periodic Ajax requests back to the
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
	 * Defines callback function to be called if jatos.js produces an error, e.g. Ajax errors.
	 */
	jatos.onError = function (onError) {
		console.warn("jatos.onError is deprecated - use the specific function's error callback or Promise function");
		onJatosError = onError;
	};

	/**
	 * Posts result data for the currently running component back to the JATOS
	 * server. Already stored result data for this component will be overwritten.
	 * It offers callbacks, either as parameter or via Promise,
	 * to signal success or failure in the transfer.
	 * 
	 * @param {object} resultData - String or object to be submitted
	 * @param {optional function} onSuccess - Function to be called in case of success
	 * @param {optional function} onError - Function to be called in case of error
	 * @return {Promise}
	 */
	jatos.submitResultData = function (resultData, onSuccess, onError) {
		return submitOrAppendResultData(resultData, false, onSuccess, onError);
	};

	/**
	 * Appends result data for the currently running component back to the JATOS
	 * server. Contrary to jatos.submitResultData it does not overwrite the result
	 * data. It offers callbacks, either as parameter or via Promise,
	 * to signal success or failure in the transfer.
	 *
	 * @param {object or string} resultData - String or object to be appended
	 * @param {optional function} onSuccess - Function to be called in case of success
	 * @param {optional function} onError - Function to be called in case of error
	 * @return {Promise}
	 */
	jatos.appendResultData = function (resultData, onSuccess, onError) {
		return submitOrAppendResultData(resultData, true, onSuccess, onError);
	};

	/**
	 * Does the sending of the result data. Uses PUT for submitResultData and
	 * POST for appendResultData.
	 */
	function submitOrAppendResultData(resultData, append, onSuccess, onError) {
		var httpMethod = append ? "POST" : "PUT";
		if (resultData === Object(resultData)) {
			resultData = JSON.stringify(resultData);
		}
		var request = {
			url: getURL("resultData"),
			data: resultData,
			method: httpMethod,
			contentType: "text/plain; charset=UTF-8",
			timeout: jatos.httpTimeout,
			retry: jatos.httpRetry,
			retryWait: jatos.httpRetryWait
		};
		return sendToHttpLoop(request, onSuccess, onError).promise();
	}

	/**
	 * Uploads a file that will be saved on the JATOS server. 
	 *
	 * @param {Blob, string or object} obj - Data to be uploaded as a file. A Blob
	 * 										will be uploaded right away. A string
	 * 										is turned into a Blob. An object is
	 * 										first turned into a JSON string	andl
	 * 										then into a Blob.
	 * @param {string} filename - Name of the uploaded file
	 * @param {optional function} onSuccess - Function to be called in case of success
	 * @param {optional function} onError - Function to be called in case of error
	 * @return {Promise}
	 */
	jatos.uploadResultFile = function (obj, filename, onSuccess, onError) {
		if (typeof filename !== "string" || 0 === filename.length) {
			callingOnError(onError, "No filename specified");
			return rejectedPromise();
		}

		var blob;
		if (obj instanceof Blob) {
			blob = obj;
		} else if (typeof obj === "string") {
			blob = new Blob([obj], { type: 'text/plain' });
		} else if (obj === Object(obj)) {
			// Object can be stringified to JSON
			blob = new Blob([JSON.stringify(obj, null, 2)], { type: 'application/json' });
		} else {
			callingOnError(onError, "Only string, Object or Blob allowed");
			return rejectedPromise();
		}

		var request = {
			url: getURL("files/" + encodeURI(filename)),
			blob: blob,
			filename: filename,
			method: 'POST',
			timeout: jatos.httpTimeout,
			retry: jatos.httpRetry,
			retryWait: jatos.httpRetryWait
		};
		return sendToHttpLoop(request, onSuccess, onError).promise();
	};

	/**
	 * Downloads a file from the JATOS server. Can only download a file that was previously
	 * uploaded with jatos.uploadResultFile in the same study run. If the file contains
	 * text it returns the content as a string. If the file contains JSON, it returns
	 * the JSON already parsed as an object. All other mime types are returned as a Blob.
	 *
	 * @param {string} filename - Name of the uploaded file
	 * @param {optional function} onSuccess - Function to be called in case of success
	 * @param {optional function} onError - Function to be called in case of error
	 * @return {Promise}
	 * 
	 * Additionally one can specify the component ID (in case different components uploaded
	 * files with the same filename):
	 * @param {number} componentPos - Position of the component to look for the file
	 * @param {string} filename - Name of the uploaded file
	 * @param {optional function} onSuccess - Function to be called in case of success
	 * @param {optional function} onError - Function to be called in case of error
	 * @return {Promise}
	 */
	jatos.downloadResultFile = function (param1, param2, param3, param4) {
		if (!initialized) {
			console.error("jatos.js not yet initialized");
			return rejectedPromise();
		}

		var componentPos, filename, onSuccess, onError;
		if (typeof param1 === 'number') {
			componentPos = param1;
			filename = param2;
			onSuccess = param3;
			onError = param4;
		} else if (typeof param1 === 'string') {
			filename = param1;
			onSuccess = param2;
			onError = param3;
		} else {
			callingOnError(onError, "Unknown first parameter");
			return rejectedPromise();
		}
		if (typeof filename !== "string" || 0 === filename.length) {
			callingOnError(onError, "No filename specified");
			return rejectedPromise();
		}

		var url = getURL("../files/" + encodeURI(filename));
		if (componentPos) {
			if (isInvalidComponentPosition(componentPos)) {
				callingOnError(onError, "Component position does not exist");
				return rejectedPromise();
			}
			var componentId = jatos.componentList[componentPos - 1].id;
			url += "?componentId=" + componentId;
		}

		var deferred = jatos.jQuery.Deferred();
		// Use XMLHttpRequest instead of jQuery because jQuery cannot handle JSON within a Blob
		var xhr = new XMLHttpRequest();
		xhr.open("GET", url, true);
		xhr.responseType = "blob";
		xhr.onload = function () {
			if (this.status == 200) {
				var blob = xhr.response;
				if (blob.type == "application/json") {
					var jsonReader = new FileReader();
					jsonReader.addEventListener("loadend", function () {
						var obj = JSON.parse(jsonReader.result);
						callFunctionIfExist(onSuccess, obj);
						deferred.resolve(obj);
					});
					jsonReader.readAsText(blob);
				} else if (blob.type == "text/plain") {
					var textReader = new FileReader();
					textReader.addEventListener("loadend", function () {
						var text = textReader.result;
						callFunctionIfExist(onSuccess, text);
						deferred.resolve(text);
					});
					textReader.readAsText(blob);
				} else {
					callFunctionIfExist(onSuccess, blob);
					deferred.resolve(blob);
				}
			} else {
				xhr.onerror();
			}
		};
		xhr.onerror = function () {
			var error = "Download of " + filename + " returned " + xhr.statusText;
			callingOnError(onError, error);
			deferred.reject(error);
		};
		xhr.send(null);

		return deferred.promise();
	};

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
	 * @param {optional function} onSuccess - Function to be called after this
	 *				function is finished
	 * @param {optional function} onFail - Callback if fail
	 * @return {Promise}
	 */
	jatos.setStudySessionData = function (studySessionData, onSuccess, onFail) {
		jatos.studySessionData = studySessionData;
		var studySessionDataStr = JSON.stringify(studySessionData);
		var request = {
			url: getURL("../studySessionData"),
			data: studySessionDataStr,
			method: "POST",
			contentType: "text/plain; charset=UTF-8",
			timeout: jatos.httpTimeout,
			retry: jatos.httpRetry,
			retryWait: jatos.httpRetryWait
		};
		return sendToHttpLoop(request, onSuccess, onFail).promise();
	};

	/**
	 * Starts the component with the given ID. Before it calls
	 * jatos.appendResultData (sends result data to the JATOS server and 
	 * appends them to the already existing ones for this component) and 
	 * jatos.setStudySessionData (syncs study session data with the JATOS server).
	 * 
	 * Either without message:
	 * @param {string|number} componentIdOrUuid - ID or UUID of the component to start
	 * @param {optional object or string} resultData - Result data to be sent back to JATOS
	 * @param {optional function} onError - Callback function if fail
	 * 
	 * Or with message:
	 * @param {string|number} componentIdOrUuid - ID or UUID of the component to start
	 * @param {optional object or String} resultData - Result data to be sent back to JATOS
	 * @param {optional string} message - Message that should be logged (max 255 chars)
	 * @param {optional function} onError - Callback function if fail
	 */
	jatos.startComponent = function (componentIdOrUuid, resultData, param3, param4) {
		if (!initialized) {
			console.error("jatos.js not yet initialized");
			return;
		}

		var message, onError, componentUuid;
		if (typeof componentIdOrUuid === 'number') {
			componentUuid = jatos.componentList.find(c => c.id == componentIdOrUuid).uuid;
		} else {
			componentUuid = componentIdOrUuid;
		}
		if (typeof param3 === 'string') {
			message = param3;
			onError = param4;
		} else if (typeof param3 === 'function') {
			onError = param3;
		}

		if (startingComponent) {
			callingOnError(onError, "Can start only one component at the same time");
			return;
		}
		startingComponent = true;

		// Send result data and study session data before starting next component
		if (resultData) jatos.appendResultData(resultData);
		jatos.setStudySessionData(jatos.studySessionData);

		var start = function () {
			window.removeEventListener('beforeunload', beforeUnloadWarning, { capture: true });

			var url = getURL("../" + componentUuid + "/start");
			if (message) url = url + "?" + jatos.jQuery.param({ "message": message });
			window.location.href = url;
		};

		// Wait for httpLoop.js to finish
		if (isDeferredPending(httpLoopDeferred)) {
			setTimeout(jatos.showOverlay, 1000, jatos.waitSendDataOverlayConfig);
			httpLoopDeferred.always(start);
		} else {
			start();
		}
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
	 * @param {optional object or string} resultData - Result data to be sent back to JATOS
	 * @param {optional function} onError - Callback function if fail
	 * 
	 * Or with message:
	 * @param {number} componentPos - Position of the component to start
	 * @param {optional object or String} resultData - Result data to be sent back to JATOS
	 * @param {optional string} message - Message that should be logged (max 255 chars)
	 * @param {optional function} onError - Callback function if fail
	 */
	jatos.startComponentByPos = function (componentPos, resultData, param3, param4) {
		if (isInvalidComponentPosition(componentPos)) {
			var onError;
			if (typeof param3 === 'function') onError = param3;
			else if (typeof param4 === 'function') onError = param4;
			callingOnError(onError, "Component position does not exist");
			return;
		}
		var componentUuid = jatos.componentList[componentPos - 1].uuid;
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
	 * @param {optional object or string} resultData - Result data to be sent back to JATOS
	 * @param {optional function} onError - Callback function if fail
	 * 
	 * Or with message:
	 * @param {optional object or string} resultData - Result data to be sent back to JATOS
	 * @param {optional string} message - Message that should be logged (max 255 chars)
	 * @param {optional function} onError - Callback function if fail
	 */
	jatos.startNextComponent = function (resultData, param2, param3) {
		var message, onError;
		if (typeof param2 === 'string') {
			message = param2;
			onError = param3;
		} else {
			onError = param2;
		}

		// If last component end study
		var lastActiveComponent = jatos.componentList.slice().reverse()
			.find(function (component) { return component.active; });
		if (jatos.componentPos >= lastActiveComponent.position) {
			if (resultData) {
				var onComplete = function () {
					jatos.endStudy(true, message);
				};
				jatos.appendResultData(resultData).done(onComplete).fail(onError);
			} else {
				jatos.endStudy(true, message);
			}
			return;
		}
		// Start next active component
		for (var i = jatos.componentPos; i < jatos.componentList.length; i++) {
			if (jatos.componentList[i].active) {
				var nextComponentUuid = jatos.componentList[i].uuid;
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
	 * @param {optional object or string} resultData - Result data be sent back
	 * @param {optional function} onError - Callback function if fail
	 * 
	 * Or with message:
	 * @param {optional object or string} resultData - Result data be sent back
	 * @param {optional string} message - Message that should be logged (max 255 chars)
	 * @param {optional function} onError - Callback function if fail
	 */
	jatos.startLastComponent = function (resultData, param2, param3) {
		var lastActiveComponent = jatos.componentList.reverse().find(c => c.active);
		jatos.startComponent(lastActiveComponent.uuid, resultData, param2, param3);
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
	 *			group channel (e.g. the group session data couldn't be updated). If
	 *			this function is not defined jatos.js will try to call the global
	 *			onJatosError function.
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
	 * @return {Promise}
	 */
	jatos.joinGroup = function (callbacks) {
		groupChannelCallbacks = callbacks ? callbacks : {};
		// Try open only once - no retry like with batch channel or with openGroupChannelWithRetry
		// Any retry has to be implemented in the component's JS.
		return openGroupChannel();
	};

	function openGroupChannel() {
		if (!webSocketSupported) {
			callingOnError(groupChannelCallbacks.onError,
				"This browser does not support WebSockets.");
			return rejectedPromise();
		}
		// WebSocket's readyState:
		//		CONNECTING 0 The connection is not yet open.
		//		OPEN       1 The connection is open and ready to communicate.
		//		CLOSING    2 The connection is in the process of closing.
		//		CLOSED     3 The connection is closed or couldn't be opened.
		if (groupChannel && groupChannel.readyState != groupChannel.CLOSED) {
			return rejectedPromise();
		}
		if (isDeferredPending(openingGroupChannelDeferred)) {
			callingOnError(groupChannelCallbacks.onError, "Can open group channel only once");
			return rejectedPromise();
		}
		if (isDeferredPending(leavingGroupDeferred)) {
			callingOnError(groupChannelCallbacks.onError, "Can't open group channel while leaving a group");
			return rejectedPromise();
		}
		if (isDeferredPending(reassigningGroupDeferred)) {
			callingOnError(groupChannelCallbacks.onError, "Can't open group channel while reassigning a group");
			return rejectedPromise();
		}

		openingGroupChannelDeferred = jatos.jQuery.Deferred();
		groupChannel = new WebSocket(
			((window.location.protocol === "https:") ? "wss://" : "ws://") +
			window.location.host + jatos.urlBasePath + "publix/" + jatos.studyResultUuid + "/group/join");
		groupChannel.onopen = function () {
			groupChannelHeartbeat();
			groupChannelClosedCheck();
			// The actual group channel opening is done when we have the current
			// version of the group session
		};
		groupChannel.onmessage = function (event) {
			handleGroupMsg(event.data);
		};
		groupChannel.onerror = function () {
			callingOnError(groupChannelCallbacks.onError, "Group channel error");
			openingGroupChannelDeferred.reject();
		};
		groupChannel.onclose = function () {
			clearGroupChannel();
			callFunctionIfExist(groupChannelCallbacks.onClose);
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
		if (groupChannel && groupChannel.readyState != groupChannel.CLOSED) {
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
			if (groupChannel.readyState == groupChannel.OPEN) {
				groupChannel.send('{"heartbeat":"ping"}');
				var timeout = setTimeout(function () {
					callingOnError(groupChannelCallbacks.onError,
						"Group channel heartbeat fail");
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
			if (groupChannel.readyState == groupChannel.CLOSED) {
				callingOnError(groupChannelCallbacks.onError,
					"Group channel closed unexpectedly");
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
		var groupMsg;
		try {
			groupMsg = JSON.parse(msg);
		} catch (error) {
			callingOnError(groupChannelCallbacks.onError, error);
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
		if (typeof groupMsg.groupResultId != 'undefined') {
			jatos.groupResultId = groupMsg.groupResultId.toString();
			// Group member ID is equal to study result ID
			jatos.groupMemberId = jatos.studyResultId;
		}
		if (typeof groupMsg.members != 'undefined') {
			jatos.groupMembers = groupMsg.members;
		}
		if (typeof groupMsg.channels != 'undefined') {
			jatos.groupChannels = groupMsg.channels;
		}
		if (typeof groupMsg.sessionPatches != 'undefined') {
			// Add to JSON-Patch for "remove" and "/" - clear all session data
			// Assumes the 'remove' operation is in the first JSON patch
			if (groupMsg.sessionPatches[0].op == "remove" &&
				groupMsg.sessionPatches[0].path == "/") {
				groupSessionData = {};
			} else {
				jsonpatch.apply(groupSessionData, groupMsg.sessionPatches);
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
			// Group joining is only done after the session version is received
			openingGroupChannelDeferred.resolve();
		} else {
			callingOnError(null, "Group channel opening failed: version missing");
			openingGroupChannelDeferred.reject();
		}
	}

	function callGroupActionCallbacks(groupMsg) {
		if (!groupMsg.action) {
			return;
		}
		switch (groupMsg.action) {
			case "OPENED":
				// onOpen and onMemberOpen
				// Someone opened a group channel; distinguish between the worker running
				// this study and others
				if (groupMsg.memberId == jatos.groupMemberId) {
					callFunctionIfExist(groupChannelCallbacks.onOpen, groupMsg.memberId);
				} else {
					callFunctionIfExist(groupChannelCallbacks.onMemberOpen, groupMsg.memberId);
					callFunctionIfExist(groupChannelCallbacks.onUpdate);
				}
				break;
			case "CLOSED":
				// onMemberClose
				// Some member closed its group channel
				// (onClose callback function is handled during groupChannel.onclose)
				if (groupMsg.memberId != jatos.groupMemberId) {
					callFunctionIfExist(groupChannelCallbacks.onMemberClose, groupMsg.memberId);
					callFunctionIfExist(groupChannelCallbacks.onUpdate);
				}
				break;
			case "JOINED":
				// onMemberJoin
				// Some member joined (it should not happen, but check the group member ID
				// (aka study result ID) is not the one of the joined member)
				if (groupMsg.memberId != jatos.groupMemberId) {
					callFunctionIfExist(groupChannelCallbacks.onMemberJoin, groupMsg.memberId);
					callFunctionIfExist(groupChannelCallbacks.onUpdate);
				}
				break;
			case "LEFT":
				// onMemberLeave
				// Some member left (it should not happen, but check the group member ID
				// (aka study result ID) is not the one of the left member)
				if (groupMsg.memberId != jatos.groupMemberId) {
					callFunctionIfExist(groupChannelCallbacks.onMemberLeave, groupMsg.memberId);
					callFunctionIfExist(groupChannelCallbacks.onUpdate);
				}
				break;
			case "SESSION":
				// onGroupSession
				// Got updated group session data and version.
				// Call onGroupSession with JSON Patch's path 
				// and op (operation) for each patch.
				groupMsg.sessionPatches.forEach(function (patch) {
					callFunctionIfExist(groupChannelCallbacks.onGroupSession, patch.path, patch.op);
				});
				callFunctionIfExist(groupChannelCallbacks.onUpdate);
				break;
			case "FIXED":
				// The group is now fixed (no new members)
				if (groupFixedTimeout) {
					groupFixedTimeout.cancel();
				}
				callFunctionIfExist(groupChannelCallbacks.onUpdate);
				break;
			case "SESSION_ACK":
				if (groupSessionTimeouts.hasOwnProperty(groupMsg.sessionActionId)) {
					groupSessionTimeouts[groupMsg.sessionActionId].cancel();
				} else {
					callingOnError(null, "Group session got 'SESSION_ACK' " +
						"with nonexistent ID " + groupMsg.sessionActionId);
				}
				break;
			case "SESSION_FAIL":
				if (groupSessionTimeouts.hasOwnProperty(groupMsg.sessionActionId)) {
					groupSessionTimeouts[groupMsg.sessionActionId].trigger();
				} else {
					callingOnError(null, "Group session got 'SESSION_FAIL' " +
						"with nonexistent ID " + groupMsg.sessionActionId);
				}
				break;
			case "ERROR":
				// onError or jatos.onError
				// Got an error
				callingOnError(groupChannelCallbacks.onError, groupMsg.errorMsg);
				break;
		}
	}

	/**
	 * Object contains all group session functions
	 */
	jatos.groupSession = {};

	/**
	 * Getter for a field in the group session data. Takes a name
	 * and returns the matching value. Works only on the first
	 * level of the object tree. For all other levels use
	 * jatos.groupSession.find. Gets the object from the
	 * locally stored copy of the group session and does not call
	 * the server.
	 * @return {object}
	 */
	jatos.groupSession.get = function (name) {
		var obj = jsonpointer.get(groupSessionData, "/" + name);
		return cloneJsonObj(obj);
	};

	/**
	 * Returns the complete group session data (might be bad performance-wise)
	 * Gets the object from the locally stored copy of the group session and
	 * does not call the server.
	 * @return {object}
	 */
	jatos.groupSession.getAll = function () {
		var obj = jatos.groupSession.find("");
		return cloneJsonObj(obj);
	};

	/**
	 * Getter for a field in the group session data. Takes a
	 * JSON Pointer and returns the matching value. Gets the object from the
	 * locally stored copy of the group session and does not call the server.
	 * @return {object}
	 */
	jatos.groupSession.find = function (path) {
		var obj = jsonpointer.get(groupSessionData, path);
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
		var obj = jsonpointer.get(groupSessionData, path);
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
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.groupSession.add = function (path, value, onSuccess, onFail) {
		var patch = generatePatch("add", path, value, null);
		return sendGroupSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * Like JSON Patch add operation, but instead of a path accepts
	 * a name, thus works only on the first level of the object tree.
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.groupSession.set = function (name, value, onSuccess, onFail) {
		var patch = generatePatch("add", "/" + name, value, null);
		return sendGroupSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * Replaces the whole session data (might be bad performance-wise)
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.groupSession.setAll = function (value, onSuccess, onFail) {
		return jatos.groupSession.replace("", value, onSuccess, onFail);
	};

	/**
	 * JSON Patch remove operation
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.groupSession.remove = function (path, onSuccess, onFail) {
		var patch = generatePatch("remove", path, null, null);
		return sendGroupSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * Clears the group session data.
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.groupSession.clear = function (onSuccess, onFail) {
		var patch = generatePatch("remove", "/", null, null);
		return sendGroupSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * JSON Patch replace operation
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.groupSession.replace = function (path, value, onSuccess, onFail) {
		var patch = generatePatch("replace", path, value, null);
		return sendGroupSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * JSON Patch copy operation
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.groupSession.copy = function (from, path, onSuccess, onFail) {
		var patch = generatePatch("copy", path, null, from);
		return sendGroupSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * JSON Patch move operation
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {Promise}
	 */
	jatos.groupSession.move = function (from, path, onSuccess, onFail) {
		var patch = generatePatch("move", path, null, from);
		return sendGroupSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * Sends a JSON Patch via the group channel to JATOS and subsequently to all
	 * other study currently running in this group. The parameter 'patches' can be a
	 * a single patch object or an array of patch objects.
	 */
	function sendGroupSessionPatch(patches, onSuccess, onFail) {
		if (!groupChannel || groupChannel.readyState != groupChannel.OPEN) {
			callingOnError(onFail, "No open group channel");
			return rejectedPromise();
		}
		if (jatos.groupSessionVersioning && isDeferredPending(sendingGroupSessionDeferred)) {
			callingOnError(onFail, "Can send only one group session patch at a time");
			return rejectedPromise();
		}

		var deferred = jatos.jQuery.Deferred();
		if (jatos.groupSessionVersioning) sendingGroupSessionDeferred = deferred;

		var sessionActionId = groupSessionCounter++;
		var msgObj = {};
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
			callingOnError(onFail, error);
			deferred.reject();
		}
		return deferred.promise();
	}

	/**
	 * Ask the JATOS server to fix this group.
	 * @param {optional callback} onSuccess - Function to be called if
	 *             the fixing was successful
	 * @param {optional callback} onFail - Function to be called if
	 *             the fixing failed
	 * @return {Promise}
	 */
	jatos.setGroupFixed = function (onSuccess, onFail) {
		if (!groupChannel || groupChannel.readyState != groupChannel.OPEN) {
			callingOnError(onFail, "No open group channel");
			return rejectedPromise();
		}
		if (isDeferredPending(sendingGroupFixedDeferred)) {
			callingOnError(onFail, "Can fix group only once");
			return rejectedPromise();
		}

		sendingGroupFixedDeferred = jatos.jQuery.Deferred();
		var msgObj = {};
		msgObj.action = "FIXED";
		try {
			groupChannel.send(JSON.stringify(msgObj));
			// Setup timeout: How long to wait for an answer from JATOS.
			setGroupFixedTimeoutAndPromiseResolution(sendingGroupFixedDeferred,
				onSuccess, onFail);
		} catch (error) {
			callingOnError(onFail, error);
			sendingGroupFixedDeferred.reject();
		}
		return sendingGroupFixedDeferred.promise();
	};

	function setGroupFixedTimeoutAndPromiseResolution(deferred, onSuccess, onFail) {
		var timeoutId = setTimeout(() => {
			callFunctionIfExist(onFail, "Timeout sending message");
			deferred.reject("Timeout sending message");
		}, jatos.channelSendingTimeoutTime);

		// Create a new timeout object with a cancel function
		groupFixedTimeout = {
			cancel: () => {
				clearTimeout(timeoutId);
				callFunctionIfExist(onSuccess, "success");
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
		return groupChannel && groupChannel.readyState == groupChannel.OPEN;
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
		if (groupChannel && groupChannel.readyState == groupChannel.OPEN) {
			return jatos.groupMembers.length == jatos.groupChannels.length;
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
		if (groupChannel && groupChannel.readyState == groupChannel.OPEN) {
			var msgObj = {};
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
		if (groupChannel && groupChannel.readyState == groupChannel.OPEN) {
			var msgObj = {};
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
	 * @param {optional function} onSuccess - Function to be called if the
	 *            reassignment was successful
	 * @param {optional function} onFail - Function to be called if the
	 *            reassignment was unsuccessful.
	 * @return {Promise}
	 */
	jatos.reassignGroup = function (onSuccess, onFail) {
		if (isDeferredPending(openingGroupChannelDeferred)) {
			callingOnError(onFail, "Can't reassign group if not joined yet");
			return rejectedPromise();
		}
		if (isDeferredPending(leavingGroupDeferred)) {
			callingOnError(onFail, "Can't reassign group during leaving");
			return rejectedPromise();
		}
		if (isDeferredPending(reassigningGroupDeferred)) {
			callingOnError(onFail, "Can't reassign group twice at the same time");
			return rejectedPromise();
		}
		if (groupChannel && groupChannel.readyState != groupChannel.OPEN) {
			callingOnError(onFail, "Group channel not open");
			return rejectedPromise();
		}

		reassigningGroupDeferred = jatos.jQuery.Deferred();
		jatos.jQuery.ajax({
			url: getURL("../group/reassign"),
			processData: false,
			type: "GET",
			timeout: jatos.httpTimeout,
			statusCode: {
				200: function () {
					// Successful reassignment (keeps the same WebSocket)
					callFunctionIfExist(onSuccess);
					reassigningGroupDeferred.resolve();
				},
				204: function () {
					// Unsuccessful reassignment
					callFunctionIfExist(onFail);
					reassigningGroupDeferred.reject();
				}
			},
			error: function (err) {
				var errMsg = getAjaxErrorMsg(err);
				callingOnError(onFail, getAjaxErrorMsg(err));
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
	 * @param {optional function} onSuccess - Function to be called after the group
	 *            is left.
	 * @param {optional function} onError - Function to be called in case of error.
	 * @return {Promise}
	 */
	jatos.leaveGroup = function (onSuccess, onError) {
		if (isDeferredPending(openingGroupChannelDeferred)) {
			callingOnError(onError, "Can't leave group if not joined yet");
			return rejectedPromise();
		}
		if (isDeferredPending(reassigningGroupDeferred)) {
			callingOnError(onError, "Can't leave group during reassigning");
			return rejectedPromise();
		}
		if (isDeferredPending(leavingGroupDeferred)) {
			callingOnError(onError, "Can leave only once");
			return rejectedPromise();
		}

		leavingGroupDeferred = jatos.jQuery.Deferred();
		jatos.jQuery.ajax({
			url: getURL("../group/leave"),
			processData: false,
			type: "GET",
			timeout: jatos.httpTimeout,
			success: function (response) {
				clearInterval(groupChannelClosedCheckTimer);
				callFunctionIfExist(onSuccess, response);
				leavingGroupDeferred.resolve(response);
			},
			error: function (err) {
				var errMsg = getAjaxErrorMsg(err);
				callingOnError(onError, getAjaxErrorMsg(err));
				leavingGroupDeferred.reject(errMsg);
			}
		}).retry({
			times: jatos.httpRetry,
			timeout: jatos.httpRetryWait
		});
		return leavingGroupDeferred.promise();
	};

	/**
	 * Aborts study. All previously submitted data will be deleted.
	 * 
	 * @param {optional string} message - Message that should be logged
	 * @param {optional function} onSuccess - Function to be called in case of
	 *				successful submit
	 * @param {optional function} onError - Function to be called in case of error
	 * @return {Promise}
	 */
	jatos.abortStudyAjax = function (message, onSuccess, onError) {
		if (!initialized) {
			console.error("jatos.js not yet initialized");
			return rejectedPromise();
		}
		if (endingStudy) {
			callingOnError(onError, "Can end/abort study only once");
			return rejectedPromise();
		}
		endingStudy = true;

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
		var deferred = sendToHttpLoop(request, onSuccess, onError);
		setTimeout(function () {
			if (isDeferredPending(httpLoopDeferred) && isDeferredPending(deferred)) {
				jatos.showOverlay(jatos.waitSendDataOverlayConfig);
			}
		}, 1000);
		deferred.done(function () {
			window.removeEventListener('beforeunload', beforeUnloadWarning, { capture: true });
			heartbeatWorker.terminate();
			httpLoop.terminate();
			clearInterval(batchChannelClosedCheckTimer);
			clearInterval(groupChannelClosedCheckTimer);
		});
		deferred.always(jatos.removeOverlay);

		return deferred.promise();
	};

	/**
	 * Aborts study. All previously submitted data will be deleted.
	 * 
	 * @param {optional string} message - Message that should be logged
	 * @param {optional boolean} showEndPage - If true an end page is shown - if false it
	 *				behaves like jatos.abortStudyAjax
	 */
	jatos.abortStudy = function (message, showEndPage) {
		if (typeof showEndPage !== "undefined" && !showEndPage) {
			return jatos.abortStudyAjax(message);
		}

		if (endingStudy) {
			callingOnError(null, "Can end/abort study only once");
			return;
		}
		endingStudy = true;

		function abort() {
			window.removeEventListener('beforeunload', beforeUnloadWarning, { capture: true });

			var url = getURL("../abort");
			if (typeof message == 'undefined') {
				window.location.href = url;
			} else {
				window.location.href = url + "?message=" + message;
			}
		}

		// Wait for httpLoop.js to finish
		if (isDeferredPending(httpLoopDeferred)) {
			setTimeout(jatos.showOverlay, 1000, jatos.waitSendDataOverlayConfig);
			httpLoopDeferred.always(abort);
		} else {
			abort();
		}
	};

	/**
	 * Ends study with an Ajax call.
	 * 
	 * Either without result data:
	 * @param {optional boolean} successful - 'true' if study should finish
	 *				successful and the participant should get the confirmation
	 *				code - 'false' otherwise.
	 * @param {optional string} message - Message to be logged (max 255 chars)
	 * @param {optional function} onSuccess - Function to be called in case of
	 *				successful submit
	 * @param {optional function} onError - Function to be called in case of error
	 * 
	 * Or with result data:
	 * @param {optional string or object} resultData- result data to be sent back
	 * 				to JATOS server
	 * @param {optional boolean} successful - 'true' if study should finish
	 *				successful and the participant should get the confirmation
	 *				code - 'false' otherwise
	 * @param {optional string} message - Message to be logged (max 255 chars)
	 * @param {optional function} onSuccess - Function to be called in case of
	 *				successful submit
	 * @param {optional function} onError - Function to be called in case of error
	 * 
	 * @return {Promise}
	 */
	jatos.endStudyAjax = function (param1, param2, param3, param4, param5) {
		if (!initialized) {
			console.error("jatos.js not yet initialized");
			return rejectedPromise();
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

		if (endingStudy) {
			callingOnError(onError, "Can end/abort study only once");
			return rejectedPromise();
		}
		endingStudy = true;
		
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
		var deferred = sendToHttpLoop(request, onSuccess, onError);
		setTimeout(function () {
			if (isDeferredPending(httpLoopDeferred) && isDeferredPending(deferred)) {
				jatos.showOverlay(jatos.waitSendDataOverlayConfig);
			}
		}, 1000);
		deferred.done(function () {
			window.removeEventListener('beforeunload', beforeUnloadWarning, { capture: true });
			heartbeatWorker.terminate();
			httpLoop.terminate();
			clearInterval(batchChannelClosedCheckTimer);
			clearInterval(groupChannelClosedCheckTimer);
		});
		deferred.always(jatos.removeOverlay);

		return deferred.promise();
	};

	/**
	 * Ends study and redirects to another URL. It's a convenience function / wrapper
	 * arround jatos.endStudyAjax. The first parameter is the URL and the other up to
	 * 5 parameters are the same as in jatos.endStudyAjax.
	 */
	jatos.endStudyAndRedirect = function (url, param1, param2, param3, param4, param5) {
		jatos.endStudyAjax(param1, param2, param3, param4, param5).done(function () {
			window.location.href = url;
		});
	};

	/**
	 * Ends study.
	 * 
	 * Either without result data:
	 * @param {optional boolean} successful - 'true' if study should finish
	 *			successful and the participant should get the confirmation code
	 *			- 'false' otherwise
	 * @param {optional string} message - Message to be logged (max 255 chars)
	 * @param {optional boolean} showEndPage - If true an end page is shown - if false it
	 *			behaves like jatos.endStudyAjax
	 *
	 * Or with result data:
	 * @param {optional string or object} resultData- result data to be sent back
	 * 				to JATOS server
	 * @param {optional boolean} successful - 'true' if study should finish
	 *			successful and the participant should get the confirmation code
	 *			- 'false' otherwise
	 * @param {optional string} message - Message to be logged (max 255 chars)
	 * @param {optional boolean} showEndPage - If true an end page is shown - if false it
	 *			behaves like jatos.endStudyAjax
	 */
	jatos.endStudy = function (param1, param2, param3, param4) {
		if (!initialized) {
			callingOnError(null, "jatos.js not yet initialized");
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
				return jatos.endStudyAjax(resultData, successful, message);
			} else {
				return jatos.endStudyAjax(successful, message);
			}
		}

		if (endingStudy) {
			callingOnError(null, "Can end/abort study only once");
			return;
		}
		endingStudy = true;

		// Before finish send result data
		if (resultData) jatos.appendResultData(resultData);

		function end() {
			window.removeEventListener('beforeunload', beforeUnloadWarning, { capture: true });

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
		if (isDeferredPending(httpLoopDeferred)) {
			setTimeout(jatos.showOverlay, 1000, jatos.waitSendDataOverlayConfig);
			httpLoopDeferred.always(end);
		} else {
			end();
		}
	};

	/**
	 * Returns the URL with protocol, host and port to the given path
	 */
	function getURL(path) {
		return new URL(path, window.location.href).toString();
	}

	jatos.getHttpLoopCounter = function () {
		return httpLoopCounter;
	};

	/**
	 * DEPRECATED - Use jatos.log instead
	 *
	 * Logs a message within the JATOS log on the server side.
	 */
	jatos.logError = function (logErrorMsg) {
		console.warn("jatos.logError is deprecated - use jatos.log instead");
		jatos.log(logErrorMsg);
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
		sendToHttpLoop(request);
	};

	/**
	 * Convenience function that sends all 'error' and 'unhandledrejection'
	 * events and console.error and console.warn calls to JATOS server log
	 */
	jatos.catchAndLogErrors = function () {
		window.addEventListener('error', function (e) {
			jatos.log("Via 'error' event in " + e.filename + ":" +
				e.lineno + " - " + e.message);
		});
		window.addEventListener('unhandledrejection', function (e) {
			jatos.log("Via 'unhandledrejection' event in " + e.filename + ":" +
				e.lineno + " - " + e.message);
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
	 * @param {object} obj - Object to which the IDs will be added
	 */
	jatos.addJatosIds = function (obj) {
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
	 * image is an spinning wheel.
	 *
	 * @param {object optional} config - Config object
	 * @param {boolean optional} config.show - If true the overlay is shown - otherwise not.
	 * 										   Default is true.
	 * @param {string optional} config.text - Text to be shown. Default is "Please wait".
	 * @param {string optional} config.imgUrl - URL of the image. Default is a spinning wheel.
	 * @param {string optional} config.showImg - If true the image is shown - otherwise not.
	 * 										     Default is true.
	 * @param {string optional} config.style - Additional CSS styles
	 */
	jatos.showOverlay = function (config) {
		if (config && typeof config.show == "boolean" && !config.show) return;

		// Create div
		var divStyle = 'color: black;' +
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
		var div = document.createElement('div');
		div.id = "jatosOverlay";
		div.style.cssText = divStyle;

		// Add Text
		var text = (config && typeof config.text == "string") ? config.text : "Please wait";
		var textElement = document.createTextNode(text);
		div.appendChild(textElement);

		// Add image
		var showImg = (config && typeof config.showImg == "boolean") ? config.showImg : true;
		if (showImg) {
			var imgUrl = (config && typeof config.imgUrl == "string") ? config.imgUrl
				: "jatos-publix/images/waiting.gif";
			var waitingImg = document.createElement('img');
			waitingImg.src = imgUrl;
			waitingImg.style.marginTop = "10px";
			div.appendChild(waitingImg);
		}

		jatos.removeOverlay(); // remove old overlay
		document.body.appendChild(div);
	};

	/**
	 * Removes the overlay that was added by jatos.showOverlay before
	 */
	jatos.removeOverlay = function () {
		var el = document.getElementById("jatosOverlay");
		if (el) el.remove();
	};

	/**
	 * Adds a button to the document that if pressed calls jatos.abortStudy.
	 * By default this button is in the bottom-right corner but this and
	 * other properties can be configured.
	 * 
	 * @param {object optional} config - Config object
	 * @param {string optional} config.text - Button text
	 * @param {boolean optional} config.confirm - Should the worker be asked for confirmation?
	 * 											  Default true.
	 * @param {string optional} config.confirmText - Confirmation text
	 * @param {string optional} config.tooltip - Tooltip text
	 * @param {string optional} config.msg - Message to be send back to JATOS to be logged
	 * @param {string optional} config.style - Additional CSS styles
	 * @param {function optional} config.action - Which function should be called in the end
	 * 											  Default is jatos.abortStudy
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
	 * Calls the function f it f exists with parameters a and b. 
	 */
	function callFunctionIfExist(f, a, b) {
		if (f && typeof f == 'function') {
			f(a, b);
		}
	}

	/**
	 * Takes a jQuery Ajax response and returns an error message.
	 */
	function getAjaxErrorMsg(jqxhr) {
		if (jqxhr.statusText == 'timeout') {
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
	 * Little helper function that calls error functions. First it tries to call the
	 * given onError one. If this fails it tries the onJatosError. If this fails
	 * it calls console.error.
	 */
	function callingOnError(onError, errorMsg) {
		if (onError) {
			onError(errorMsg);
		} else if (onJatosError) {
			onJatosError(errorMsg);
		}
		console.error(errorMsg);
	}

	/**
	 * Sets a timeout and puts an object with two functions, 'cancel' and 'trigger'
	 * into the given sessionTimeouts
	 */
	function setChannelSendingTimeoutAndPromiseResolution(deferred, sessionTimeouts,
		sessionActionId, onSuccess, onFail) {
		var timeoutId = setTimeout(function () {
			callFunctionIfExist(onFail, "Timeout sending message");
			deferred.reject("Timeout sending message");
		}, jatos.channelSendingTimeoutTime);

		// Create a new timeout object with two functions: 1) to cancel
		// the timeout and 2) to trigger the timeout prematurely
		sessionTimeouts[sessionActionId] = {
			cancel: function () {
				clearTimeout(timeoutId);
				callFunctionIfExist(onSuccess, "success");
				deferred.resolve("success");
			},
			trigger: function () {
				clearTimeout(timeoutId);
				callFunctionIfExist(onFail, "Error sending message");
				deferred.reject("Error sending message");
			}
		};

		// Always clean up and delete the timeout obj after the deferred is resolved
		deferred.always(function () { delete sessionTimeouts[sessionActionId]; });
	}

	/**
	 * Checks if the given jQuery Deferred or Promise object exists and is not in state pending
	 */
	function isDeferredPending(deferred) {
		return typeof deferred != 'undefined' && deferred.state() == 'pending';
	}

	function rejectedPromise() {
		var deferred = jatos.jQuery.Deferred();
		deferred.reject();
		return deferred.promise();
	}

	function isInvalidComponentPosition(pos) {
		return pos <= 0 || pos > jatos.componentList.length;
	}

	function cloneJsonObj(obj) {
		var copy;

		// Handle the 3 simple types, and null or undefined
		if (null === obj || "object" != typeof obj) return obj;

		// Handle Array
		if (obj instanceof Array) {
			copy = [];
			for (var i = 0, len = obj.length; i < len; i++) {
				copy[i] = cloneJsonObj(obj[i]);
			}
			return copy;
		}

		// Handle object
		if (obj instanceof Object) {
			copy = {};
			for (var attr in obj) {
				if (obj.hasOwnProperty(attr)) copy[attr] = cloneJsonObj(obj[attr]);
			}
			return copy;
		}

		throw new Error("Unable to copy obj! Its type isn't supported.");
	}

})();
