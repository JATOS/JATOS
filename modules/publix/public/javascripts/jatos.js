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
	
/**
 * jatos.js version
 */
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
 * Member IDs of the current members of the group result .
 */
jatos.groupMembers = [];
/**
 * Member IDs of the currently open group channels. Don't confuse with internal
 * groupChannel variable.
 */
jatos.groupChannels = [];
/**
 * Group session data shared in between the group. 
 */
jatos.groupSessionData = {};
/**
 * Version of the current group session data. This is used to prevent concurrent
 * changes of the data.
 */
var groupSessionVersion;
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
jatos.jQuery = {};
getScript('/public/lib/jatos-publix/javascripts/jquery-1.11.1.min.js', function() {
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
	// Plugin to retry ajax calls 
	jatos.jQuery.ajax({
		url: "/public/lib/jatos-publix/javascripts/jquery.ajax-retry.min.js",
		dataType: "script",
		cache: true,
		error: function(err) {
			callingOnError(null, getAjaxErrorMsg(err));
		}
	}).done(onSuccess);
}

/**
 * Initialising jatos.js
 */
function initJatos() {
	
	if (!jQueryExists()) {
		return;
	}
	readIdCookie();
	getInitData();

	/**
	 * Reads JATOS' ID cookie and stores all key-value pairs into jatos scope.
	 */
	function readIdCookie() {
		var nameEQ = encodeURI("JATOS_IDS") + "=";
		var ca = document.cookie.split(';');
		for (var i = 0; i < ca.length; i++) {
			var c = ca[i];
			while (c.charAt(0) === ' ') {
				c = c.substring(1, c.length);
			}
			if (c.indexOf(nameEQ) === 0) {
				var cookieStr = decodeURI(c.substring(nameEQ.length + 1,
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
				callingOnError(null, getAjaxErrorMsg(err));
			}
		}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
	}

	/**
	 * Puts the ajax response into the different jatos variables.
	 */
	function setInitData(initData) {
		// Session data
		try {
			jatos.studySessionData = jatos.jQuery.parseJSON(initData.studySessionData);
		} catch (e) {
			jatos.studySessionData = "Error parsing JSON";
			if (onJatosError) {
				onJatosError(e);
			}
		}
		jatos.studySessionDataFrozen = Object.freeze({
			"studySessionDataStr" : initData.studySessionData
		});
		
		// Study properties
		jatos.studyProperties = initData.studyProperties;
		if (jatos.studyProperties.jsonData) {
			jatos.studyJsonInput = jatos.jQuery
					.parseJSON(jatos.studyProperties.jsonData);
		} else {
			jatos.studyJsonInput = {};
		}
		delete jatos.studyProperties.jsonData;
		
		// Batch properties
		jatos.batchProperties = initData.batchProperties;
		
		// Study's component list and study length
		jatos.componentList = initData.componentList;
		jatos.studyLength = initData.componentList.length;
		
		// Component properties
		jatos.componentProperties = initData.componentProperties;
		if (jatos.componentProperties.jsonData) {
			jatos.componentJsonInput = jatos.jQuery
					.parseJSON(jatos.componentProperties.jsonData);
		} else {
			jatos.componentJsonInput = {};
		}
		delete jatos.componentProperties.jsonData;
		
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
};

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
		return "JATOS server not responding while trying to get URL";
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
				onSuccess(response);
			}
		},
		error : function(err) {
			submittingResultData = false;
			callingOnError(onError, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
};

/**
 * Posts study session data back to the JATOS server. This function is called by
 * all functions that start a new component, so it shouldn't be necessary to
 * call it manually.
 * 
 * @param {Object}
 *            studySessionData - Object to be submitted
 * @param {optional
 *            Function} onComplete - Function to be called after this function is
 *            finished
 */
jatos.setStudySessionData = function(studySessionData, onComplete) {
	if (!jQueryExists()) {
		return;
	}
	var studySessionDataStr;
	try {
		studySessionDataStr = JSON.stringify(studySessionData);
	} catch (error) {
		if (onJatosError) {
			onJatosError(error);
		}
		if (onComplete) {
			onComplete();
		}
		return;
	}
	if (jatos.studySessionDataFrozen.studySessionDataStr == studySessionDataStr) {
		// If old and new session data are equal don't post it
		if (onComplete) {
			onComplete();
		}
		return;
	}
	jatos.jQuery.ajax({
		url : "/publix/" + jatos.studyId + "/studySessionData",
		data : studySessionDataStr,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : jatos.httpTimeout,
		complete : function() {
			if (onComplete) {
				onComplete();
			}
		},
		error: function(err) {
			callingOnError(null, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
};

/**
 * Starts the component with the given ID.
 * 
 * @param {Object}
 *            componentId - ID of the component to start
 */
jatos.startComponent = function(componentId) {
	if (startingComponent) {
		return;
	}
	startingComponent = true;
	var onComplete = function() {
		window.location.href = "/publix/" + jatos.studyId + "/" + componentId
				+ "/start";
	};
	jatos.setStudySessionData(jatos.studySessionData, onComplete);
};

/**
 * Starts the component with the given position (# of component within study).
 * 
 * @param {Object}
 *            componentPos - Position of the component to start
 */
jatos.startComponentByPos = function(componentPos) {
	if (startingComponent) {
		return;
	}
	startingComponent = true;
	var onComplete = function() {
		window.location.href = "/publix/" + jatos.studyId
				+ "/component/start?position=" + componentPos;
	};
	jatos.setStudySessionData(jatos.studySessionData, onComplete);
};

/**
 * Starts the next component of this study. The next component is the one with
 * position + 1.
 */
jatos.startNextComponent = function() {
	if (startingComponent) {
		return;
	}
	startingComponent = true;
	var callbackWhenComplete = function() {
		window.location.href = "/publix/" + jatos.studyId
				+ "/nextComponent/start";
	};
	jatos.setStudySessionData(jatos.studySessionData, callbackWhenComplete);
};

/**
 * Starts the last component of this study or if it's inactive the component
 * with the highest position that is active.
 */
jatos.startLastComponent = function() {
	for (var i = jatos.componentList.length - 1; i >= 0; i--) {
	    if (jatos.componentList[i].active) {
	    	jatos.startComponentByPos(i + 1);
	    	break;
	    }
	}
};

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
		if (undefined === successful || undefined === errorMsg) {
			fullUrl = url;
		} else if (undefined === successful) {
			fullUrl = url + "?errorMsg=" + errorMsg;
		} else if (undefined === errorMsg) {
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
					onSuccess(response);
				}
			},
			error : function(err) {
				endingComponent = false;
				callingOnError(onError, getAjaxErrorMsg(err));
			}
		}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
	};
	jatos.setStudySessionData(jatos.studySessionData, onComplete);
};

/**
 * Tries to join a group (actually a GroupResult) in the JATOS server and open
 * the group channel WebSocket.
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
		callingOnError(callbacks.onError,
				"Error during opening of group channel");
	};
	groupChannel.onclose = function() {
		joiningGroup = false;
		jatos.groupResultId = null;
		jatos.groupMemberId = null;
		jatos.groupMembers = [];
		jatos.groupChannels = [];
		jatos.groupSessionData = null;
		groupSessionVersion = null;
		if (callbacks.onClose) {
			callbacks.onClose();
		}
	};
};

function handleGroupMsg(msg, callbacks) {
	var groupMsg = jatos.jQuery.parseJSON(msg);
	updateGroupVars(groupMsg);
	// Now do the callbacks that were given as parameter to joinGroup
	callGroupActionCallbacks(groupMsg, callbacks);
	// Handle onMessage callback
	if (groupMsg.msg && callbacks.onMessage) {
		callbacks.onMessage(groupMsg.msg);
	}
}

function updateGroupVars(groupMsg) {
	if (groupMsg.groupResultId) {
		jatos.groupResultId = groupMsg.groupResultId;
		// Group member ID is equal to study result ID
		jatos.groupMemberId = jatos.studyResultId;
	}
	try {
		if (groupMsg.members) {
			jatos.groupMembers = jatos.jQuery.parseJSON(groupMsg.members);
		}
		if (groupMsg.channels) {
			jatos.groupChannels = jatos.jQuery.parseJSON(groupMsg.channels);
		}
		if (groupMsg.groupSessionData) {
			jatos.groupSessionData = jatos.jQuery.parseJSON(groupMsg.groupSessionData);
		}
		if (groupMsg.groupSessionVersion) {
			groupSessionVersion = jatos.jQuery.parseJSON(groupMsg.groupSessionVersion);
		}
	} catch (error) {
		if (onJatosError) {
			onJatosError(error);
		}
	}
}

function callGroupActionCallbacks(groupMsg, callbacks) {
	if (!groupMsg.action) {
		return;
	}
	// onOpen and onMemberOpen
	// Someone opened a group channel; distinguish between the worker running
	// this study and others
	if (groupMsg.action == "opened") {
		if (groupMsg.memberId == jatos.groupMemberId && callbacks.onOpen) {
			callbacks.onOpen(groupMsg.memberId);
		} else if (groupMsg.memberId != jatos.groupMemberId
				&& callbacks.onMemberOpen) {
			callbacks.onMemberOpen(groupMsg.memberId);
		}
	}
	// onMemberClose
	// Some member closed its group channel
	// (onClose callback function is handled during groupChannel.onclose)
	if (groupMsg.action == "closed" && groupMsg.memberId != jatos.groupMemberId
			&& callbacks.onMemberClose) {
		callbacks.onMemberClose(groupMsg.memberId);
	}
	// onMemberJoin
	// Some member joined (it should not happen, but check the group member ID
	// (aka study result ID) is not the one of the joined member)
	if (groupMsg.action == "joined" && groupMsg.memberId != jatos.groupMemberId
			&& callbacks.onMemberJoin) {
		callbacks.onMemberJoin(groupMsg.memberId);
	}
	// onMemberLeave
	// Some member left (it should not happen, but check the group member ID
	// (aka study result ID) is not the one of the left member)
	if (groupMsg.action == "left" && groupMsg.memberId != jatos.groupMemberId
			 && callbacks.onMemberLeave) {
		callbacks.onMemberLeave(groupMsg.memberId);
	}
	
	if (groupMsg.action == "update" && callbacks.onUpdate) {
		callbacks.onUpdate(jatos.groupSessionData);
	}
}

/**
 * Sends the group session data via the group channel WebSocket to the JATOS
 * server where it's stored and broadcasted to all members of this group.
 * 
 * @param {Object} groupSessionData - An object in JSON
 */
jatos.setGroupSessionData = function(groupSessionData) {
	if (!jQueryExists()) {
		return;
	}
	if (groupChannel && groupChannel.readyState == 1) {
		var msgObj = {};
		msgObj.groupSessionData = groupSessionData;
		msgObj.groupSessionVersion = groupSessionVersion;
		try {
			groupChannel.send(JSON.stringify(msgObj));
		} catch (error) {
			if (onJatosError) {
				onJatosError(error);
			}
		}
	}
};

/**
 * @return {Boolean} True if the group has reached the minimum amount of active
 *         members. It's not necessary that each member has an open group
 *         channel.
 */
jatos.isMinActiveMemberReached = function() {
	return jatos.groupMembers.length >= jatos.batchProperties.minActiveMembers;
};

/**
 * @return {Boolean} True if the group has reached the minimum amount of active
 *         members and each member has an open group channel.
 */
jatos.isMinActiveMemberOpen = function() {
	return jatos.groupChannels.length >= jatos.batchProperties.minActiveMembers;
};

/**
 * @return {Boolean} True if the group has reached the maximum amount of active
 *         members. It's not necessary that each member has an open group
 *         channel.
 */
jatos.isMaxActiveMemberReached = function() {
	if (jatos.batchProperties.maxActiveMembers == null) {
		return false;
	} else {
		return jatos.groupMembers.length >= jatos.batchProperties.maxActiveMembers;
	}
};

/**
 * @return {Boolean} True if the group has reached the maximum amount of active
 *         members and each member has an open group channel.
 */
jatos.isMaxActiveMemberOpen = function() {
	if (jatos.batchProperties.maxActiveMembers == null) {
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
jatos.isGroupOpen = function() {
	if (groupChannel && groupChannel.readyState == 1) {
		return jatos.groupMembers.length == jatos.groupChannels.length;
	} else {
		return false;
	}
};

/**
 * Sends a message to all group members if group channel is open.
 * 
 * @param {String} msg - Message to send
 */
jatos.sendGroupMsg = function(msg) {
	if (groupChannel && groupChannel.readyState == 1) {
		var msgObj = {};
		msgObj.msg = msg;
		groupChannel.send(JSON.stringify(msgObj));
	}
};

/**
 * Sends a message to the group member with the given member ID only  if group
 * channel is open.
 * 
 * @param {String} recipient - Recipient's group member ID
 * @param {String} msg - Message to send to the recipient
 */
jatos.sendMsgTo = function(recipient, msg) {
	if (groupChannel && groupChannel.readyState == 1) {
		var msgObj = {};
		msgObj.recipient = recipient;
		msgObj.msg = msg;
		groupChannel.send(JSON.stringify(msgObj));
	}
};

/**
 * Tries to leave the group (actually a GroupResult) it has previously joined.
 * The group channel WebSocket is not closed in the function - it's closed from
 * the JATOS' side.
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
				onSuccess(response);
			}
		},
		error : function(err) {
			leavingGroup = false;
			callingOnError(onError, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
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
 */
jatos.abortStudyAjax = function(message, onSuccess, onError) {
	if (!jQueryExists() || abortingComponent) {
		return;
	}
	abortingComponent = true;
	var url = "/publix/" + jatos.studyId + "/abort";
	var fullUrl;
	if (undefined === message) {
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
				onSuccess(response);
			}
		},
		error : function(err) {
			abortingComponent = false;
			callingOnError(onError, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
};

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
	if (undefined === message) {
		window.location.href = url;
	} else {
		window.location.href = url + "?message=" + message;
	}
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
 */
jatos.endStudyAjax = function(successful, errorMsg, onSuccess, onError) {
	if (!jQueryExists() || endingComponent) {
		return;
	}
	endingComponent = true;
	var url = "/publix/" + jatos.studyId + "/end";
	var fullUrl;
	if (undefined === successful || undefined === errorMsg) {
		fullUrl = url;
	} else if (undefined === successful) {
		fullUrl = url + "?errorMsg=" + errorMsg;
	} else if (undefined === errorMsg) {
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
				onSuccess(response);
			}
		},
		error : function(err) {
			endingComponent = false;
			callingOnError(onError, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
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
jatos.endStudy = function(successful, errorMsg) {
	if (endingComponent) {
		return;
	}
	endingComponent = true;
	var url = "/publix/" + jatos.studyId + "/end";
	if (undefined === successful || undefined === errorMsg) {
		window.location.href = url;
	} else if (undefined === successful) {
		window.location.href = url + "?errorMsg=" + errorMsg;
	} else if (undefined === errorMsg) {
		window.location.href = url + "?successful=" + successful;
	} else {
		window.location.href = url + "?successful=" + successful + "&errorMsg="
				+ errorMsg;
	}
};

/**
 * Logs a message within the JATOS log on the server side.
 */
jatos.logError = function(logErrorMsg) {
	jatos.log(logErrorMsg);
};

/**
 * Logs a message within the JATOS log on the server side.
 */
jatos.log = function(logMsg) {
	if (!jQueryExists()) {
		return;
	}
	jatos.jQuery.ajax({
		url : "/publix/" + jatos.studyId + "/" + jatos.componentId
				+ "/log",
		data : logMsg,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : jatos.httpTimeout,
		error : function(err) {
			callingOnError(null, getAjaxErrorMsg(err));
		}
	}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
};

/**
 * Convenience function that adds all JATOS IDs (study ID, component ID, worker
 * ID, study result ID, component result ID) to the given object.
 * 
 * @param {Object}
 *            obj - Object to which the IDs will be added
 */
jatos.addJatosIds = function(obj) {
	obj.studyId = jatos.studyId;
	obj.groupResultId = jatos.groupResultId;
	obj.componentId = jatos.componentId;
	obj.workerId = jatos.workerId;
	obj.studyResultId = jatos.studyResultId;
	obj.componentResultId = jatos.componentResultId;
};

})();

