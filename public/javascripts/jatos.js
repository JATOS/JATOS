/**
 * JATOS JavaScript Library
 * 
 * Author Kristian Lange 2014
 */

var jatos = {};
var onErrorCallback;
var onLoadCallback;

window.addEventListener('load', onload);

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
}

/**
 * Initialising jatos.js.
 */
function onload() {
	var studyDataReady = false;
	var componentDataReady = false;

	/**
	 * Reads JATOS' ID cookie and stores all key-value pairs into jatos scope
	 * This function is automatically called after the page is loaded, so it's
	 * not necessary to call it again.
	 */
	readIdCookie = function() {
		var nameEQ = escape("JATOS_IDs") + "=";
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
	 * Checks whether study's data and component's data are finished loading
	 */
	ready = function() {
		if (studyDataReady && componentDataReady) {
			if (onLoadCallback) {
				onLoadCallback();
			}
		}
	}

	/**
	 * Gets the study's data from the JATOS server and stores them in
	 * jatos.studyData (the whole data) and jatos.studyJsonData (just the JSON
	 * data).
	 */
	getStudyData = function() {
		$.ajax({
			url : "/publix/" + jatos.studyId + "/getData",
			type : "GET",
			dataType : 'json',
			success : function(response) {
				jatos.studyData = response;
				jatos.studyJsonData = $.parseJSON(jatos.studyData.jsonData);
				studyDataReady = true;
				ready();
			},
			error : function(err) {
				if (onErrorCallback) {
					onErrorCallback(err.responseText);
				}
			}
		});
	}

	/**
	 * Gets the component's data from the JATOS server and stores them in
	 * jatos.componentData (the whole data) and jatos.componentJsonData (just
	 * the JSON data).
	 */
	getComponentData = function() {
		$.ajax({
			url : "/publix/" + jatos.studyId + "/" + jatos.componentId
					+ "/getData",
			type : "GET",
			dataType : 'json',
			success : function(response) {
				jatos.componentData = response;
				jatos.componentJsonData = $
						.parseJSON(jatos.componentData.jsonData);
				document.title = jatos.componentData.title;
				componentDataReady = true;
				ready();
			},
			error : function(err) {
				if (onErrorCallback) {
					onErrorCallback(err.responseText);
				}
			}
		});
	}

	readIdCookie();
	getStudyData();
	getComponentData();
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
	$.ajax({
		url : "/publix/" + jatos.studyId + "/" + jatos.componentId
				+ "/submitResultData",
		data : resultData,
		processData : false,
		type : "POST",
		contentType : "text/plain",
		success : function(response) {
			if (success) {
				success(response)
			}
		},
		error : function(err) {
			if (onErrorCallback) {
				onErrorCallback(err.responseText);
			}
			if (error) {
				error(response)
			}
		}
	});
}

/**
 * Starts the component with the given ID. You can pass on information to the
 * next component by adding a query string.
 * 
 * @param {Object}
 *            nextComponentId - ID of the component to start
 * @param {optional
 *            Object} queryString - Query string without the initial '?' that
 *            should be added to the URL
 */
jatos.startComponent = function(nextComponentId, queryString) {
	var url = "/publix/" + jatos.studyId + "/" + nextComponentId + "/start";
	if (queryString) {
		url += "?" + queryString;
	}
	window.location.href = url;
}

/**
 * Starts the component with the given position (# of component within study).
 * You can pass on information to the next component by adding a query string.
 * 
 * @param {Object}
 *            nextComponentPos - Position of the component to start
 * @param {optional
 *            Object} queryString - Query string without the initial '?' that
 *            should be added to the URL
 */
jatos.startComponentByPos = function(nextComponentPos, queryString) {
	var url = "/publix/" + jatos.studyId + "/startComponent?position="
			+ nextComponentPos;
	if (queryString) {
		url += "&" + queryString;
	}
	window.location.href = url;
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
	var url = "/publix/" + jatos.studyId + "/startNextComponent";
	if (queryString) {
		url += "?" + queryString;
	}
	window.location.href = url;
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
	var url = "/publix/" + jatos.studyId + "/" + jatos.componentId + "/end";
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
		success : function(response) {
			if (success) {
				success(response)
			}
		},
		error : function(err) {
			if (onErrorCallback) {
				onErrorCallback(err.responseText);
			}
			if (error) {
				error(response)
			}
		}
	});
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
		success : function(response) {
			if (success) {
				success(response)
			}
		},
		error : function(err) {
			if (onErrorCallback) {
				onErrorCallback(err.responseText);
			}
			if (error) {
				error(response)
			}
		}
	});
}

/**
 * Aborts study. All previously submitted data will be deleted.
 * 
 * @param {optional
 *            String} message - Message that should be logged
 */
jatos.abortStudy = function(message) {
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
		success : function(response) {
			if (success) {
				success(response)
			}
		},
		error : function(err) {
			if (onErrorCallback) {
				onErrorCallback(err.responseText);
			}
			if (error) {
				error(response)
			}
		}
	});
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
		contentType : "text/plain"
	});
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
