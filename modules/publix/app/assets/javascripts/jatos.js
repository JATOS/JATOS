/**
 * jatos.js (JATOS JavaScript Library)
 * http://www.jatos.org
 * Author Kristian Lange 2014 - 2017
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

var jatos = {};

// Encapsulate the whole library so nothing unintentional gets out (e.g. jQuery
// or functions or variables)
(function () {
	"use strict";

	/**
	 * jatos.js version
	 */
	jatos.version = "3.1.5";
	/**
	 * How long should JATOS wait until to retry the HTTP call. Warning: There is a
	 * general problem with JATOS and HTTP retries. In many cases a JATOS regards a
	 * second call of the same function as a reload of the component. A reload of a
	 * component is often forbidden and leads to failed finish of the study.
	 * Therefore I put the HTTP timeout time to 60 secs. If there is now answer
	 * within this time I assume the call never reached the server and it's our last
	 * hope to continue the study is to retry the call.
	 */
	jatos.httpTimeout = 60000;
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
	 * Represents the state of the group in JATOS; only set if group channel is open
	 */
	jatos.groupState = null;
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
	 * JS timeout objects
	 */
	var batchSessionTimeout;
	var groupSessionTimeout;
	var groupFixedTimeout;
	/**
	 * Version of the current group and batch session data. This is used to prevent
	 * concurrent changes of the data.
	 */
	var groupSessionVersion;
	var batchSessionVersion;
	/**
	 * Group channel WebSocket to exchange messages between workers of a group.
	 * Not to be confused with 'jatos.groupChannels'. Accessible only by jatos.js.
	 */
	var groupChannel;
	/**
	 * Batch channel WebSocket: exchange date between study runs of a batch
	 */
	var batchChannel;
	/**
	 * WebSocket support by the browser is needed for group channel.
	 */
	var webSocketSupported = 'WebSocket' in window;
	/**
	 * Web worker initialized in initJatos() that sends a periodic Ajax request
	 * back to the JATOS server.
	 */
	var heartbeatWorker;

	/**
	 * State booleans. If true jatos.js is in this state. Several states can be true
	 * at the same time.
	 */
	var initialized = false;
	var onJatosLoadCalled = false;
	var startingComponent = false;
	/**
	 * jQuery.Deferred objects: can hold state pending, resolved, or rejected
	 */
	var openingBatchChannelDeferred;
	var sendingBatchSessionDeferred;
	var joiningGroupDeferred;
	var sendingGroupSessionDeferred;
	var sendingGroupFixedDeferred;
	var reassigningGroupDeferred;
	var leavingGroupDeferred;
	var submittingResultDataDeferred;
	var endingDeferred;
	var abortingDeferred;
	/**
	 * Callback function defined via jatos.onLoad.
	 */
	var onJatosLoad;
	/**
	 * Callback function defined via jatos.onBatchSession
	 */
	var onJatosBatchSession;
	/**
	 * Callback function if jatos.js produces an error, defined via jatos.onError.
	 */
	var onJatosError;

	// Load jatos.js's jQuery and put it in jatos.jQuery to avoid conflicts with
	// a component's jQuery version. Afterwards initialise (jatos.js will always be
	// initialised - even if jatos.onLoad() is never called).
	jatos.jQuery = {};
	getScript('/public/lib/jatos-publix/javascripts/jquery-1.12.4.min.js', function () {
		jatos.jQuery = jQuery.noConflict(true);
		loadScripts(initJatos);
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
	 * Load and run additional JS.
	 */
	function loadScripts(onSuccess) {
		if (!jQueryExists()) {
			return;
		}
		jatos.jQuery.when(
			// Plugin to retry ajax calls: https://github.com/johnkpaul/jquery-ajax-retry
			jatos.jQuery.getScript("/public/lib/jatos-publix/javascripts/jquery.ajax-retry.min.js"),
			// JSON Patch library https://github.com/Starcounter-Jack/JSON-Patch
			jatos.jQuery.getScript("/public/lib/jatos-publix/javascripts/json-patch-duplex.js"),
			// JSON Pointer library https://github.com/alexeykuzmin/jsonpointer.js
			jatos.jQuery.getScript("/public/lib/jatos-publix/javascripts/jsonpointer.js"),
			jatos.jQuery.Deferred(function (deferred) {
				jatos.jQuery(deferred.resolve);
			})
		).done(onSuccess).fail(function (err) {
			callingOnError(null, getAjaxErrorMsg(err));
		});
	}

	/**
	 * Initialising jatos.js
	 */
	function initJatos() {
		if (!jQueryExists()) {
			return;
		}

		jatos.studyResultId = getUrlQueryParameter("srid");
		readIdCookie();

		getInitData()
			.then(openBatchChannel)
			.then(ready);

		if (window.Worker) {
			heartbeatWorker = new Worker("/public/lib/jatos-publix/javascripts/heartbeat.js");
			heartbeatWorker.postMessage([jatos.studyId, jatos.studyResultId]);
		}

		/**
		 * Extracts the given URL query parameter from the URL query string
		 */
		function getUrlQueryParameter(parameter) {
			var a = window.location.search.substr(1).split('&');
			if (a === "") return {};
			var b = {};
			for (var i = 0; i < a.length; ++i) {
				var p = a[i].split('=', 2);
				if (p.length == 1)
					b[p[0]] = "";
				else
					b[p[0]] = decodeURIComponent(p[1].replace(/\+/g, " "));
			}
			return b[parameter];
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
				if (idMap.studyResultId == jatos.studyResultId) {
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
				url: "/publix/" + jatos.studyId + "/" + jatos.componentId +
					"/initData" + "?srid=" + jatos.studyResultId,
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
		 * Puts the ajax response into the different jatos variables.
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
				callingOnError(null, error);
			}

			// Study properties
			jatos.studyProperties = initData.studyProperties;
			if (typeof jatos.studyProperties.jsonData != 'undefined') {
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
			if (typeof jatos.componentProperties.jsonData != 'undefined') {
				jatos.componentJsonInput = jatos.jQuery
					.parseJSON(jatos.componentProperties.jsonData);
			} else {
				jatos.componentJsonInput = {};
			}
			delete jatos.componentProperties.jsonData;

			// Initialising finished
			initialized = true;
		}

		/**
		 * Opens the WebSocket for the batch channel which is used to get and
		 * update the batch session data.
		 */
		function openBatchChannel() {
			if (!webSocketSupported) {
				callingOnError(null, "This browser does not support WebSockets. Can't open batch channel.");
				return resolvedPromise();
			}
			// WebSocket's readyState:
			//		CONNECTING 0 The connection is not yet open.
			//		OPEN       1 The connection is open and ready to communicate.
			//		CLOSING    2 The connection is in the process of closing.
			//		CLOSED     3 The connection is closed or couldn't be opened.
			if (isDeferredPending(openingBatchChannelDeferred) ||
				(batchChannel && batchChannel.readyState != 3)) {
				callingOnError(null, "Can open only one batch channel.");
				return resolvedPromise();
			}
			openingBatchChannelDeferred = jatos.jQuery.Deferred();

			batchChannel = new WebSocket(
				((window.location.protocol === "https:") ? "wss://" : "ws://") +
				window.location.host + "/publix/" + jatos.studyId +
				"/batch/open" + "?srid=" + jatos.studyResultId);
			batchChannel.onopen = function (event) {
				// Do nothing -  batch channel opening is only done when we have the batch session version
			};
			batchChannel.onmessage = function (event) {
				handleBatchMsg(event.data);
			};
			batchChannel.onerror = function () {
				// Resolve anyway so initialization can continue
				openingBatchChannelDeferred.resolve();
				callingOnError(null, "Couldn't open a batch channel");
			};
			batchChannel.onclose = function () {
				// Resolve anyway so initialization can continue
				openingBatchChannelDeferred.resolve();
				batchSessionData = {};
				batchSessionVersion = null;
			};

			return openingBatchChannelDeferred.promise();
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

			if (typeof batchMsg.patches != 'undefined') {
				jsonpatch.apply(batchSessionData, batchMsg.patches);
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
					// Call onJatosBatchSession with JSON Patch's path
					if (batchMsg.patches[0] && batchMsg.patches[0].path) {
						callFunctionIfExist(onJatosBatchSession, batchMsg.patches[0].path);
					} else {
						callFunctionIfExist(onJatosBatchSession);
					}
					break;
				case "SESSION_ACK":
					batchSessionTimeout.cancel();
					break;
				case "SESSION_FAIL":
					batchSessionTimeout.trigger();
					break;
				case "ERROR":
					// onError or jatos.onError
					// Got an erro
					callingOnError(null, batchMsg.errorMsg);
					break;
			}
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
	 * JSON Patch add operation
	 * @param {string} path - JSON pointer path 
	 * @param {object} value - value to be stored
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {jQuery.deferred.promise}
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
	 * @return {jQuery.deferred.promise}
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
	 * @return {jQuery.deferred.promise}
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
	 * @return {jQuery.deferred.promise}
	 */
	jatos.batchSession.remove = function (path, onSuccess, onFail) {
		var patch = generatePatch("remove", path, null, null);
		return sendBatchSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * Clears the batch session data and sets it to an empty object
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {jQuery.deferred.promise}
	 */
	jatos.batchSession.clear = function (onSuccess, onFail) {
		return jatos.batchSession.remove("", onSuccess, onFail);
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
	 * @return {jQuery.deferred.promise}
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
	 * @return {jQuery.deferred.promise}
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
	 * @return {jQuery.deferred.promise}
	 */
	jatos.batchSession.move = function (from, path, onSuccess, onFail) {
		var patch = generatePatch("move", path, null, from);
		return sendBatchSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * JSON Patch test operation
	 * @param {string} path - JSON pointer path to be tested
	 * @param {object} value - value to be tested
	 * @return {boolean}
	 */
	jatos.batchSession.test = function (path, value) {
		var patches = [];
		patches.push(generatePatch("test", path, value, null));
		return jsonpatch.apply(batchSessionData, patches);
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
	 * Sends a JSON Patch via the batch channel to JATOS and subsequently to all
	 * other study currently running in this batch
	 */
	function sendBatchSessionPatch(patch, onSuccess, onFail) {
		if (!batchChannel || batchChannel.readyState != 1) {
			callingOnError(onFail, "No open batch channel");
			return rejectedPromise();
		}
		if (isDeferredPending(sendingBatchSessionDeferred)) {
			callingOnError(onFail, "Can send only one batch session patch at a time");
			return rejectedPromise();
		}

		sendingBatchSessionDeferred = jatos.jQuery.Deferred();
		var msgObj = {};
		msgObj.action = "SESSION";
		msgObj.patches = [];
		msgObj.patches.push(patch);
		msgObj.version = batchSessionVersion;
		try {
			batchChannel.send(JSON.stringify(msgObj));
			// Setup timeout: How long to wait for an answer from JATOS.
			batchSessionTimeout = setChannelSendingTimeout(
				sendingBatchSessionDeferred, onSuccess, onFail);
		} catch (error) {
			callingOnError(onFail, error);
			sendingBatchSessionDeferred.reject();
		}
		return sendingBatchSessionDeferred.promise();
	}

	/**
	 * Call onJatosLoad() if it already exists and jatos.js is initialised
	 */
	function ready() {
		if (onJatosLoad && !onJatosLoadCalled && initialized) {
			onJatosLoadCalled = true;
			onJatosLoad();
		}
	}

	/**
	 * A web worker used in jatos.js to send periodic Ajax requests back to the
	 * JATOS server. With this function one can set the period with which the
	 * heartbeat is send.
	 * 
	 * @param {Number}
	 *            heartbeatPeriod - in milliseconds (Integer)
	 */
	jatos.setHeartbeatPeriod = function (heartbeatPeriod) {
		if (typeof heartbeatPeriod == 'number' && heartbeatWorker) {
			heartbeatWorker.postMessage([jatos.studyId, jatos.studyResultId,
				heartbeatPeriod
			]);
		}
	};

	/**
	 * Defines callback function that is to be called when jatos.js finished its
	 * initialisation.
	 */
	jatos.onLoad = function (onLoad) {
		onJatosLoad = onLoad;
		ready();
	};

	/**
	 * Defines callback function that is to be called in case jatos.js produces an
	 * error, e.g. Ajax errors.
	 */
	jatos.onBatchSession = function (onBatchSession) {
		onJatosBatchSession = onBatchSession;
	};

	/**
	 * Defines callback function that is to be called in case jatos.js produces an
	 * error, e.g. Ajax errors.
	 */
	jatos.onError = function (onError) {
		onJatosError = onError;
	};

	/**
	 * Posts resultData back to the JATOS server.
	 * 
	 * @param {Object}
	 *            resultData - String to be submitted
	 * @param {optional
	 *            Function} onSuccess - Function to be called in case of successful
	 * @param {optional
	 *            Function} onError - (DEPRECATED) Function to be called in case
	 *            of error
	 * @return {jQuery.deferred.promise}
	 */
	jatos.submitResultData = function (resultData, onSuccess, onError) {
		if (isDeferredPending(submittingResultDataDeferred)) {
			callingOnError(onError, "Can send only one result data at a time");
			return rejectedPromise();
		}

		submittingResultDataDeferred = jatos.jQuery.Deferred();
		jatos.jQuery.ajax({
			url: "/publix/" + jatos.studyId + "/" +
				jatos.componentId + "/resultData" +
				"?srid=" + jatos.studyResultId,
			data: resultData,
			processData: false,
			type: "POST",
			contentType: "text/plain; charset=UTF-8",
			timeout: jatos.httpTimeout,
			success: function (response) {
				callFunctionIfExist(onSuccess, response);
				submittingResultDataDeferred.resolve(response);
			},
			error: function (err) {
				var errMsg = getAjaxErrorMsg(err);
				callingOnError(onError, errMsg);
				submittingResultDataDeferred.reject(errMsg);
			}
		}).retry({
			times: jatos.httpRetry,
			timeout: jatos.httpRetryWait
		});
		return submittingResultDataDeferred;
	};

	/**
	 * Posts study session data back to the JATOS server. This function is called by
	 * all functions that start a new component, so it shouldn't be necessary to
	 * call it manually.
	 * 
	 * @param {Object}
	 *            studySessionData - Object to be submitted
	 * @param {optional
	 *            Function} onSuccess - Function to be called after this function is
	 *            finished
	 * @param {optional
	 *            Function} onFail - Function to be called after if this this
	 *            functions fails
	 * @return {jQuery.deferred.promise}
	 */
	jatos.setStudySessionData = function (studySessionData, onSuccess, onFail) {
		var deferred = jatos.jQuery.Deferred();
		var studySessionDataStr;
		try {
			studySessionDataStr = JSON.stringify(studySessionData);
		} catch (error) {
			callingOnError(null, error);
			callFunctionIfExist(onFail);
			deferred.reject(errMsg);
			return deferred;
		}
		jatos.jQuery.ajax({
			url: "/publix/" + jatos.studyId + "/studySessionData" + "?srid=" + jatos.studyResultId,
			data: studySessionDataStr,
			processData: false,
			type: "POST",
			contentType: "text/plain; charset=UTF-8",
			timeout: jatos.httpTimeout,
			success: function () {
				callFunctionIfExist(onSuccess);
				deferred.resolve();
			},
			error: function (err) {
				var errMsg = getAjaxErrorMsg(err);
				callingOnError(onFail, errMsg);
				deferred.reject(errMsg);
			}
		}).retry({
			times: jatos.httpRetry,
			timeout: jatos.httpRetryWait
		});
		return deferred;
	};

	/**
	 * Starts the component with the given ID.
	 * 
	 * @param {Object}
	 *            componentId - ID of the component to start
	 */
	jatos.startComponent = function (componentId) {
		if (startingComponent) {
			return;
		}
		startingComponent = true;
		var onComplete = function () {
			window.location.href = "/publix/" + jatos.studyId + "/" + componentId +
				"/start" + "?srid=" + jatos.studyResultId;
		};
		jatos.setStudySessionData(jatos.studySessionData).always(onComplete);
	};

	/**
	 * Starts the component with the given position (# of component within study).
	 * 
	 * @param {Object}
	 *            componentPos - Position of the component to start
	 */
	jatos.startComponentByPos = function (componentPos) {
		if (startingComponent) {
			return;
		}
		startingComponent = true;
		var onComplete = function () {
			window.location.href = "/publix/" + jatos.studyId +
				"/component/start?position=" + componentPos + "&srid=" + jatos.studyResultId;
		};
		jatos.setStudySessionData(jatos.studySessionData).always(onComplete);
	};

	/**
	 * Starts the next component of this study. The next component is the one with
	 * position + 1.
	 */
	jatos.startNextComponent = function () {
		if (startingComponent) {
			return;
		}
		startingComponent = true;
		var onComplete = function () {
			window.location.href = "/publix/" + jatos.studyId +
				"/nextComponent/start" + "?srid=" + jatos.studyResultId;
		};
		jatos.setStudySessionData(jatos.studySessionData).always(onComplete);
	};

	/**
	 * Starts the last component of this study or if it's inactive the component
	 * with the highest position that is active.
	 */
	jatos.startLastComponent = function () {
		for (var i = jatos.componentList.length - 1; i >= 0; i--) {
			if (jatos.componentList[i].active) {
				jatos.startComponentByPos(i + 1);
				break;
			}
		}
	};

	/**
	 * @DEPRECATED since jatos.js 3.1.1
	 * Finishes component. Usually this is not necessary because the last component
	 * is automatically finished if the new component is started. Nevertheless it's
	 * useful to explicitly tell about a FAIL and submit an error message. Finishing
	 * the component doesn't finish the study.
	 * 
	 * @param {optional
	 *            Boolean} successful - 'true' if study should finish successful and
	 *            the participant should get the confirmation code - 'false'
	 *            otherwise.
	 * @param {optional
	 *            String} errorMsg - Error message that should be logged.
	 * @param {optional
	 *            Function} onSuccess - Function to be called in case of successful
	 *            submit
	 * @param {optional
	 *            Function} onError - Function to be called in case of error
	 */
	jatos.endComponent = function (successful, errorMsg, onSuccess, onError) {
		if (isDeferredPending(endingDeferred)) {
			callingOnError(onError, "Can end only once");
			return rejectedPromise();
		}

		endingDeferred = jatos.jQuery.Deferred();
		console.warn("Usage of jatos.endComponent is deprecated. " +
			"Use jatos.startComponent, jatos.startComponentByPos, jatos.startNextComponent, " +
			"jatos.startLastComponent instead. See http://www.jatos.org/jatos.js-Reference.html.");
		var onComplete = function () {
			var url = "/publix/" + jatos.studyId + "/" + jatos.componentId +
				"/end" + "?srid=" + jatos.studyResultId;
			var fullUrl;
			if (typeof successful != 'undefined' && typeof errorMsg != 'undefined') {
				fullUrl = url + "&successful=" + successful + "&errorMsg=" + errorMsg;
			} else if (typeof successful != 'undefined') {
				fullUrl = url + "&successful=" + successful;
			} else if (typeof errorMsg != 'undefined') {
				fullUrl = url + "&errorMsg=" + errorMsg;
			} else {
				fullUrl = url;
			}
			jatos.jQuery.ajax({
				url: fullUrl,
				processData: false,
				type: "GET",
				timeout: jatos.httpTimeout,
				success: function (response) {
					callFunctionIfExist(onSuccess, response);
				},
				error: function (err) {
					callingOnError(onError, getAjaxErrorMsg(err));
				}
			}).retry({
				times: jatos.httpRetry,
				timeout: jatos.httpRetryWait
			});
		};
		jatos.setStudySessionData(jatos.studySessionData).always(onComplete);
	};

	/**
	 * Tries to join a group (actually a GroupResult) in the JATOS server and if it
	 * succeeds opens the group channel's WebSocket.
	 * 
	 * @param {Object} callbacks - Defining callback functions for group events. All
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
	 * @return {jQuery.deferred.promise}
	 */
	jatos.joinGroup = function (callbacks) {
		callbacks = callbacks ? callbacks : {};
		if (!webSocketSupported) {
			callingOnError(callbacks.onError,
				"This browser does not support WebSockets.");
			return rejectedPromise();
		}
		// WebSocket's readyState:
		//		CONNECTING 0 The connection is not yet open.
		//		OPEN       1 The connection is open and ready to communicate.
		//		CLOSING    2 The connection is in the process of closing.
		//		CLOSED     3 The connection is closed or couldn't be opened.
		if (isDeferredPending(joiningGroupDeferred) ||
			isDeferredPending(reassigningGroupDeferred) ||
			isDeferredPending(leavingGroupDeferred) ||
			!callbacks || (groupChannel && groupChannel.readyState != 3)) {
			callingOnError(callbacks.onError, "Can join only once");
			return rejectedPromise();
		}

		joiningGroupDeferred = jatos.jQuery.Deferred();
		groupChannel = new WebSocket(
			((window.location.protocol === "https:") ? "wss://" : "ws://") +
			window.location.host + "/publix/" + jatos.studyId +
			"/group/join" + "?srid=" + jatos.studyResultId);
		groupChannel.onopen = function (event) {
			// Do nothing -  group channel opening is only done when we have the group session version
		};
		groupChannel.onmessage = function (event) {
			handleGroupMsg(event.data, callbacks);
		};
		groupChannel.onerror = function () {
			joiningGroupDeferred.reject();
			callingOnError(callbacks.onError, "Couldn't open a group channel");
		};
		groupChannel.onclose = function () {
			joiningGroupDeferred.reject();
			jatos.groupMemberId = null;
			jatos.groupResultId = null;
			jatos.groupState = null;
			jatos.groupMembers = [];
			jatos.groupChannels = [];
			groupSessionData = {};
			groupSessionVersion = null;
			callFunctionIfExist(callbacks.onClose);
		};
		return joiningGroupDeferred;
	};

	/**
	 * A group message from the JATOS server can be an action, a message from an
	 * other group member, or an error. An action usually comes with the current
	 * group variables (members, channels, group session data etc.). A group message
	 * from the JATOS server is always in JSON format.
	 */
	function handleGroupMsg(msg, callbacks) {
		var groupMsg = JSON.parse(msg);
		updateGroupVars(groupMsg, callbacks);
		// Now handle the action and map them to callbacks that were given as
		// parameter to joinGroup
		callGroupActionCallbacks(groupMsg, callbacks);
		// Handle onMessage callback
		if (groupMsg.msg && callbacks.onMessage) {
			callbacks.onMessage(groupMsg.msg);
		}
	}

	/**
	 * Update the group variables that usually come with an group action
	 */
	function updateGroupVars(groupMsg, callbacks) {
		if (typeof groupMsg.groupResultId != 'undefined') {
			jatos.groupResultId = groupMsg.groupResultId.toString();
			// Group member ID is equal to study result ID
			jatos.groupMemberId = jatos.studyResultId;
		}
		if (typeof groupMsg.groupState != 'undefined') {
			jatos.groupState = groupMsg.groupState;
		}
		if (typeof groupMsg.members != 'undefined') {
			jatos.groupMembers = groupMsg.members;
		}
		if (typeof groupMsg.channels != 'undefined') {
			jatos.groupChannels = groupMsg.channels;
		}
		if (typeof groupMsg.sessionPatches != 'undefined') {
			jsonpatch.apply(groupSessionData, groupMsg.sessionPatches);
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
			joiningGroupDeferred.resolve();
		}
	}

	function callGroupActionCallbacks(groupMsg, callbacks) {
		if (!groupMsg.action) {
			return;
		}
		switch (groupMsg.action) {
			case "OPENED":
				// onOpen and onMemberOpen
				// Someone opened a group channel; distinguish between the worker running
				// this study and others
				if (groupMsg.memberId == jatos.groupMemberId) {
					callFunctionIfExist(callbacks.onOpen, groupMsg.memberId);
				} else {
					callFunctionIfExist(callbacks.onMemberOpen, groupMsg.memberId);
					callFunctionIfExist(callbacks.onUpdate);
				}
				break;
			case "CLOSED":
				// onMemberClose
				// Some member closed its group channel
				// (onClose callback function is handled during groupChannel.onclose)
				if (groupMsg.memberId != jatos.groupMemberId) {
					callFunctionIfExist(callbacks.onMemberClose, groupMsg.memberId);
					callFunctionIfExist(callbacks.onUpdate);
				}
				break;
			case "JOINED":
				// onMemberJoin
				// Some member joined (it should not happen, but check the group member ID
				// (aka study result ID) is not the one of the joined member)
				if (groupMsg.memberId != jatos.groupMemberId) {
					callFunctionIfExist(callbacks.onMemberJoin, groupMsg.memberId);
					callFunctionIfExist(callbacks.onUpdate);
				}
				break;
			case "LEFT":
				// onMemberLeave
				// Some member left (it should not happen, but check the group member ID
				// (aka study result ID) is not the one of the left member)
				if (groupMsg.memberId != jatos.groupMemberId) {
					callFunctionIfExist(callbacks.onMemberLeave, groupMsg.memberId);
					callFunctionIfExist(callbacks.onUpdate);
				}
				break;
			case "SESSION":
				// onGroupSession
				// Got updated group session data and version
				// Call onGroupSession with JSON Patch's path
				if (groupMsg.sessionPatches[0] && groupMsg.sessionPatches[0].path) {
					callFunctionIfExist(callbacks.onGroupSession, groupMsg.sessionPatches[0].path);
				} else {
					callFunctionIfExist(callbacks.onGroupSession);
				}
				callFunctionIfExist(callbacks.onUpdate);
				break;
			case "FIXED":
				// The group is now fixed (no new members)
				if (groupFixedTimeout) {
					groupFixedTimeout.cancel();
				}
				callFunctionIfExist(callbacks.onUpdate);
				break;
			case "SESSION_ACK":
				groupSessionTimeout.cancel();
				break;
			case "SESSION_FAIL":
				groupSessionTimeout.trigger();
				break;
			case "ERROR":
				// onError or jatos.onError
				// Got an error
				callingOnError(callbacks.onError, groupMsg.errorMsg);
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
	 * JSON Patch add operation
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {jQuery.deferred.promise}
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
	 * @return {jQuery.deferred.promise}
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
	 * @return {jQuery.deferred.promise}
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
	 * @return {jQuery.deferred.promise}
	 */
	jatos.groupSession.remove = function (path, onSuccess, onFail) {
		var patch = generatePatch("remove", path, null, null);
		return sendGroupSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * Clears the group session data and sets it to an empty object
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {jQuery.deferred.promise}
	 */
	jatos.groupSession.clear = function (onSuccess, onFail) {
		return jatos.groupSession.remove("", onSuccess, onFail);
	};

	/**
	 * JSON Patch replace operation
	 * @param {optional callback} onSuccess - Function to be called if
	 *             this patch was successfully applied on the server and
	 *             the client side
	 * @param {optional callback} onError - Function to be called if
	 *             this patch failed
	 * @return {jQuery.deferred.promise}
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
	 * @return {jQuery.deferred.promise}
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
	 * @return {jQuery.deferred.promise}
	 */
	jatos.groupSession.move = function (from, path, onSuccess, onFail) {
		var patch = generatePatch("move", path, null, from);
		return sendGroupSessionPatch(patch, onSuccess, onFail);
	};

	/**
	 * JSON Patch test operation
	 * @return {boolean}
	 */
	jatos.groupSession.test = function (path, value) {
		var patches = [];
		patches.push(generatePatch("test", path, value, null));
		return jsonpatch.apply(groupSessionData, patches);
	};

	/**
	 * Sends a JSON Patch via the group channel to JATOS and subsequently to all
	 * other study currently running in this group
	 */
	function sendGroupSessionPatch(patch, onSuccess, onFail) {
		if (!groupChannel || groupChannel.readyState != 1) {
			callingOnError(onFail, "No open group channel");
			return rejectedPromise();
		}
		if (isDeferredPending(sendingGroupSessionDeferred)) {
			callingOnError(onFail, "Can send only one group session patch at a time");
			return rejectedPromise();
		}

		sendingGroupSessionDeferred = jatos.jQuery.Deferred();
		var msgObj = {};
		msgObj.action = "SESSION";
		msgObj.sessionPatches = [];
		msgObj.sessionPatches.push(patch);
		msgObj.sessionVersion = groupSessionVersion;
		try {
			groupChannel.send(JSON.stringify(msgObj));
			// Setup timeout: How long to wait for an answer from JATOS.
			groupSessionTimeout = setChannelSendingTimeout(
				sendingGroupSessionDeferred, onSuccess, onFail);
		} catch (error) {
			callingOnError(onFail, error);
			sendingGroupSessionDeferred.reject();
		}
		return sendingGroupSessionDeferred.promise();
	}

	/**
	 * Ask the JATOS server to fix this group.
	 * @param {optional callback} onSuccess - Function to be called if
	 *             the fixing was successful
	 * @param {optional callback} onFail - Function to be called if
	 *             the fixing failed
	 * @return {jQuery.deferred.promise}
	 */
	jatos.setGroupFixed = function (onSuccess, onFail) {
		if (!groupChannel || groupChannel.readyState != 1) {
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
			groupFixedTimeout = setChannelSendingTimeout(
				sendingGroupFixedDeferred, onSuccess, onFail);
		} catch (error) {
			callingOnError(onFail, error);
			sendingGroupFixedDeferred.reject();
		}
		return sendingGroupFixedDeferred.promise();
	};

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
		return groupChannel && groupChannel.readyState == 1;
	};

	/**
	 * @return {Boolean} True if the group has reached the maximum amount of active
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
	 * @return {Boolean} True if the group has reached the maximum amount of active
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
	 * @return {Boolean} True if all active members of the group have an open group
	 *         channel. It's not necessary that the group has reached its minimum
	 *         or maximum active member size.
	 */
	jatos.isGroupOpen = function () {
		if (groupChannel && groupChannel.readyState == 1) {
			return jatos.groupMembers.length == jatos.groupChannels.length;
		} else {
			return false;
		}
	};

	/**
	 * Sends a message to all group members if group channel is open.
	 * 
	 * @param {Object} msg - Any JavaScript object
	 */
	jatos.sendGroupMsg = function (msg) {
		if (groupChannel && groupChannel.readyState == 1) {
			var msgObj = {};
			msgObj.msg = msg;
			groupChannel.send(JSON.stringify(msgObj));
		}
	};

	/**
	 * Sends a message to a single group member specified with the given member ID
	 * (only if group channel is open).
	 * 
	 * @param {String} recipient - Recipient's group member ID
	 * @param {Object} msg - Any JavaScript object
	 */
	jatos.sendGroupMsgTo = function (recipient, msg) {
		if (groupChannel && groupChannel.readyState == 1) {
			var msgObj = {};
			msgObj.recipient = recipient;
			msgObj.msg = msg;
			groupChannel.send(JSON.stringify(msgObj));
		}
	};

	/**
	 * Asks the JATOS server to reassign this study run to a different group.
	 * 
	 * @param {optional Function} onSuccess - Function to be called if the
	 *            reassignment was successful
	 * @param {optional Function} onFail - Function to be called if the
	 *            reassignment was unsuccessful. 
	 * @return {jQuery.deferred.promise}
	 */
	jatos.reassignGroup = function (onSuccess, onFail) {
		if (isDeferredPending(joiningGroupDeferred) ||
			isDeferredPending(reassigningGroupDeferred) ||
			isDeferredPending(leavingGroupDeferred) ||
			(groupChannel && groupChannel.readyState != 1)) {
			callingOnError(onFail, "Can reassign only once");
			return rejectedPromise();
		}

		reassigningGroupDeferred = jatos.jQuery.Deferred();
		jatos.jQuery.ajax({
			url: "/publix/" + jatos.studyId + "/group/reassign" + "?srid=" + jatos.studyResultId,
			processData: false,
			type: "GET",
			timeout: jatos.httpTimeout,
			statusCode: {
				200: function () {
					// Successful reassignment
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
		return reassigningGroupDeferred;
	};

	/**
	 * Tries to leave the group (actually a GroupResult) it has previously joined.
	 * The group channel WebSocket is not closed in this function - it's closed from
	 * the JATOS' side.
	 * 
	 * @param {optional Function} onSuccess - Function to be called after the group
	 *            is left.
	 * @param {optional Function} onError - Function to be called in case of error.
	 * @return {jQuery.deferred.promise}
	 */
	jatos.leaveGroup = function (onSuccess, onError) {
		if (isDeferredPending(joiningGroupDeferred) ||
			isDeferredPending(reassigningGroupDeferred) ||
			isDeferredPending(leavingGroupDeferred)) {
			callingOnError(onError, "Can leave only once");
			return rejectedPromise();
		}

		leavingGroupDeferred = jatos.jQuery.Deferred();
		jatos.jQuery.ajax({
			url: "/publix/" + jatos.studyId + "/group/leave" + "?srid=" + jatos.studyResultId,
			processData: false,
			type: "GET",
			timeout: jatos.httpTimeout,
			success: function (response) {
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
		return leavingGroupDeferred;
	};

	/**
	 * Aborts study. All previously submitted data will be deleted.
	 * 
	 * @param {optional
	 *            String} message - Message that should be logged
	 * @param {optional
	 *            Function} onSuccess - Function to be called in case of successful
	 *            submit
	 * @param {optional
	 *            Function} onError - Function to be called in case of error
	 * @return {jQuery.deferred.promise}
	 */
	jatos.abortStudyAjax = function (message, onSuccess, onError) {
		if (isDeferredPending(abortingDeferred)) {
			callingOnError(onError, "Can abort only once");
			return rejectedPromise();
		}

		function abort() {
			abortingDeferred = jatos.jQuery.Deferred();
			var url = "/publix/" + jatos.studyId + "/abort" + "?srid=" + jatos.studyResultId;
			var fullUrl;
			if (typeof message == 'undefined') {
				fullUrl = url;
			} else {
				fullUrl = url + "&message=" + message;
			}
			jatos.jQuery.ajax({
				url: fullUrl,
				processData: false,
				type: "GET",
				timeout: jatos.httpTimeout,
				success: function (response) {
					callFunctionIfExist(onSuccess, response);
					abortingDeferred.resolve(response);
				},
				error: function (err) {
					var errMsg = getAjaxErrorMsg(err);
					callingOnError(onError, errMsg);
					abortingDeferred.reject(errMsg);
				}
			}).retry({
				times: jatos.httpRetry,
				timeout: jatos.httpRetryWait
			});
			return abortingDeferred;
		}

		return jatos.setStudySessionData(jatos.studySessionData).always(abort);
	};

	/**
	 * Aborts study. All previously submitted data will be deleted.
	 * 
	 * @param {optional
	 *            String} message - Message that should be logged
	 */
	jatos.abortStudy = function (message) {
		if (isDeferredPending(abortingDeferred)) {
			callingOnError(onError, "Can abort only once");
			return rejectedPromise();
		}

		abortingDeferred = jatos.jQuery.Deferred();

		function abort() {
			var url = "/publix/" + jatos.studyId + "/abort" + "?srid=" + jatos.studyResultId;
			if (typeof message == 'undefined') {
				window.location.href = url;
			} else {
				window.location.href = url + "&message=" + message;
			}
		}
		jatos.setStudySessionData(jatos.studySessionData).always(abort);
	};

	/**
	 * Ends study with an Ajax call.
	 * 
	 * @param {optional
	 *            Boolean} successful - 'true' if study should finish successful and
	 *            the participant should get the confirmation code - 'false'
	 *            otherwise.
	 * @param {optional
	 *            String} errorMsg - Error message that should be logged.
	 * @param {optional
	 *            Function} onSuccess - Function to be called in case of successful
	 *            submit
	 * @param {optional
	 *            Function} onError - Function to be called in case of error
	 * @return {jQuery.deferred.promise}
	 */
	jatos.endStudyAjax = function (successful, errorMsg, onSuccess, onError) {
		if (isDeferredPending(endingDeferred)) {
			callingOnError(onError, "Can end only once");
			return rejectedPromise();
		}

		function end() {
			endingDeferred = jatos.jQuery.Deferred();
			var url = "/publix/" + jatos.studyId + "/end" + "?srid=" + jatos.studyResultId;
			var fullUrl;
			if (typeof successful == 'boolean' && typeof errorMsg == 'string') {
				fullUrl = url + "&" + jatos.jQuery.param({
					"successful": successful,
					"errorMsg": errorMsg
				});
			} else if (typeof successful == 'boolean' && typeof errorMsg != 'string') {
				fullUrl = url + "&" + jatos.jQuery.param({
					"successful": successful
				});
			} else if (typeof successful != 'boolean' && typeof errorMsg == 'string') {
				fullUrl = url + "&" + jatos.jQuery.param({
					"errorMsg": errorMsg
				});
			} else {
				fullUrl = url;
			}
			jatos.jQuery.ajax({
				url: fullUrl,
				processData: false,
				type: "GET",
				timeout: jatos.httpTimeout,
				success: function (response) {
					callFunctionIfExist(onSuccess, response);
					endingDeferred.resolve(response);
				},
				error: function (err) {
					var errMsg = getAjaxErrorMsg(err);
					callingOnError(onError, errMsg);
					endingDeferred.reject(errMsg);
				}
			}).retry({
				times: jatos.httpRetry,
				timeout: jatos.httpRetryWait
			});
			return endingDeferred;
		}
		return jatos.setStudySessionData(jatos.studySessionData).always(end);
	};

	/**
	 * Ends study.
	 * 
	 * @param {optional
	 *            Boolean} successful - 'true' if study should finish successful and
	 *            the participant should get the confirmation code - 'false'
	 *            otherwise.
	 * @param {optional
	 *            String} errorMsg - Error message that should be logged.
	 */
	jatos.endStudy = function (successful, errorMsg) {
		if (isDeferredPending(endingDeferred)) {
			callingOnError(onError, "Can end only once");
			return rejectedPromise();
		}

		function end() {
			endingDeferred = jatos.jQuery.Deferred();
			var url = "/publix/" + jatos.studyId + "/end" + "?srid=" + jatos.studyResultId;
			var fullUrl;
			if (typeof successful == 'boolean' && typeof errorMsg == 'string') {
				fullUrl = url + "&" + jatos.jQuery.param({
					"successful": successful,
					"errorMsg": errorMsg
				});
			} else if (typeof successful == 'boolean' && typeof errorMsg != 'string') {
				fullUrl = url + "&" + jatos.jQuery.param({
					"successful": successful
				});
			} else if (typeof successful != 'boolean' && typeof errorMsg == 'string') {
				fullUrl = url + "&" + jatos.jQuery.param({
					"errorMsg": errorMsg
				});
			} else {
				fullUrl = url;
			}
			window.location.href = fullUrl;
		}

		jatos.setStudySessionData(jatos.studySessionData).always(end);
	};

	/**
	 * Logs a message within the JATOS log on the server side.
	 * Deprecated, use jatos.log instead.
	 */
	jatos.logError = function (logErrorMsg) {
		jatos.log(logErrorMsg);
	};

	/**
	 * Logs a message within the JATOS log on the server side.
	 */
	jatos.log = function (logMsg) {
		jatos.jQuery.ajax({
			url: "/publix/" + jatos.studyId + "/" + jatos.componentId +
				"/log" + "?srid=" + jatos.studyResultId,
			data: logMsg,
			processData: false,
			type: "POST",
			contentType: "text/plain; charset=UTF-8",
			timeout: jatos.httpTimeout,
			error: function (err) {
				callingOnError(null, getAjaxErrorMsg(err));
			}
		}).retry({
			times: jatos.httpRetry,
			timeout: jatos.httpRetryWait
		});
	};

	/**
	 * Convenience function that adds all JATOS IDs (study ID, study title, 
	 * component ID, component position, component title, worker ID,
	 * study result ID, component result ID, group result ID, group member ID)
	 * to the given object.
	 * 
	 * @param {Object}
	 *            obj - Object to which the IDs will be added
	 */
	jatos.addJatosIds = function (obj) {
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

	function callFunctionIfExist(f, a) {
		if (f && typeof f == 'function') {
			if (typeof a != 'undefined') {
				f(a);
			} else {
				f();
			}
		}
	}

	/**
	 * Should be called in the beginning of each function that wants to use jQuery.
	 */
	function jQueryExists() {
		if (!jatos.jQuery) {
			if (onJatosError) {
				onJatosError("jatos.js' jQuery not (yet?) loaded");
			}
			return false;
		}
		return true;
	}

	/**
	 * Takes a jQuery Ajax response and returns an error message.
	 */
	function getAjaxErrorMsg(jqxhr) {
		if (jqxhr.statusText == 'timeout') {
			return "JATOS server not responding while trying to get URL";
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
	 * given onError one. If this fails it tries the onJatosError.
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
	 * Sets a timeout and returns an object with two functions 1) to cancel the
	 * timeout and 2) to trigger the timeout prematurely
	 */
	function setChannelSendingTimeout(deferred, onSuccess, onFail) {
		var timeoutId = setTimeout(function () {
			callFunctionIfExist(onFail, "Timeout sending message");
			deferred.reject("Timeout sending message");
		}, jatos.channelSendingTimeoutTime);
		return {
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
	}

	/**
	 * Checks if the given jQuery Deferred object exists and is not in state pending
	 */
	function isDeferredPending(deferred) {
		return typeof deferred != 'undefined' && deferred.state() == 'pending';
	}

	function rejectedPromise() {
		var deferred = jatos.jQuery.Deferred();
		deferred.reject();
		return deferred.promise();
	}

	function resolvedPromise() {
		var deferred = jatos.jQuery.Deferred();
		deferred.resolve();
		return deferred.promise();
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

		// Handle Object
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