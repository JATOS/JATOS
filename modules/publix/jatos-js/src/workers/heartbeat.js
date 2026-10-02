/*!
 * heartbeat.js
 *
 * Web worker used by jatos.js to send periodic HTTP requests to the JATOS
 * server. This study-run heartbeat is separate from the channel heartbeats.
 *
 * http://www.jatos.org
 * Author Kristian Lange
 * Licensed under Apache License 2.0
 */

"use strict";

/**
 * How many milliseconds it waits between beats (default is 60 sec)
 */
const periodDefault = 60000;
let period;
let url;
let xhr;

/**
 * Handles worker messages containing the study-result UUID and, optionally,
 * the heartbeat period.
 */
onmessage = function(e) {
	const studyResultUuid = e.data[0];
	period = (typeof e.data[1] === 'undefined') ? periodDefault : e.data[1];
	url = "../../../../" + studyResultUuid + "/heartbeat";
	if (!xhr) {
		send();
	}
};

function send() {
	xhr = new XMLHttpRequest();
	xhr.open('POST', url);
	xhr.setRequestHeader('Content-Type', 'text/plain');
	xhr.onload = function() {
		setTimeout(function() {
			send();
		}, period);
	};
	xhr.send();
}
