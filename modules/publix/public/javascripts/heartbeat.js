/**
 * heartbeat.js
 * 
 * Web worker used in jatos.js that sends a periodic Ajax request back to the
 * JATOS server.
 * 
 * http://www.jatos.org
 * Author Kristian Lange 2014 - 2016
 * Licensed under Apache License 2.0
 */

"use strict";

/**
 * How many milliseconds it waits between beats (default is 2 min)
 */
var periodDefault = 120000;
var period;
var url;
var ajax;

/**
 * Web workers callback function. In the event's data must be an array with the
 * study Id, the study result ID and optionally the heartbeat's period.
 */
onmessage = function(e) {
	var studyId = e.data[0];
	var studyResultId = e.data[1];
	period = (typeof e.data[2] === 'undefined') ? periodDefault
			: e.data[2];
	url = "/publix/" + studyId + "/heartbeat" + "?srid=" + studyResultId;
	if (!ajax) {
		send();
	}
}

function send() {
	ajax = new XMLHttpRequest();
	ajax.open('POST', url);
	ajax.setRequestHeader('Content-Type', 'text/plain');
	ajax.onload = function() {
		setTimeout(function() {
			send();
		}, period);
	};
	ajax.send();
}