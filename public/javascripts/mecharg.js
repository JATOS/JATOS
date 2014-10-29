/**
 * MechArg JavaScript Library
 * 
 * Author Kristian Lange 2014
 */

var mecharg = {};

window.addEventListener('load', onload);

function onload() {
	mecharg.readIdCookie();
}

/**
 * Reads MechArg's ID cookie and stores the studyId into mecharg.sid,
 * componentId into mecharg.cid, and the componentPosition into mecharg.pos.
 * This function is automatically called after the page is loaded, so it's not
 * necessary to call it again.
 */
mecharg.readIdCookie = function() {
	var nameEQ = escape("MechArg_IDs") + "=";
	var ca = document.cookie.split(';');
	for (var i = 0; i < ca.length; i++) {
		var c = ca[i];
		while (c.charAt(0) === ' ') {
			c = c.substring(1, c.length);
		}
		if (c.indexOf(nameEQ) === 0) {
			var cookieStr = unescape(c.substring(nameEQ.length, c.length));
			var idMap = cookieStr.split("&");
			var mechArgIds = {};
			mecharg.sid = parseInt(idMap[0].split("=")[1]);
			mecharg.cid = parseInt(idMap[1].split("=")[1]);
			mecharg.pos = parseInt(idMap[2].split("=")[1]);
		}
	}
}

/**
 * Gets the study's data from the MechArg server and stores them in
 * mecharg.sData (the whole data) and mecharg.sJsonData (just the JSON data).
 * 
 * @param {optional
 *            Function} success - Function to be called in case of success.
 * @param {optional
 *            Function} error - Function to be called in case server returns an
 *            error.
 */
mecharg.getStudyData = function(success, error) {
	$.ajax({
		url : "/publix/" + mecharg.sid + "/getData",
		type : "GET",
		dataType : 'json',
		success : function(response) {
			mecharg.sData = response;
			mecharg.sJsonData = $.parseJSON(mecharg.sData.jsonData);
			if (success) {
				success(response)
			}
			;
		},
		error : function(err) {
			if (error) {
				error(err.responseText)
			}
			;
		}
	});
}

/**
 * Gets the component's data from the MechArg server and stores them in
 * mecharg.cData (the whole data) and mecharg.cJsonData (just the JSON data).
 * 
 * @param {optional
 *            Function} success - Function to be called in case of success.
 * @param {optional
 *            Function} error - Function to be called in case server returns an
 *            error.
 */
mecharg.getComponentData = function(success, error) {
	$.ajax({
		url : "/publix/" + mecharg.sid + "/" + mecharg.cid + "/getData",
		type : "GET",
		dataType : 'json',
		success : function(response) {
			mecharg.cData = response;
			mecharg.cJsonData = $.parseJSON(mecharg.cData.jsonData);
			if (success) {
				success(response)
			}
			;
		},
		error : function(err) {
			if (error) {
				error(err.responseText)
			}
			;
		}
	});
}

/**
 * Posts resultData as JSON back to the MechArg server.
 * 
 * @param {Object}
 *            resultData - JS object. Will be turned into an JSON string.
 * @param {optional
 *            Function} success - Function to be called in case of successful
 *            submit
 * @param {optional
 *            Function} error - Function to be called in case server returns an
 *            error
 */
mecharg.submitResultData = function(resultData, success, error) {
	var resultJson = JSON.stringify(resultData);
	$.ajax({
		url : "/publix/" + mecharg.sid + "/" + mecharg.cid
				+ "/submitResultData",
		data : resultJson,
		processData : false,
		type : "POST",
		contentType : "application/json",
		success : function(response) {
			if (success) {
				success(response)
			}
			;
		},
		error : function(err) {
			if (error) {
				error(err.responseText)
			}
			;
		}
	});
}

/**
 * Starts the next component of this study. The next component is the one with
 * position + 1.
 */
mecharg.startNextComponent = function() {
	window.location.href = "/publix/" + mecharg.sid + "/startNextComponent";
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
		window.location.href = "/publix/" + mecharg.sid + "/end";
	} else {
		window.location.href = "/publix/" + mecharg.sid + "/end?successful="
				+ successful + "&errorMsg=" + errorMsg;
	}
}

/**
 * Logs an error within the MechArg.
 */
mecharg.logError = function(logErrorMsg) {
	$.ajax({
		url : "/publix/" + mecharg.sid + "/" + mecharg.cid + "/logError",
		data : logErrorMsg,
		processData : false,
		type : "POST",
		contentType : "text/plain"
	});
}
