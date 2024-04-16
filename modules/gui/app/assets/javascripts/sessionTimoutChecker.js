/**
 * Web worker used in JATOS to regularly check for user session timeouts.
 * The actual session timeout handling is done on the server side.
 *
 * I use a web worker with regular checking of the times (with setInterval) instead of just using setTimeout directly,
 * because I found that this is the safest way if JATOS' browser tab runs in the background.
 */

"use strict";

var sessionTimeoutTime;
var sessionInactivityTime;
var checkSessionTimeoutsInterval;

/**
 * Web workers callback function. In the event's data must be an array with the
 * two session timeouts, session timeout and session inactivity
 */
onmessage = function(msg) {
	setSessionTimeouts(msg.data[0], msg.data[1]);
}

function setSessionTimeouts(sessionTimeout, sessionInactivity) {
	var currentTime = Date.now();
	sessionTimeoutTime = currentTime + sessionTimeout * 60000;
	sessionInactivityTime = currentTime + sessionInactivity * 60000;
	checkSessionTimeoutsInterval = setInterval(checkSessionTimeouts, 1000);
}

function checkSessionTimeouts() {
	var currentTime = Date.now();
	if (currentTime > sessionTimeoutTime) {
		clearInterval(checkSessionTimeoutsInterval);
		postMessage("You have been signed out due to session timeout.");
	}
	if (currentTime > sessionInactivityTime) {
		clearInterval(checkSessionTimeoutsInterval);
		postMessage("You have been signed out due to inactivity.");
	}
}
