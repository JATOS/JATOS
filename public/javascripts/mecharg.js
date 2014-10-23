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

// Reads MechArg's cookie and returns an object {studyId, componentId,
// componentPosition}
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

mecharg.getComponentData = function(success, error) {
	$.ajax({
		url : "/publix/" + mecharg.sid + "/" + mecharg.cid + "/getData",
		type : "GET",
		dataType : 'json',
		success : function(response) {
			mecharg.componentData = response;
			mecharg.jsonData = $.parseJSON(componentData.jsonData);
			success(response);
		},
		error : function(err) {
			error(err.responseText);
		}
	});
}

mecharg.submitResultData = function(resultData, success, error) {
	var resultJson = JSON.stringify(resultData);
	$.ajax({
		url : "/publix/" + mecharg.sid + "/" + mecharg.cid + "/submitResultData",
		data : resultJson,
		processData : false,
		type : "POST",
		contentType : "application/json",
		success : function(response) {
			success(response);
		},
		error : function(err) {
			error(err.responseText);
		}
	});
}

mecharg.startNextComponent = function() {
	window.location.href = "/publix/" + mecharg.sid + "/startNextComponent";
}