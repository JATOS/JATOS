/*!
 * http-loop-worker.js
 *
 * Web worker used in jatos.js - Background queue for HTTP requests
 *
 * While the study continues, this worker sends the requests in
 * the background without blocking the study's thread. The request
 * order is kept, including retries. After success or final failure,
 * the worker reports the outcome and continues with the next request.
 * A failed request does not prevent later study completion requests.
 *
 * http://www.jatos.org
 * Author Kristian Lange
 * Licensed under Apache License 2.0
 */

"use strict";

/**
 * Request queued by the main thread. Supply either a text body or a file upload;
 * if both are present, data takes precedence over blob.
 *
 * @typedef {Object} HttpRequest
 * @property {number} id - Correlation ID returned as requestId in the response.
 * @property {string} url - Request URL.
 * @property {string} method - HTTP method, e.g. GET, POST or PUT.
 * @property {string} [data] - Text request body.
 * @property {Blob} [blob] - File contents, sent as the multipart field "file".
 * @property {string} [filename] - Upload filename, supplied together with blob.
 * @property {string} [contentType] - Content-Type for text bodies; omit for file
 * uploads so XMLHttpRequest generates the multipart boundary.
 * @property {number} [timeout] - Timeout per attempt in milliseconds; omitted or
 * zero leaves XMLHttpRequest's default of no timeout.
 * @property {number} [retry] - Remaining retries after the initial attempt,
 * decremented on each retry. Omitted or zero disables retries. HTTP 400 and 413
 * are never retried.
 * @property {number} [retryWait] - Delay before each retry in milliseconds.
 */

/** @type {HttpRequest[]} */
const requests = [];
let running = false;

/**
 * Queues a request received from the main thread and starts processing if idle.
 *
 * @param {MessageEvent<HttpRequest>} request - Worker message; data holds the request.
 */
onmessage = function (request) {
	if (!request.data) {
		console.error("Empty request.data");
		return;
	}
	requests.push(request.data);
	if (!running) {
		running = true;
		run();
	}
}

/**
 * Sends the next queued request. Only HTTP 200 counts as a success. Reports a
 * response with requestId and status on success; on final failure also includes
 * url, method and error, plus status/statusText for non-timeout failures.
 * Retries stay ahead of later requests and produce no intermediate response.
 */
function run() {
	if (requests.length === 0) {
		running = false;
		return;
	}

	const request = requests.shift();

	const xhr = new XMLHttpRequest();
	xhr.open(request.method, request.url);
	if (request.contentType) xhr.setRequestHeader("Content-Type", request.contentType);
	if (request.timeout) xhr.timeout = request.timeout;
	xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest"); // Lets the backend identify this as an XMLHttpRequest.

	xhr.onload = function () {
		if (xhr.status === 200) {
			self.postMessage({
				status: xhr.status,
				requestId: request.id
			});
			run(); // Run the next request in line without waiting
		} else {
			handleErrorAndRetry(false);
		}
	};
	xhr.ontimeout = function () { handleErrorAndRetry(true) };
	xhr.onerror = function () { handleErrorAndRetry(false) };

	/**
	 * Retries the current request or reports its final failure and advances the queue.
	 * @param {boolean} timeout - Whether the attempt failed because its timeout elapsed.
	 */
	function handleErrorAndRetry(timeout) {
		// Do not retry if
		// 1) a 400 (Bad Request),
		// 2) a 413 (Content too large),
		// 3) retry is not wanted, or
		// 4) all retry attempts were already used
		if (xhr.status === 400 || xhr.status === 413 || !(request.retry > 0)) {
			const msg = {
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
			console.error(`Failed ${request.method} to ${request.url}`);
			self.postMessage(msg); // Do not retry the request and post message back to sender
			run(); // Run the next request in line without waiting
		} else {
			console.warn(`Retry ${request.method} to ${request.url} - ${request.retry} retry attempts left`);
			request.retry = request.retry - 1;
			requests.unshift(request); // Retry this request before other requests
			setTimeout(run, request.retryWait); // Run the next request after waiting a bit
		}
	}

	// Actual sending of data
	let data;
	if ("data" in request) {
		data = request.data;
	} else if ("blob" in request) {
		data = new FormData();
		data.append("file", request.blob, request.filename);
	}
	xhr.send(data);
}
