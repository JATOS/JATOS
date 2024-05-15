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
 * Web workers callback function. The message's data object needs three key-value pairs
 * @param {Number} m.data.userSessionTimeout - timeout in ms when the user session invalidates
 * @param {Number} m.data.userSessionInactivity - timeout in ms when the the user session invalidates if there is no user activity
 * @param {Number} m.data.userSigninTime - timestamp when the user last signed in
 */
onmessage = function(m) {
    var currentTime = Date.now();
    sessionTimeoutTime = m.data.userSigninTime + m.data.userSessionTimeout;
    sessionInactivityTime = currentTime + m.data.userSessionInactivity;
    checkSessionTimeoutsInterval = setInterval(checkSessionTimeouts, 5000);
}

function checkSessionTimeouts() {
	var currentTime = Date.now();
	if (currentTime > sessionTimeoutTime) {
		clearInterval(checkSessionTimeoutsInterval);
		postMessage("You have been signed out because your session timed out.");
	}
	if (currentTime > sessionInactivityTime) {
		clearInterval(checkSessionTimeoutsInterval);
		postMessage("You have been signed out due to inactivity.");
	}
}