/**
 * jatos.js (JATOS JavaScript Library)
 * Version 2.1.1
 * http://www.jatos.org
 * Author Kristian Lange 2014 - 2015
 * Licensed under Apache License 2.0
 * 
 * Uses plugin jquery.ajax-retry:
 * https://github.com/johnkpaul/jquery-ajax-retry
 * Copyright (c) 2012 John Paul
 * Licensed under the MIT license.
 */

var jatos = {};

// Encapsulate the whole library so nothing unintentional gets out (e.g. jQuery
// or functions or variables)
(function() {
"use strict";
	
jatos.version = "2.1.1";
	
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
 * How long should jatos.js wait between a failed HTTP call and a retry.
 */
jatos.httpRetryWait = 1000;
/**
 * Member IDs of the current members of the group.
 */
jatos.groupMembers = [];
/**
 * member IDs of the currently open group channels.
 */
jatos.groupChannels = [];
/**
 * Possible group states are:
 * CLOSE: No group joined (and group channel is closed)
 * INCOMPLETE: Joined a group and other members are still allowed to join
 * COMPLETE: Joined a group and but no new members are allowed to join
 */
var groupState = Object.freeze({
	CLOSE : "CLOSE",
	INCOMPLETE : "INCOMPLETE",
	COMPLETE : "COMPLETE",
});
/**
 * Current group state 
 */
jatos.groupState = groupState.CLOSE;
/**
 * Group state prior to the last change. For internal jatos.js use only.
 */
var priorGroupState;
/**
 * Group channel WebSocket to exchange messages between workers of a group.
 * Not to be confused with 'jatos.groupChannels'. Accessible only by jatos.js.
 */
var groupChannel;
/**
 * WebSocket support by the browser is needed for group channel.
 */
var webSocketSupported = 'WebSocket' in window;

/**
 * State booleans. If true jatos.js is in this state. Several states can be true
 * at the same time.
 */
var initialized = false;
var onJatosLoadCalled = false;
var startingComponent = false;
var endingComponent = false;
var submittingResultData = false;
var joiningGroup = false;
var leavingGroup = false;
var abortingComponent = false;

/**
 * Callback function defined via jatos.onLoad.
 */
var onJatosLoad;
/**
 * Callback function if jatos.js produces an error, defined via jatos.onError.
 */
var onJatosError;

// Load jatos.js's jQuery and put it in jatos.jQuery to avoid conflicts with
// a component's jQuery version. Afterwards initialise (jatos.js will always be
// initialised - even if jatos.onLoad() is never called).
jatos.jQuery;
getScript('/assets/javascripts/jquery-1.11.1.min.js', function() {
	jatos.jQuery = jQuery.noConflict(true);
	loadScripts(initJatos);
});

/**
 * Adds a <script> element into HTML's head and call success function when loaded
 */
function getScript(url, onSuccess) {
	var script = document.createElement('script');
	script.src = url;
	var head = document.getElementsByTagName('head')[0], done = false;
	script.onload = script.onreadystatechange = function() {
		if (!done && (!this.readyState || this.readyState == 'loaded'
				|| this.readyState == 'complete')) {
			done = true;
			onSuccess();
			script.onload = script.onreadystatechange = null;
			head.removeChild(script);
		}
	}
	head.appendChild(script);
}

/**
 * Load and run additional JS.
 */
function loadScripts(onSuccess) {
	if (!jQueryExists()) {
		return;
	}
	// Plugin to retry ajax calls 
	jatos.jQuery.ajax({
		url: "/assets/javascripts/jquery.ajax-retry.min.js",
		dataType: "script",
		cache: true,
		error: function(err) {
			callingOnError(null, getAjaxErrorMsg(err))
		}
	}).done(onSuccess);
}

/**
 * Initialising jatos.js
 */
function initJatos() {
	
	var studyPropertiesReady = false;
	var studySessionDataReady = false;
	var componentPropertiesReady = false;
	
	if (!jQueryExists()) {
		return;
	}
	readIdCookie();
	getInitData();

	/**
	 * Reads JATOS' ID cookie and stores all key-value pairs into jatos scope.
	 */
	function readIdCookie() {
		var nameEQ = escape("JATOS_IDS") + "=";
		var ca = document.cookie.split(';');
		for (var i = 0; i < ca.length; i++) {
			var c = ca[i];
			while (c.charAt(0) === ' ') {
				c = c.substring(1, c.length);
			}
			if (c.indexOf(nameEQ) === 0) {
				var cookieStr = unescape(c.substring(nameEQ.length + 1,
						c.length - 1));
				var idMap = cookieStr.split("&");
				idMap.forEach(function(entry) {
					var keyValuePair = entry.split("=");
					jatos[keyValuePair[0]] = keyValuePair[1];
				});
			}
		}
		// Convert component's position to int
		jatos.componentPos = parseInt(jatos.componentPos);
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
		jatos.jQuery.ajax({
			url : "/publix/" + jatos.studyId + "/" + jatos.componentId
					+ "/initData",
			type : "GET",
			dataType : 'json',
			timeout : jatos.httpTimeout,
			success : setInitData,
			error: function(err) {
				callingOnError(null, getAjaxErrorMsg(err))
			}
		}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
	}

	/**
	 * Puts the ajax response into the different jatos variables.
	 */
	function setInitData(initData) {
		// Session data
		try {
			jatos.studySessionData = jatos.jQuery.parseJSON(initData.studySession);
		} catch (e) {
			jatos.studySessionData = "Error parsing JSON";
			if (onJatosError) {
				onJatosError(e);
			}
		}
		jatos.studySessionDataFrozen = Object.freeze({
			"sessionDataStr" : initData.studySession
		});
		
		// Study properties
		jatos.studyProperties = initData.studyProperties;
		jatos.studyJsonInput = jatos.jQuery
				.parseJSON(jatos.studyProperties.jsonData);
		delete jatos.studyProperties.jsonData;
		
		// Study's component list
		jatos.componentList = initData.componentList;
		
		// Component properties
		jatos.componentProperties = initData.componentProperties;
		jatos.componentJsonInput = jatos.jQuery
				.parseJSON(jatos.componentProperties.jsonData);
		delete jatos.componentProperties.jsonData;
		
		// Conveniently set document's title
		document.title = jatos.componentProperties.title;
		
		// Depreacated variables - will be removed in future
		jatos.studyData = jatos.studyProperties;
		jatos.studyJsonData = jatos.studyJsonInput;
		jatos.componentData = jatos.componentProperties;
		jatos.componentJsonData = jatos.componentJsonInput;
		
		// Initialising finished
		initialized = true;
		ready();
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
 * Call onJatosLoad() if it already exists and jatos.js is initialised
 */
function ready() {
	if (onJatosLoad && !onJatosLoadCalled && initialized) {
		onJatosLoadCalled = true;
		onJatosLoad();
	}
}

/**
 * Defines callback function that is to be called when jatos.js finished its
 * initialisation.
 */
jatos.onLoad = function(onLoad) {
	onJatosLoad = onLoad;
	ready();
}

/**
 * Defines callback function that is to be called in case jatos.js produces an
 * error, e.g. Ajax errors.
 */
jatos.onError = function(onError) {
	onJatosError = onError;
};

/**
 * Takes a jQuery Ajax response and returns an error message.
 */
function getAjaxErrorMsg(jqxhr) {
	if (jqxhr.statusText == 'timeout') {
		return "JATOS server not responding while trying to get URL "
				+ settings.url;
	} else {
		if (jqxhr.responseText) {
			return jqxhr.statusText + ": " + jqxhr.responseText;
		} else {
			return jqxhr.statusText + ": "
				+ "Error during Ajax call to JATOS server.";
		}
	}
}

/**
 * Little helper function calls either onError or onJatosError if one of them is
 * defined.
 */
function callingOnError(onError, errorMsg) {
	if (onError) {
		onError(errorMsg);
	} else if (onJatosError) {
		onJatosError(errorMsg);
	}
}

/**
 * Posts resultData back to the JATOS server.
 * 
 * @param {Object}
 *            resultData - String to be submitted
 * @param {optional
 *            Function} onSuccess - Function to be called in case of successful
 *            submit
 * @param {optional
 *            Function} onError - Function to be called in case of error
 */
jatos.submitResultData = function(resultData, onSuccess, onError) {
	if (!jQueryExists() || submittingResultData) {
		return;
	}
	submittingResultData = true;
	jatos.jQuery.ajax({
		url : "/publix/" + jatos.studyId + "/" + jatos.componentId
				+ "/resultData",
		data : resultData,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : jatos.httpTimeout,
		success : function(response) {
			submittingResultData = false;
			if (onSuccess) {
				onSuccess(response)
			}
		},
		error : function(err) {
			submittingResultData = false;
			callingOnError(onError, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
}

/**
 * Posts study session data back to the JATOS server. This function is called by
 * all functions that start a new component, so it shouldn't be necessary to
 * call it manually.
 * 
 * @param {Object}
 *            sessionData - Object to be submitted
 * @param {optional
 *            Function} onComplete - Function to be called after this function is
 *            finished
 */
jatos.setStudySessionData = function(sessionData, onComplete) {
	if (!jQueryExists()) {
		return;
	}
	var sessionDataStr;
	try {
		sessionDataStr = JSON.stringify(sessionData);
	} catch (error) {
		if (onJatosError) {
			onJatosError(error);
		}
		if (onComplete) {
			onComplete()
		}
		return;
	}
	if (jatos.studySessionDataFrozen.sessionDataStr == sessionDataStr) {
		// If old and new session data are equal don't post it
		if (onComplete) {
			onComplete()
		}
		return;
	}
	jatos.jQuery.ajax({
		url : "/publix/" + jatos.studyId + "/sessionData",
		data : sessionDataStr,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : jatos.httpTimeout,
		complete : function() {
			if (onComplete) {
				onComplete()
			}
		},
		error: function(err) {
			callingOnError(null, getAjaxErrorMsg(err))
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
}

/**
 * Starts the component with the given ID. You can pass on information to the
 * next component by adding a query string.
 * 
 * @param {Object}
 *            componentId - ID of the component to start
 * @param {optional
 *            Object} queryString - Query string without the initial '?' that
 *            should be added to the URL
 */
jatos.startComponent = function(componentId, queryString) {
	if (startingComponent) {
		return;
	}
	startingComponent = true;
	var onComplete = function() {
		var url = "/publix/" + jatos.studyId + "/" + componentId + "/start";
		if (queryString) {
			url += "?" + queryString;
		}
		window.location.href = url;
	};
	jatos.setStudySessionData(jatos.studySessionData, onComplete);
}

/**
 * Starts the component with the given position (# of component within study).
 * You can pass on information to the next component by adding a query string.
 * 
 * @param {Object}
 *            componentPos - Position of the component to start
 * @param {optional
 *            Object} queryString - Query string without the initial '?' that
 *            should be added to the URL
 */
jatos.startComponentByPos = function(componentPos, queryString) {
	if (startingComponent) {
		return;
	}
	startingComponent = true;
	var onComplete = function() {
		var url = "/publix/" + jatos.studyId + "/component/start?position="
				+ componentPos;
		if (queryString) {
			url += "&" + queryString;
		}
		window.location.href = url;
	}
	jatos.setStudySessionData(jatos.studySessionData, onComplete);
}

/**
 * Starts the next component of this study. The next component is the one with
 * position + 1. You can pass on information to the next component by adding a
 * query string.
 * 
 * @param {optional
 *            Object} queryString - Query string without the initial '?' that
 *            should be added to the URL
 */
jatos.startNextComponent = function(queryString) {
	if (startingComponent) {
		return;
	}
	startingComponent = true;
	var callbackWhenComplete = function() {
		var url = "/publix/" + jatos.studyId + "/nextComponent/start";
		if (queryString) {
			url += "?" + queryString;
		}
		window.location.href = url;
	}
	jatos.setStudySessionData(jatos.studySessionData, callbackWhenComplete);
}

/**
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
jatos.endComponent = function(successful, errorMsg, onSuccess, onError) {
	if (!jQueryExists() || endingComponent) {
		return;
	}
	endingComponent = true;
	var onComplete = function() {
		var url = "/publix/" + jatos.studyId + "/" + jatos.componentId + "/end";
		var fullUrl;
		if (undefined == successful || undefined == errorMsg) {
			fullUrl = url;
		} else if (undefined == successful) {
			fullUrl = url + "?errorMsg=" + errorMsg;
		} else if (undefined == errorMsg) {
			fullUrl = url + "?successful=" + successful;
		} else {
			fullUrl = url + "?successful=" + successful + "&errorMsg="
					+ errorMsg;
		}
		jatos.jQuery.ajax({
			url : fullUrl,
			processData : false,
			type : "GET",
			timeout : jatos.httpTimeout,
			success : function(response) {
				endingComponent = false;
				if (onSuccess) {
					onSuccess(response)
				}
			},
			error : function(err) {
				endingComponent = false;
				callingOnError(onError, getAjaxErrorMsg(err));
			}
		}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
	}
	jatos.setStudySessionData(jatos.studySessionData, onComplete);
}

/**
 * Tries to join a group in the JATOS server and open the group channel WebSocket.
 * 
 * @param {Object} callbacks - Defining callback functions for group
 * 			events. These callbacks functions can be:
 *		onOpen: to be called when the group channel is successfully opened
 *		onClose: to be called when the group channel is closed
 *		onError: to be called when an error during opening of the group channel's
 *			WebSocket occurs 
 * 		onMessage(msg): to be called if a message from another group member is
 *			received. It gets the message as a parameter.
 *		onMemberJoin(memberId): to be called when another member (not the worker
 *			running	this study) joined the group. It gets the group member ID as
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
 *		onComplete: to be called when the group has reached the maximum
 *			amount of members, but it's not necessary that each member opened a
 *			group channel (group state changed from INCOMPLETE to COMPLETE)
 *		onCompleteOpen: to be called when the group has reached the maximum
 *			amount of members and each member opened a group channel
 *		onIncomplete: to be called when a group has not the maximum amount of
 *			members anymore (group state changed from COMPLETE to INCOMPLETE)
 */
jatos.joinGroup = function(callbacks) {
	if (!webSocketSupported) {
		callingOnError(callbacks.onError,
				"This browser does not support WebSockets.");
		return;
	}
	// WebSocket's readyState:
	//		CONNECTING 0 The connection is not yet open.
	//		OPEN       1 The connection is open and ready to communicate.
	//		CLOSING    2 The connection is in the process of closing.
	//		CLOSED     3 The connection is closed or couldn't be opened.
	if (!jatos.jQuery || joiningGroup || !callbacks
			|| (groupChannel && groupChannel.readyState != 3)) {
		return;
	}
	joiningGroup = true;
	
	groupChannel = new WebSocket("ws://" + location.host + 
			"/publix/" + jatos.studyId + "/group/join");
	groupChannel.onmessage = function(event) {
		joiningGroup = false;
		handleGroupMsg(event.data, callbacks);
	};
	groupChannel.onerror = function() {
		joiningGroup = false;
		jatos.groupState = groupState.CLOSE;
		callingOnError(callbacks.onError,
				"Error during opening of group channel");
	};
	groupChannel.onclose = function() {
		joiningGroup = false;
		jatos.groupMemberId = null;
		jatos.groupMembers = [];
		jatos.groupChannels = [];
		jatos.groupState = groupState.CLOSE;
		if (callbacks.onClose) {
			callbacks.onClose();
		}
	};
}

function handleGroupMsg(msg, callbacks) {
	var groupMsg = jatos.jQuery.parseJSON(msg);
	updateGroupVars(groupMsg);
	// Now do the callbacks that were given as parameter to joinGroup
	callGroupCallbacks(groupMsg, callbacks);
}

function updateGroupVars(groupMsg) {
	if (groupMsg.groupId) {
		jatos.groupId = groupMsg.groupId;
		jatos.groupMemberId = jatos.studyResultId;
	}
	if (groupMsg.members) {
		jatos.groupMembers = jatos.jQuery.parseJSON(groupMsg.members);
	}
	if (groupMsg.channels) {
		jatos.groupChannels = jatos.jQuery.parseJSON(groupMsg.channels);
	}
	if (groupMsg.state) {
		priorGroupState = jatos.groupState;
		if (groupMsg.state == "COMPLETE") {
			jatos.groupState = groupState.COMPLETE;
		} else if (groupMsg.state == "INCOMPLETE") {
			jatos.groupState = groupState.INCOMPLETE;
		}
	}
}

function callGroupCallbacks(groupMsg, callbacks) {
	// onOpen and onMemberOpen
	// Someone opened a group channel; distinguish between the worker running
	// this study and others
	if (groupMsg.opened) {
		if (groupMsg.opened == jatos.studyResultId && callbacks.onOpen) {
			callbacks.onOpen(groupMsg.opened);
		} else if (callbacks.onMemberOpen) {
			callbacks.onMemberOpen(groupMsg.opened);
		}
	}
	// onMemberClose
	// Some member closed its group channel
	if (groupMsg.closed && groupMsg.closed != jatos.studyResultId
			&& callbacks.onMemberClose) {
		callbacks.onMemberClose(groupMsg.closed);
	}
	// onMemberJoin
	// Some member joined (it should not happen, but check the group member ID
	// (aka study result ID) is not the one of the joined member)
	if (groupMsg.joined && groupMsg.joined != jatos.studyResultId
			&& callbacks.onMemberJoin) {
		callbacks.onMemberJoin(groupMsg.joined);
	}
	// onMemberLeave
	// Some member left (it should not happen, but check the group member ID
	// (aka study result ID) is not the one of the left member)
	if (groupMsg.left && groupMsg.left != jatos.studyResultId
			 && callbacks.onMemberLeave) {
		callbacks.onMemberLeave(groupMsg.left);
	}
	// onComplete
	// Group is full now - the state changed from INCOMPLETE to COMPLETE.
	// It's not necessary at this point that every member opened its channel.
	// It's not important which event triggers 'joined' or 'opened'.
	if (groupMsg.state && (groupMsg.joined || groupMsg.opened)
			&& jatos.isGroupComplete()
			&& priorGroupState == groupState.INCOMPLETE
			&& callbacks.onComplete) {
		callbacks.onComplete();
	}
	// onIncomplete
	// Group is not full anymore - the state changed from INCOMPLETE to
	// COMPLETE. It's not important which event triggers 'left' or 'closed'.
	if (groupMsg.state && (groupMsg.left || groupMsg.closed)
			&& jatos.isGroupIncomplete()
			&& priorGroupState == groupState.COMPLETE
			&& callbacks.onIncomplete) {
		callbacks.onIncomplete();
	}
	// onCompleteOpen
	// The group is complete and every member has an open channel
	if (groupMsg.state && groupMsg.opened && jatos.isGroupCompleteOpen()
			&& callbacks.onCompleteOpen) {
		callbacks.onCompleteOpen();
	}
	// onMessage
	// This callback must be the last, because all jatos.js membershop vars have
	// to be up to date 
	if (groupMsg.msg && callbacks.onMessage) {
		callbacks.onMessage(groupMsg.msg);
	}
}

/**
 * @return {Boolean} True if the group has reached the maximum amount of
 *         members.
 */
jatos.isGroupComplete = function() {
	return jatos.groupState == groupState.COMPLETE;
}

/**
 * @return {Boolean} True if the group has reached the maximum amount of
 *         members and all members have an open group channel.
 */
jatos.isGroupCompleteOpen = function() {
	return jatos.isGroupComplete() && jatos.groupMembers && jatos.groupChannels
			&& jatos.groupMembers.length == jatos.groupChannels.length;
}

/**
 * @return {Boolean} True if the group has not reached the maximum amount of
 *         members.
 */
jatos.isGroupIncomplete = function() {
	return jatos.groupState == groupState.INCOMPLETE;
}

/**
 * Sends a message to all group members.
 * 
 * @param {String} msg - Message to send
 */
jatos.sendGroupMsg = function(msg) {
	if (groupChannel) {
		var msgObj = {};
		msgObj["msg"] = msg;
		groupChannel.send(JSON.stringify(msgObj));
	}
}

/**
 * Sends a message to the group member with the given member ID only.
 * 
 * @param {String} recipient - Recipient's group member ID
 * @param {String} msg - Message to send to the recipient
 */
jatos.sendMsgTo = function(recipient, msg) {
	if (groupChannel) {
		var msgObj = {};
		msgObj["recipient"] = recipient;
		msgObj["msg"] = msg;
		groupChannel.send(JSON.stringify(msgObj));
	}
}

/**
 * Tries to leave the group it has previously joined. The group channel
 * WebSocket is not closed in the function - it's closed from the JATOS' side.
 * 
 * @param {optional
 *            Function} onSuccess - Function to be called after the group is
 *            left.
 * @param {optional
 *            Function} onError - Function to be called in case of error.
 */
jatos.leaveGroup = function(onSuccess, onError) {
	if (!jQueryExists() || leavingGroup) {
		return;
	}
	leavingGroup = true;
	
	jatos.jQuery.ajax({
		url : "/publix/" + jatos.studyId + "/group/leave",
		processData : false,
		type : "GET",
		timeout : jatos.httpTimeout,
		success : function(response) {
			leavingGroup = false;
			if (onSuccess) {
				onSuccess()
			}
		},
		error : function(err) {
			leavingGroup = false;
			callingOnError(onError, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
}

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
 */
jatos.abortStudyAjax = function(message, onSuccess, onError) {
	if (!jQueryExists() || abortingComponent) {
		return;
	}
	abortingComponent = true;
	var url = "/publix/" + jatos.studyId + "/abort";
	var fullUrl;
	if (undefined == message) {
		fullUrl = url;
	} else {
		fullUrl = url + "?message=" + message;
	}
	jatos.jQuery.ajax({
		url : fullUrl,
		processData : false,
		type : "GET",
		timeout : jatos.httpTimeout,
		success : function(response) {
			abortingComponent = false;
			if (onSuccess) {
				onSuccess(response)
			}
		},
		error : function(err) {
			abortingComponent = false;
			callingOnError(onError, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
}

/**
 * Aborts study. All previously submitted data will be deleted.
 * 
 * @param {optional
 *            String} message - Message that should be logged
 */
jatos.abortStudy = function(message) {
	if (abortingComponent) {
		return;
	}
	abortingComponent = true;
	var url = "/publix/" + jatos.studyId + "/abort";
	if (undefined == message) {
		window.location.href = url;
	} else {
		window.location.href = url + "?message=" + message;
	}
}

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
 */
jatos.endStudyAjax = function(successful, errorMsg, onSuccess, onError) {
	if (!jQueryExists() || endingComponent) {
		return;
	}
	endingComponent = true;
	var url = "/publix/" + jatos.studyId + "/end";
	var fullUrl;
	if (undefined == successful || undefined == errorMsg) {
		fullUrl = url;
	} else if (undefined == successful) {
		fullUrl = url + "?errorMsg=" + errorMsg;
	} else if (undefined == errorMsg) {
		fullUrl = url + "?successful=" + successful;
	} else {
		fullUrl = url + "?successful=" + successful + "&errorMsg=" + errorMsg;
	}
	jatos.jQuery.ajax({
		url : fullUrl,
		processData : false,
		type : "GET",
		timeout : jatos.httpTimeout,
		success : function(response) {
			endingComponent = false;
			if (onSuccess) {
				onSuccess(response)
			}
		},
		error : function(err) {
			endingComponent = false;
			callingOnError(onError, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
}

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
jatos.endStudy = function(successful, errorMsg) {
	if (endingComponent) {
		return;
	}
	endingComponent = true;
	var url = "/publix/" + jatos.studyId + "/end";
	if (undefined == successful || undefined == errorMsg) {
		window.location.href = url;
	} else if (undefined == successful) {
		window.location.href = url + "?errorMsg=" + errorMsg;
	} else if (undefined == errorMsg) {
		window.location.href = url + "?successful=" + successful;
	} else {
		window.location.href = url + "?successful=" + successful + "&errorMsg="
				+ errorMsg;
	}
}

/**
 * Logs an error within the JATOS.
 */
jatos.logError = function(logErrorMsg) {
	if (!jQueryExists()) {
		return;
	}
	jatos.jQuery.ajax({
		url : "/publix/" + jatos.studyId + "/" + jatos.componentId
				+ "/log",
		data : logErrorMsg,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : jatos.httpTimeout,
		error : function(err) {
			callingOnError(null, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
}

/**
 * Convenience function that adds all JATOS IDs (study ID, component ID, worker
 * ID, study result ID, component result ID) to the given object.
 * 
 * @param {Object}
 *            obj - Object to which the IDs will be added
 */
jatos.addJatosIds = function(obj) {
	obj.studyId = jatos.studyId;
	obj.groupId = jatos.groupId;
	obj.componentId = jatos.componentId;
	obj.workerId = jatos.workerId;
	obj.studyResultId = jatos.studyResultId;
	obj.componentResultId = jatos.componentResultId;
}

})();

