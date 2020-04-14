/**
 * httpLoop.js
 * 
 * Web worker used in jatos.js - Background queue for ajax requests
 * 
 * While the study continues, this worker sends the requests in 
 * the background without blocking the study's thread. The request
 * order is kept. All end/abort study functions wait until all
 * requests here are done.
 * 
 * http://www.jatos.org
 * Author Kristian Lange 2020
 * Licensed under Apache License 2.0
 */

// jshint ignore: start

"use strict";

var requests = [];
var running = false;

onmessage = function (request) {
	requests.push(request.data);
	if (!running) {
		running = true;
		run();
	}
}

function run() {
	if (requests.length == 0) {
		running = false;
		return;
	}

	var request = requests.shift();

	var xhr = new XMLHttpRequest();
	xhr.open(request.method, request.url);
	if (request.contentType) xhr.setRequestHeader("Content-Type", request.contentType);
	if (request.timeout) xhr.timeout = request.timeout;
	// X-Requested-With header needed to detect Ajax in backend
	xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");

	xhr.onload = function () {
		if (xhr.status == 200) {
			self.postMessage({
				status: xhr.status,
				requestId: request.id
			});
			run();
		} else {
			handleError(false);
		}
	};
	xhr.ontimeout = function () { handleError(true) };
	xhr.onerror = function () { handleError(false) };

	function handleError(timeout) {
		if ("retry" in request === false || request.retry <= 0) {
			var msg = {
				requestId: request.id,
				url: request.url,
				method: request.method
			}
			if (timeout) {
				msg.error = "timeout"
			} else {
				msg.status = xhr.status;
				msg.statusText = xhr.statusText;
				msg.error = xhr.responseText.trim() || null;
			}
			self.postMessage(msg);
		} else {
			console.log("Retry " + request.method + " to " + request.url);
			request.retry = request.retry - 1;
			requests.unshift(request);
		}
		setTimeout(run, request.retryWait);
	}

	// Actual sending of data
	var data;
	if ("data" in request) {
		data = request.data;
	} else if ("blob" in request) {
		data = new FormData();
		data.append("file", request.blob, request.filename);
	}
	xhr.send(data);
}
