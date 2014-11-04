/**
 * MechArg JavaScript Library
 * 
 * Author Kristian Lange 2014
 */

var mecharg = {};
var onErrorCallback;
var onLoadCallback;

window.addEventListener('load', onload);

mecharg.onError = function(callback) {
	onErrorCallback = callback;
}

mecharg.onLoad = function(callback) {
	onLoadCallback = callback;
}

function onload() {
	var studyDataReady = false;
	var componentDataReady = false;

	ready = function() {
		if (studyDataReady && componentDataReady) {
			if (onLoadCallback) {
				onLoadCallback();
			}
		}
	}

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
 * Posts resultData as JSON back to the MechArg server.
 * 
 * @param {Object}
 *            resultData - JS object. Will be turned into an JSON string.
 * @param {optional
 *            Function} success - Function to be called in case of successful
 *            submit
 */
mecharg.submitResultData = function(resultData, success) {
	var resultJson = JSON.stringify(resultData);

	$.ajax({
		url : "/publix/" + mecharg.studyId + "/" + mecharg.componentId
				+ "/submitResultData",
		data : resultJson,
		processData : false,
		type : "POST",
		contentType : "application/json",
		success : function(response) {
			if (success) {
				success(response)
			}
		},
		error : function(err) {
			if (onErrorCallback) {
				onErrorCallback(err.responseText);
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
	if (undefined == successful || undefined == errorMsg) {
		window.location.href = "/publix/" + mecharg.studyId + "/end";
	} else {
		window.location.href = "/publix/" + mecharg.studyId
				+ "/end?successful=" + successful + "&errorMsg=" + errorMsg;
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
