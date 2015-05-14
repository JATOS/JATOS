/**
 * jatos.js (JATOS JavaScript Library)
 * http://jatos.org
 * Author Kristian Lange 2014 - 2015
 * Licensed under Apache License 2.0
 * 
 * Plugin: jquery.ajax-retry
 * https://github.com/johnkpaul/jquery-ajax-retry
 * Copyright (c) 2012 John Paul
 * Licensed under the MIT license.
 */

var jatos = {};

// Encapsulate the whole library so nothing unintentional gets out (e.g. jQuery
// or functions or variables)
(function() {
	
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
 * State booleans. If true jatos.js is in this state.
 */
var initialized = false;
var onLoadCallbackCalled = false;
var startingComponent = false;
var endingComponent = false;
var submittingResultData = false;
var abortingComponent = false;

/**
 * Callback function defined via jatos.onLoad.
 */
var onLoadCallback;
/**
 * Callback function defined via jatos.onError.
 */
var onErrorCallback;

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
function getScript(url, success) {
	var script = document.createElement('script');
	script.src = url;
	var head = document.getElementsByTagName('head')[0], done = false;
	script.onload = script.onreadystatechange = function() {
		if (!done && (!this.readyState || this.readyState == 'loaded'
				|| this.readyState == 'complete')) {
			done = true;
			success();
			script.onload = script.onreadystatechange = null;
			head.removeChild(script);
		}
	}
	head.appendChild(script);
}

/**
 * Load and run additional JS.
 */
function loadScripts(successCallback) {
	if (!jQueryExists()) {
		return;
	}
	// Plugin to retry ajax calls 
	jatos.jQuery.ajax({
		url: "/assets/javascripts/jquery.ajax-retry.min.js",
		dataType: "script",
		cache: true
	}).done(successCallback);
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
			success : setInitData
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
			if (onErrorCallback) {
				onErrorCallback(e);
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

function jQueryExists() {
	if (!jatos.jQuery) {
		if (onErrorCallback) {
			onErrorCallback("jatos.js' jQuery not (yet?) loaded");
		}
		return false;
	}
	return true;
}

/**
 * Call onLoadCallback() if it already exists and jatos.js is initialised
 */
function ready() {
	if (onErrorCallback) {
		// If we have an error callback also set the jQuery ajax error callback
		setJQueryAjaxError(onErrorCallback);
	}
	if (onLoadCallback && !onLoadCallbackCalled && initialized) {
		onLoadCallbackCalled = true;
		onLoadCallback();
	}
}

/**
 * Defines callback function that is to be called when jatos.js finished its
 * initialisation.
 */
jatos.onLoad = function(callback) {
	onLoadCallback = callback;
	ready();
}

/**
 * Defines callback function that is to be called in case jatos.js produces an
 * error.
 */
jatos.onError = function(callback) {
	onErrorCallback = callback;
	// If we have an error callback also set the jQuery ajax error callback
	setJQueryAjaxError(onErrorCallback);
};

/**
 * Define what jQuery should do if an ajax error happens. This way we can
 * define it once without writing it on every ajax call.
 */
function setJQueryAjaxError(callback) {
	if (!jatos.jQuery || !callback) {
		return;
	}
	jatos.jQuery(document).ajaxError(function(event, jqxhr, settings, thrownError) {
		if (jqxhr.statusText == 'timeout') {
			callback("JATOS server not responding while trying to get URL "
					+ settings.url);
		} else {
			callback(jqxhr.responseText);
		}
	});
}

/**
 * Posts resultData back to the JATOS server.
 * 
 * @param {Object}
 *            resultData - String to be submitted
 * @param {optional
 *            Function} success - Function to be called in case of successful
 *            submit
 * @param {optional
 *            Function} error - Function to be called in case of error
 */
jatos.submitResultData = function(resultData, success, error) {
	if (!jQueryExists() || submittingResultData) {
		return;
	}
	submittingResultData = true;
	jatos.jQuery.ajax({
		url : "/publix/" + jatos.studyId + "/" + jatos.componentId
				+ "/submitResultData",
		data : resultData,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : jatos.httpTimeout,
		success : function(response) {
			submittingResultData = false;
			if (success) {
				success(response)
			}
		},
		error : function(err) {
			submittingResultData = false;
			if (error) {
				error(err)
			}
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
 *            Function} complete - Function to be called after this function is
 *            finished
 */
jatos.setStudySessionData = function(sessionData, complete) {
	if (!jQueryExists()) {
		return;
	}
	var sessionDataStr;
	try {
		sessionDataStr = JSON.stringify(sessionData);
	} catch (error) {
		if (onErrorCallback) {
			onErrorCallback(error);
		}
		if (complete) {
			complete()
		}
		return;
	}
	if (jatos.studySessionDataFrozen.sessionDataStr == sessionDataStr) {
		// If old and new session data are equal don't post it
		if (complete) {
			complete()
		}
		return;
	}
	jatos.jQuery.ajax({
		url : "/publix/" + jatos.studyId + "/setSessionData",
		data : sessionDataStr,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : jatos.httpTimeout,
		complete : function() {
			if (complete) {
				complete()
			}
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
	var callbackWhenComplete = function() {
		var url = "/publix/" + jatos.studyId + "/" + componentId + "/start";
		if (queryString) {
			url += "?" + queryString;
		}
		window.location.href = url;
	};
	jatos.setStudySessionData(jatos.studySessionData, callbackWhenComplete);
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
	var callbackWhenComplete = function() {
		var url = "/publix/" + jatos.studyId + "/startComponent?position="
				+ componentPos;
		if (queryString) {
			url += "&" + queryString;
		}
		window.location.href = url;
	}
	jatos.setStudySessionData(jatos.studySessionData, callbackWhenComplete);
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
		var url = "/publix/" + jatos.studyId + "/startNextComponent";
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
 *            Function} success - Function to be called in case of successful
 *            submit
 * @param {optional
 *            Function} error - Function to be called in case of error
 */
jatos.endComponent = function(successful, errorMsg, success, error) {
	if (!jQueryExists() || endingComponent) {
		return;
	}
	endingComponent = true;
	var callbackWhenComplete = function() {
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
				if (success) {
					success(response)
				}
			},
			error : function(err) {
				endingComponent = false;
				if (error) {
					error(err)
				}
			}
		}).retry({times : jatos.httpRetry, timeout : jatos.httpRetryWait});
	}
	jatos.setStudySessionData(jatos.studySessionData, callbackWhenComplete);
}

/**
 * Aborts study. All previously submitted data will be deleted.
 * 
 * @param {optional
 *            String} message - Message that should be logged
 * @param {optional
 *            Function} success - Function to be called in case of successful
 *            submit
 * @param {optional
 *            Function} error - Function to be called in case of error
 */
jatos.abortStudyAjax = function(message, success, error) {
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
			if (success) {
				success(response)
			}
		},
		error : function(err) {
			abortingComponent = false;
			if (error) {
				error(err)
			}
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
 *            Function} success - Function to be called in case of successful
 *            submit
 * @param {optional
 *            Function} error - Function to be called in case of error
 */
jatos.endStudyAjax = function(successful, errorMsg, success, error) {
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
			if (success) {
				success(response)
			}
		},
		error : function(err) {
			endingComponent = false;
			if (error) {
				error(err)
			}
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
				+ "/logError",
		data : logErrorMsg,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : jatos.httpTimeout
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
	obj.componentId = jatos.componentId;
	obj.workerId = jatos.workerId;
	obj.studyResultId = jatos.studyResultId;
	obj.componentResultId = jatos.componentResultId;
}

})();

