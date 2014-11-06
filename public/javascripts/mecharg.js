/**
 * MechArg JavaScript Library
 * 
 * Author Kristian Lange 2014
 */

var mecharg = {};
var onErrorCallback;
var onLoadCallback;

window.addEventListener('load', onload);

/**
 * Defines callback function that is to be called when mecharg.js is finished
 * its initialisation.
 */
mecharg.onLoad = function(callback) {
	onLoadCallback = callback;
}

/**
 * Defines callback function that is to be called in case mecharg.js produces an
 * error.
 */
mecharg.onError = function(callback) {
	onErrorCallback = callback;
}

/**
 * Initialising mecharg.js.
 */
function onload() {
	var studyDataReady = false;
	var componentDataReady = false;

	/**
	 * Reads MechArg's ID cookie and stores all key-value pairs into mecharg
	 * scope This function is automatically called after the page is loaded, so
	 * it's not necessary to call it again.
	 */
	readIdCookie = function() {
		var nameEQ = escape("MechArg_IDs") + "=";
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
					mecharg[keyValuePair[0]] = keyValuePair[1];
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
	 * Gets the study's data from the MechArg server and stores them in
	 * mecharg.studyData (the whole data) and mecharg.studyJsonData (just the
	 * JSON data).
	 */
	getStudyData = function() {
		$
				.ajax({
					url : "/publix/" + mecharg.studyId + "/getData",
					type : "GET",
					dataType : 'json',
					success : function(response) {
						mecharg.studyData = response;
						mecharg.studyJsonData = $
								.parseJSON(mecharg.studyData.jsonData);
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
	 * Gets the component's data from the MechArg server and stores them in
	 * mecharg.componentData (the whole data) and mecharg.componentJsonData
	 * (just the JSON data).
	 */
	getComponentData = function() {
		$.ajax({
			url : "/publix/" + mecharg.studyId + "/" + mecharg.componentId
					+ "/getData",
			type : "GET",
			dataType : 'json',
			success : function(response) {
				mecharg.componentData = response;
				mecharg.componentJsonData = $
						.parseJSON(mecharg.componentData.jsonData);
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
 * Posts resultData back to the MechArg server.
 * 
 * @param {Object}
 *            resultData - String to be submitted
 * @param {optional
 *            Function} success - Function to be called in case of successful
 *            submit
 * @param {optional
 *            Function} error - Function to be called in case of error
 */
mecharg.submitResultData = function(resultData, success, error) {
	$.ajax({
		url : "/publix/" + mecharg.studyId + "/" + mecharg.componentId
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
 * Starts the next component of this study. The next component is the one with
 * position + 1.
 */
mecharg.startNextComponent = function() {
	window.location.href = "/publix/" + mecharg.studyId + "/startNextComponent";
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
mecharg.endComponent = function(successful, errorMsg, success, error) {
	var url = "/publix/" + mecharg.studyId + "/" + mecharg.componentId + "/end";
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
mecharg.abortStudyAjax = function(message, success, error) {
	var url = "/publix/" + mecharg.studyId + "/abort";
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
mecharg.abortStudy = function(message) {
	var url = "/publix/" + mecharg.studyId + "/abort";
	if (undefined == message) {
		window.location.href = url;
	} else {
		window.location.href = url + "?message=" + message;
	}
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
 * @param {optional
 *            Function} success - Function to be called in case of successful
 *            submit
 * @param {optional
 *            Function} error - Function to be called in case of error
 */
mecharg.endStudyAjax = function(successful, errorMsg, success, error) {
	var url = "/publix/" + mecharg.studyId + "/end";
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
mecharg.endStudy = function(successful, errorMsg) {
	var url = "/publix/" + mecharg.studyId + "/end";
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
 * Logs an error within the MechArg.
 */
mecharg.logError = function(logErrorMsg) {
	$.ajax({
		url : "/publix/" + mecharg.studyId + "/" + mecharg.componentId
				+ "/logError",
		data : logErrorMsg,
		processData : false,
		type : "POST",
		contentType : "text/plain"
	});
}
