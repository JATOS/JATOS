/**
 * Web worker used in JATOS to regularly check for session timeouts
 * The actual session timeout handling is of course done on the server side.
 * These timeouts just cause an login overlay.
 * 
 * http://www.jatos.org
 * Author Kristian Lange 2017
 * Licensed under Apache License 2.0
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
		postMessage("You have been logged out due to session timeout.");
	}
	if (currentTime > sessionInactivityTime) {
		clearInterval(checkSessionTimeoutsInterval);
		postMessage("You have been logged out due to inactivity.");
	}
}
