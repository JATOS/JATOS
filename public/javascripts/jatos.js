/**
 * jatos.js (JATOS JavaScript Library)
 * http://jatos.org
 * Author Kristian Lange 2014
 * Licensed under Apache License 2.0
 * 
 * Plugin: jquery.ajax-retry
 * https://github.com/johnkpaul/jquery-ajax-retry
 * Copyright (c) 2012 John Paul
 * Licensed under the MIT license.
 */

/**
 * How long should JATOS wait until to retry the HTTP call. Warning: There is a
 * general problem with HTTP retries. In many cases a JATOS regards a second
 * call of the same function as a reload of the component. A reload of a
 * component is often forbidden and leads to failed finish of the study.
 * Therefore I put the HTTP timeout time to 60 secs. If there is now answer
 * within this time I assume the call never reached the server and it's our last
 * hope to continue the study is to retry the call.
 */
var httpTimeout = 60000;
/**
 * How many times should jatos.js retry to send a failed HTTP call.
 */
var httpRetry = 5;
/**
 * How long should jatos.js wait between a failed HTTP call and a retry.
 */
var httpRetryWait = 1000;

var submittingResultData = false;
var startingComponent = false;
var endingComponent = false;
var abortingComponent = false;

var jatos = {};
var onErrorCallback;
var onLoadCallback;

window.addEventListener('load', loadScripts(initJatos));

/**
 * Defines callback function that is to be called when jatos.js is finished its
 * initialisation.
 */
jatos.onLoad = function(callback) {
	onLoadCallback = callback;
}

/**
 * Defines callback function that is to be called in case jatos.js produces an
 * error.
 */
jatos.onError = function(callback) {
	onErrorCallback = callback;
	$(document).ajaxError(function(event, jqxhr, settings, thrownError) {
		if (jqxhr.statusText == 'timeout') {
			onErrorCallback("JATOS server not responding while trying to get URL " + settings.url);
		} else {
			onErrorCallback(jqxhr.responseText);
		}
	});
};

/**
 * Load and run additional JS.
 */
function loadScripts(successCallback) {
	// Plugin to retry ajax calls 
	$.ajax({
		url: "/assets/javascripts/jquery.ajax-retry.min.js",
		dataType: "script",
		cache: true
	}).done(successCallback);
}

/**
 * Initialising jatos.js.
 */
function initJatos() {
	var studyPropertiesReady = false;
	var studySessionDataReady = false;
	var componentPropertiesReady = false;

	/**
	 * Reads JATOS' ID cookie and stores all key-value pairs into jatos scope.
	 * This function is automatically called after the page is loaded, so it's
	 * not necessary to call it again.
	 */
	readIdCookie = function() {
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
	}

	/**
	 * Checks whether study's properties, study session data, and component's
	 * properties are finished loading
	 */
	ready = function() {
		if (studyPropertiesReady && studySessionDataReady
				&& componentPropertiesReady) {
			if (onLoadCallback) {
				onLoadCallback();
			}
		}
	}

	/**
	 * Gets the study's properties from the JATOS server and stores them in
	 * jatos.studyProperties and jatos.studyJsonInput (just the JSON input data
	 * of the properties).
	 */
	getStudyProperties = function() {
		$.ajax({
			url : "/publix/" + jatos.studyId + "/getProperties",
			type : "GET",
			dataType : 'json',
			timeout : httpTimeout,
			success : function(response) {
				// Legacy names TODO remove
				jatos.studyData = response;
				jatos.studyJsonData = $.parseJSON(jatos.studyData.jsonData);
				delete jatos.studyData.jsonData;
				// New names
				jatos.studyProperties = jatos.studyData;
				jatos.studyJsonInput = jatos.studyJsonData;
				studyPropertiesReady = true;
				ready();
			}
		}).retry({times : httpRetry, timeout : httpRetryWait});
	};

	/**
	 * Gets the study's session data from the JATOS server and stores them in
	 * jatos.studySessionData.
	 */
	getStudySessionData = function() {
		$.ajax({
			url : "/publix/" + jatos.studyId + "/getSessionData",
			type : "GET",
			dataType : 'text',
			timeout : httpTimeout,
			success : function(response) {
				try {
					jatos.studySessionData = $.parseJSON(response);
				} catch (e) {
					jatos.studySessionData = "Error parsing JSON";
					if (onErrorCallback) {
						onErrorCallback(e);
					}
				}
				jatos.studySessionDataFrozen = Object.freeze({
					"sessionDataStr" : response
				});
				studySessionDataReady = true;
				ready();
			}
		}).retry({times : httpRetry, timeout : httpRetryWait});
	}

	/**
	 * Gets the component's properties from the JATOS server and stores them in
	 * jatos.componentProperties and jatos.componentJsonInput (just the JSON
	 * input data of the properties).
	 */
	getComponentProperties = function() {
		$.ajax({
			url : "/publix/" + jatos.studyId + "/" + jatos.componentId
					+ "/getProperties",
			type : "GET",
			dataType : 'json',
			timeout : httpTimeout,
			success : function(response) {
				// Legacy names
				jatos.componentData = response;
				jatos.componentJsonData = $
						.parseJSON(jatos.componentData.jsonData);
				delete jatos.componentData.jsonData;
				// New names
				jatos.componentProperties = jatos.componentData;
				jatos.componentJsonInput = jatos.componentJsonData;
				document.title = jatos.componentData.title;
				componentPropertiesReady = true;
				ready();
			}
		}).retry({times : httpRetry, timeout : httpRetryWait});
	}

	readIdCookie();
	getStudyProperties();
	getStudySessionData();
	getComponentProperties();
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
	if (submittingResultData){
		return;
	}
	submittingResultData = true;
	$.ajax({
		url : "/publix/" + jatos.studyId + "/" + jatos.componentId
				+ "/submitResultData",
		data : resultData,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : httpTimeout,
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
	}).retry({times : httpRetry, timeout : httpRetryWait});
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
	$.ajax({
		url : "/publix/" + jatos.studyId + "/setSessionData",
		data : sessionDataStr,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : httpTimeout,
		complete : function() {
			if (complete) {
				complete()
			}
		}
	}).retry({times : httpRetry, timeout : httpRetryWait});
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
	if (endingComponent) {
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
		$.ajax({
			url : fullUrl,
			processData : false,
			type : "GET",
			timeout : httpTimeout,
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
		}).retry({times : httpRetry, timeout : httpRetryWait});
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
	if (abortingComponent) {
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
	$.ajax({
		url : fullUrl,
		processData : false,
		type : "GET",
		timeout : httpTimeout,
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
	}).retry({times : httpRetry, timeout : httpRetryWait});
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
	if (endingComponent) {
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
	$.ajax({
		url : fullUrl,
		processData : false,
		type : "GET",
		timeout : httpTimeout,
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
	}).retry({times : httpRetry, timeout : httpRetryWait});
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
	$.ajax({
		url : "/publix/" + jatos.studyId + "/" + jatos.componentId
				+ "/logError",
		data : logErrorMsg,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		timeout : httpTimeout
	}).retry({times : httpRetry, timeout : httpRetryWait});
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

