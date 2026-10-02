"use strict";
(() => {
  // src/workers/http-loop-worker.js
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
  var requests = [];
  var running = false;
  onmessage = function(request) {
    if (!request.data) {
      console.error("Empty request.data");
      return;
    }
    requests.push(request.data);
    if (!running) {
      running = true;
      run();
    }
  };
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
    xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");
    xhr.onload = function() {
      if (xhr.status === 200) {
        self.postMessage({
          status: xhr.status,
          requestId: request.id
        });
        run();
      } else {
        handleErrorAndRetry(false);
      }
    };
    xhr.ontimeout = function() {
      handleErrorAndRetry(true);
    };
    xhr.onerror = function() {
      handleErrorAndRetry(false);
    };
    function handleErrorAndRetry(timeout) {
      if (xhr.status === 400 || xhr.status === 413 || !(request.retry > 0)) {
        const msg = {
          requestId: request.id,
          url: request.url,
          method: request.method
        };
        if (timeout) {
          msg.error = "timeout";
        } else {
          msg.status = xhr.status;
          msg.statusText = xhr.statusText;
          msg.error = xhr.responseText.trim() || null;
        }
        console.error(`Failed ${request.method} to ${request.url}`);
        self.postMessage(msg);
        run();
      } else {
        console.warn(`Retry ${request.method} to ${request.url} - ${request.retry} retry attempts left`);
        request.retry = request.retry - 1;
        requests.unshift(request);
        setTimeout(run, request.retryWait);
      }
    }
    let data;
    if ("data" in request) {
      data = request.data;
    } else if ("blob" in request) {
      data = new FormData();
      data.append("file", request.blob, request.filename);
    }
    xhr.send(data);
  }
})();
