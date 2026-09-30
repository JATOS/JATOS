var jatos;
(() => {
  // src/utils/callbacks.js
  var call = (f) => {
    if (f && typeof f == "function") f();
  };
  var callWithArgs = (f, ...args) => {
    if (f && typeof f == "function") args.length ? f(...args) : f();
  };
  var callMany = (arg, ...functions) => functions.forEach((f) => {
    if (f && typeof f === "function") f(arg);
  });

  // src/utils/clone-json.js
  function cloneJsonObj(obj) {
    var copy;
    if (null === obj || "object" != typeof obj) return obj;
    if (obj instanceof Array) {
      copy = [];
      for (var i = 0, len = obj.length; i < len; i++) {
        copy[i] = cloneJsonObj(obj[i]);
      }
      return copy;
    }
    if (obj instanceof Object) {
      copy = {};
      for (var attr in obj) {
        if (obj.hasOwnProperty(attr)) copy[attr] = cloneJsonObj(obj[attr]);
      }
      return copy;
    }
    throw new Error("Unable to copy obj! Its type isn't supported.");
  }

  // src/jatos-promise.js
  function createLegacyDeferred() {
    let currentState = "pending";
    let settledArgs = [];
    let settledContext;
    const doneCallbacks = [];
    const failCallbacks = [];
    const progressCallbacks = [];
    const promiseMethods = {
      state: function() {
        return currentState;
      },
      always: function(...callbacks) {
        this.done(...callbacks);
        this.fail(...callbacks);
        return this;
      },
      catch: function(onRejected) {
        return this.then(null, onRejected);
      },
      done: function(...callbacks) {
        addCallbacks(doneCallbacks, callbacks);
        if (currentState === "resolved") fireCallbacks(callbacks, settledContext, settledArgs);
        return this;
      },
      fail: function(...callbacks) {
        addCallbacks(failCallbacks, callbacks);
        if (currentState === "rejected") fireCallbacks(callbacks, settledContext, settledArgs);
        return this;
      },
      progress: function(...callbacks) {
        if (currentState === "pending") addCallbacks(progressCallbacks, callbacks);
        return this;
      },
      then: function(onFulfilled, onRejected) {
        const chained = createLegacyDeferred();
        const chainedPromise = chained.promise();
        this.done(function(...args) {
          settleChained(chained, chainedPromise, onFulfilled, "resolve", this, args);
        });
        this.fail(function(...args) {
          settleChained(chained, chainedPromise, onRejected, "reject", this, args);
        });
        return chainedPromise;
      },
      // jQuery retains pipe as an older name for promise transformation.
      pipe: function(onFulfilled, onRejected) {
        return this.then(onFulfilled, onRejected);
      },
      promise: function(target) {
        if (target != null) return Object.assign(target, promiseMethods);
        return promise;
      }
    };
    const promise = Object.assign({}, promiseMethods);
    const deferred = Object.assign({}, promiseMethods, {
      notify: function(...args) {
        return this.notifyWith(this === deferred ? void 0 : this, args);
      },
      notifyWith: function(context, args) {
        if (currentState === "pending") fireCallbacks(progressCallbacks, context, toArray(args));
        return this;
      },
      reject: function(...args) {
        return this.rejectWith(this === deferred ? void 0 : this, args);
      },
      rejectWith: function(context, args) {
        if (currentState !== "pending") return this;
        currentState = "rejected";
        settledContext = context;
        settledArgs = toArray(args);
        fireCallbacks(failCallbacks, settledContext, settledArgs);
        return this;
      },
      resolve: function(...args) {
        return this.resolveWith(this === deferred ? void 0 : this, args);
      },
      resolveWith: function(context, args) {
        if (currentState !== "pending") return this;
        currentState = "resolved";
        settledContext = context;
        settledArgs = toArray(args);
        fireCallbacks(doneCallbacks, settledContext, settledArgs);
        return this;
      }
    });
    return deferred;
  }
  function addCallbacks(target, callbacks) {
    callbacks.flat(Infinity).forEach((callback) => {
      if (typeof callback === "function") target.push(callback);
    });
  }
  function fireCallbacks(callbacks, context, args) {
    callbacks.flat(Infinity).forEach((callback) => {
      if (typeof callback === "function") callback.apply(context, args);
    });
  }
  function settleChained(chained, chainedPromise, handler, fallback, context, args) {
    setTimeout(function() {
      if (typeof handler !== "function") {
        chained[fallback + "With"](context, args);
        return;
      }
      try {
        const result = handler.apply(context, args);
        adoptResult(chained, chainedPromise, result);
      } catch (error) {
        chained.reject(error);
      }
    });
  }
  function adoptResult(chained, chainedPromise, result) {
    if (result === chainedPromise) {
      chained.reject(new TypeError("Thenable self-resolution"));
    } else if (result && typeof result.then === "function") {
      result.then(
        (...args) => chained.resolve(...args),
        (...args) => chained.reject(...args)
      );
    } else {
      chained.resolve(result);
    }
  }
  function toArray(args) {
    return args == null ? [] : Array.from(args);
  }
  function createLegacyPromiseCompatibility() {
    return {
      createDeferred: createLegacyDeferred,
      rejectedPromise: function(errorMsg) {
        const deferred = createLegacyDeferred();
        deferred.reject(errorMsg);
        return deferred.promise();
      }
    };
  }
  function isDeferredPending(deferred) {
    return typeof deferred != "undefined" && deferred.state() === "pending";
  }

  // src/result-data.js
  function installResultDataApi(jatos2, dependencies) {
    const {
      createDeferred: createDeferred2,
      getURL,
      isInitialized,
      isInvalidComponentPosition: isInvalidComponentPosition2,
      isStudyRunInvalid,
      rejectedPromise: rejectedPromise2,
      sendToHttpLoop
    } = dependencies;
    jatos2.submitResultData = function(resultData, onSuccess, onError) {
      return submitOrAppendResultData(resultData, false, onSuccess, onError);
    };
    jatos2.appendResultData = function(resultData, onSuccess, onError) {
      return submitOrAppendResultData(resultData, true, onSuccess, onError);
    };
    function submitOrAppendResultData(resultData, append, onSuccess, onError) {
      if (isStudyRunInvalid()) {
        const errorMsg = "Can't send result data. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise2(errorMsg);
      }
      let httpMethod = append ? "POST" : "PUT";
      if (resultData === Object(resultData)) {
        resultData = JSON.stringify(resultData);
      }
      let request = {
        url: getURL("resultData"),
        data: resultData,
        method: httpMethod,
        contentType: "text/plain; charset=UTF-8",
        timeout: jatos2.httpTimeout,
        retry: jatos2.httpRetry,
        retryWait: jatos2.httpRetryWait
      };
      let deferred = sendToHttpLoop(request, onSuccess, onError);
      deferred.fail(function(err, status) {
        if (status === 413) {
          jatos2.showOverlay({ text: "Couldn't send result data: too large!", id: "result413", timeout: 8e3, showImg: false });
        }
      });
      return deferred.promise();
    }
    jatos2.uploadResultFile = function(obj, filename, onSuccess, onError) {
      if (isStudyRunInvalid()) {
        const errorMsg = "Can't upload file. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise2(errorMsg);
      }
      if (typeof filename !== "string" || 0 === filename.length) {
        const errorMsg = "No filename specified.";
        callMany(errorMsg, onError, console.error);
        return rejectedPromise2(errorMsg);
      }
      let blob;
      if (obj instanceof Blob) {
        blob = obj;
      } else if (typeof obj === "string") {
        blob = new Blob([obj], { type: "text/plain" });
      } else if (obj === Object(obj)) {
        blob = new Blob([JSON.stringify(obj, null, 2)], { type: "application/json" });
      } else {
        const errorMsg = "Only string, Object or Blob allowed.";
        callMany(errorMsg, onError, console.error);
        return rejectedPromise2(errorMsg);
      }
      let request = {
        url: getURL("files/" + encodeURI(filename)),
        blob,
        filename,
        method: "POST",
        timeout: jatos2.httpTimeout,
        retry: jatos2.httpRetry,
        retryWait: jatos2.httpRetryWait
      };
      let deferred = sendToHttpLoop(request, onSuccess, onError);
      deferred.fail(function(err, status) {
        if (status === 413) {
          jatos2.showOverlay({ text: "Couldn't send result file: too large!", id: "result413", timeout: 8e3, showImg: false });
        }
      });
      return deferred.promise();
    };
    jatos2.downloadResultFile = function(param1, param2, param3, param4) {
      if (!isInitialized()) {
        const errorMsg = "jatos.js not yet initialized";
        console.error(errorMsg);
        return rejectedPromise2(errorMsg);
      }
      let componentPos, filename, onSuccess, onError;
      if (typeof param1 === "number") {
        componentPos = param1;
        filename = param2;
        onSuccess = param3;
        onError = param4;
      } else if (typeof param1 === "string") {
        filename = param1;
        onSuccess = param2;
        onError = param3;
      } else {
        const errorMsg = "Unknown first parameter.";
        console.error(errorMsg);
        return rejectedPromise2(errorMsg);
      }
      if (isStudyRunInvalid()) {
        const errorMsg = "Can't download file. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise2(errorMsg);
      }
      if (typeof filename !== "string" || 0 === filename.length) {
        const errorMsg = "No filename specified.";
        callMany(errorMsg, onError, console.error);
        return rejectedPromise2(errorMsg);
      }
      let url = getURL("../files/" + encodeURI(filename));
      if (componentPos) {
        if (isInvalidComponentPosition2(componentPos)) {
          const errorMsg = "Component position does not exist.";
          callMany(errorMsg, onError, console.error);
          return rejectedPromise2(errorMsg);
        }
        const componentId = jatos2.componentList[componentPos - 1].id;
        url += "?componentId=" + componentId;
      }
      const deferred = createDeferred2();
      const xhr = new XMLHttpRequest();
      xhr.open("GET", url, true);
      xhr.responseType = "blob";
      xhr.onload = function() {
        if (this.status === 200) {
          const blob = xhr.response;
          if (blob.type === "application/json") {
            const jsonReader = new FileReader();
            jsonReader.addEventListener("loadend", function() {
              const obj = JSON.parse(jsonReader.result);
              callWithArgs(onSuccess, obj);
              deferred.resolve(obj);
            });
            jsonReader.readAsText(blob);
          } else if (blob.type === "text/plain") {
            const textReader = new FileReader();
            textReader.addEventListener("loadend", function() {
              const text = textReader.result;
              callWithArgs(onSuccess, text);
              deferred.resolve(text);
            });
            textReader.readAsText(blob);
          } else {
            callWithArgs(onSuccess, blob);
            deferred.resolve(blob);
          }
        } else {
          xhr.onerror();
        }
      };
      xhr.onerror = function() {
        const error = "Download of " + filename + " returned " + xhr.statusText;
        callMany(error, onError, console.error);
        deferred.reject(error);
      };
      xhr.send(null);
      return deferred.promise();
    };
  }

  // src/http-loop.js
  function createHttpLoop({ createDeferred: createDeferred2, isInitialized }) {
    let worker;
    let counter = 0;
    let idleDeferred;
    const waitingRequests = {};
    function start() {
      worker = new Worker("jatos-publix/javascripts/http-loop-worker.js");
      worker.addEventListener("message", (event) => handleMessage(event.data), false);
    }
    function send(request, onSuccess, onError) {
      if (!isInitialized()) {
        console.error("jatos.js not yet initialized");
        return createDeferred2().reject();
      }
      const deferred = createDeferred2();
      deferred.done(function() {
        call(onSuccess);
      });
      deferred.fail(function(err) {
        callWithArgs(onError, err);
      });
      request.id = counter++;
      waitingRequests[request.id] = deferred;
      if (!isDeferredPending(idleDeferred)) {
        idleDeferred = createDeferred2();
      }
      worker.postMessage(request);
      return deferred;
    }
    function handleMessage(msg) {
      const deferred = waitingRequests[msg.requestId];
      delete waitingRequests[msg.requestId];
      if (msg.status === 200) {
        deferred.resolve();
      } else {
        const errMsg = [msg.status, msg.statusText, msg.error].filter(function(s) {
          return s;
        }).join(", ");
        deferred.reject(
          msg.method + " to " + msg.url + " failed: " + errMsg,
          msg.status,
          msg.statusText,
          msg.error
        );
      }
      if (Object.keys(waitingRequests).length === 0 && isDeferredPending(idleDeferred)) {
        idleDeferred.resolve();
      }
    }
    function isBusy() {
      return isDeferredPending(idleDeferred);
    }
    function whenIdle(callback) {
      if (isBusy()) {
        idleDeferred.always(callback);
      } else {
        callback();
      }
    }
    function terminate() {
      worker.terminate();
    }
    function getCounter() {
      return counter;
    }
    return { getCounter, isBusy, send, start, terminate, whenIdle };
  }

  // src/component-navigation.js
  function installComponentNavigationApi(jatos2, dependencies) {
    const {
      beforeUnloadWarning,
      getURL,
      httpLoop,
      isEndingStudy,
      isInitialized,
      isStartingComponent,
      isStudyRunInvalid,
      setStartingComponent
    } = dependencies;
    jatos2.startComponent = function(componentIdOrUuid, resultData, param3, param4) {
      if (!isInitialized()) {
        console.error("jatos.js not yet initialized");
        return;
      }
      let message, onError, componentUuid;
      if (typeof componentIdOrUuid === "number") {
        componentUuid = jatos2.componentList.find((c) => c.id === componentIdOrUuid).uuid;
      } else {
        componentUuid = componentIdOrUuid;
      }
      if (typeof param3 === "string") {
        message = param3;
        onError = param4;
      } else if (typeof param3 === "function") {
        onError = param3;
      }
      if (isStudyRunInvalid()) {
        callMany("Can't start component. This study run is invalid.", onError, console.warn);
        return;
      }
      if (isStartingComponent()) {
        callMany("Can start only one component at the same time", onError, console.warn);
        return;
      }
      if (isEndingStudy()) {
        callMany("Can't start component if study already ended.", onError, console.warn);
        return;
      }
      const isSingleComponentRun = jatos2.jatosRun === "RUN_COMPONENT_FINISHED";
      if (isSingleComponentRun) {
        if (resultData) {
          jatos2.endStudy(resultData, true, message);
        } else {
          jatos2.endStudy(true, message);
        }
        return;
      }
      setStartingComponent(true);
      if (resultData) jatos2.appendResultData(resultData);
      jatos2.setStudySessionData(jatos2.studySessionData);
      const start = function() {
        window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        let url = getURL("../" + componentUuid + "/start");
        if (message) url = url + "?" + jatos2.jQuery.param({ "message": message });
        window.location.href = url;
      };
      if (httpLoop.isBusy()) {
        setTimeout(jatos2.showOverlay, 1e3, jatos2.waitSendDataOverlayConfig);
      }
      httpLoop.whenIdle(start);
    };
    jatos2.startComponentByPos = function(componentPos, resultData, param3, param4) {
      if (isInvalidComponentPosition(jatos2.componentList, componentPos)) {
        let onError;
        if (typeof param3 === "function") onError = param3;
        else if (typeof param4 === "function") onError = param4;
        callMany("Component position does not exist", onError, console.error);
        return;
      }
      const componentUuid = jatos2.componentList[componentPos - 1].uuid;
      jatos2.startComponent(componentUuid, resultData, param3, param4);
    };
    jatos2.startComponentByTitle = function(title, resultData, param3, param4) {
      const component = jatos2.componentList.find((component2) => component2.title === title);
      if (!component) {
        let onError;
        if (typeof param3 === "function") onError = param3;
        else if (typeof param4 === "function") onError = param4;
        callMany(`Component with title ${title} does not exist`, onError, console.error);
        return;
      }
      const componentUuid = component.uuid;
      jatos2.startComponent(componentUuid, resultData, param3, param4);
    };
    jatos2.startNextComponent = function(resultData, param2, param3) {
      let message;
      if (typeof param2 === "string") {
        message = param2;
      }
      const lastActiveComponent = jatos2.componentList.slice().reverse().find(function(component) {
        return component.active;
      });
      if (jatos2.componentPos >= lastActiveComponent.position) {
        if (resultData) {
          jatos2.endStudy(resultData, true, message);
        } else {
          jatos2.endStudy(true, message);
        }
        return;
      }
      for (let i = jatos2.componentPos; i < jatos2.componentList.length; i++) {
        if (jatos2.componentList[i].active) {
          const nextComponentUuid = jatos2.componentList[i].uuid;
          jatos2.startComponent(nextComponentUuid, resultData, param2, param3);
          break;
        }
      }
    };
    jatos2.startLastComponent = function(resultData, param2, param3) {
      const lastActiveComponent = jatos2.componentList.reverse().find((c) => c.active);
      jatos2.startComponent(lastActiveComponent.uuid, resultData, param2, param3);
    };
  }
  function isInvalidComponentPosition(componentList, pos) {
    return pos <= 0 || pos > componentList.length;
  }

  // src/index.js
  /*!
   * jatos.js (JATOS JavaScript Library)
   * http://www.jatos.org
   * Licensed under Apache License 2.0
   *
   * Uses plugin jquery.ajax-retry:
   * https://github.com/johnkpaul/jquery-ajax-retry
   * Copyright (c) 2012 John Paul
   * Licensed under the MIT license.
   *
   * Uses Starcounter-Jack/JSON-Patch:
   * https://github.com/Starcounter-Jack/JSON-Patch
   * Copyright (c) 2017-2022 Joachim Wester
   * Licensed under the MIT license.
   */
  jatos = {};
  window.jatos = jatos;
  var { createDeferred, rejectedPromise } = createLegacyPromiseCompatibility();
  (function() {
    "use strict";
    jatos.version = "3.11.3";
    jatos.httpTimeout = 3e4;
    jatos.httpRetry = 5;
    jatos.httpRetryWait = 1e3;
    jatos.studyJsonInput = {};
    jatos.studyInput = {};
    jatos.studyLength = null;
    jatos.studyProperties = {};
    jatos.studySessionData = {};
    jatos.componentList = [];
    jatos.componentJsonInput = {};
    jatos.componentInput = {};
    jatos.componentPos = null;
    jatos.componentProperties = {};
    jatos.batchProperties = {};
    jatos.batchJsonInput = {};
    jatos.batchInput = {};
    jatos.groupMemberId = null;
    jatos.groupResultId = null;
    jatos.groupMembers = [];
    jatos.groupChannels = [];
    let groupState = null;
    let groupSessionData = {};
    let batchSessionData = {};
    jatos.channelSendingTimeoutTime = 1e4;
    jatos.channelHeartbeatInterval = 1e4;
    jatos.channelHeartbeatTimeoutTime = 1e4;
    jatos.channelClosedCheckInterval = 2e3;
    jatos.channelOpeningBackoffTimeMin = 1e3;
    jatos.channelOpeningBackoffTimeMax = 12e4;
    jatos.waitSendDataOverlayConfig = {
      text: "Sending data. Please wait."
    };
    const batchSessionTimeouts = {};
    const groupSessionTimeouts = {};
    let groupFixedTimeout;
    let batchChannelHeartbeatTimer;
    let groupChannelHeartbeatTimer;
    let batchChannelHeartbeatTimeoutTimers = [];
    let groupChannelHeartbeatTimeoutTimers = [];
    let batchChannelClosedCheckTimer;
    let groupChannelClosedCheckTimer;
    let batchSessionVersion;
    let groupSessionVersion;
    let batchSessionCounter = 0;
    let groupSessionCounter = 0;
    jatos.batchSessionVersioning = true;
    jatos.groupSessionVersioning = true;
    let batchChannel;
    let groupChannel;
    let groupChannelCallbacks;
    const webSocketSupported = "WebSocket" in window;
    let heartbeatWorker;
    let initialized = false;
    let jatosOnLoadEventFired = false;
    let batchChannelAlive = false;
    let startingComponent = false;
    let endingStudy = false;
    let studyRunInvalid = false;
    let openingBatchChannelDeferred;
    let sendingBatchSessionDeferred;
    let openingGroupChannelDeferred;
    let sendingGroupSessionDeferred;
    let sendingGroupFixedDeferred;
    let reassigningGroupDeferred;
    let leavingGroupDeferred;
    const jatosOnLoadEvent = new Event("jatosOnLoad");
    const batchChannelAliveEvent = new Event("batchChannelAlive");
    const batchChannelDeadEvent = new Event("batchChannelDead");
    let onJatosBatchSession;
    let showBeforeUnloadWarning = true;
    const httpLoop = createHttpLoop({
      createDeferred,
      isInitialized: () => initialized
    });
    jatos.jQuery = {};
    getScript("jatos-publix/javascripts/jquery-3.7.1.min.js", function() {
      jatos.jQuery = jQuery.noConflict(true);
      jatos.jQuery.ajaxSetup({
        cache: true
      });
      initJatos();
    });
    function getScript(url, onSuccess) {
      const script = document.createElement("script");
      script.src = url;
      const head = document.getElementsByTagName("head")[0];
      let done = false;
      script.onload = script.onreadystatechange = function() {
        if (!done && (!this.readyState || this.readyState === "loaded" || this.readyState === "complete")) {
          done = true;
          onSuccess();
          script.onload = script.onreadystatechange = null;
          head.removeChild(script);
        }
      };
      head.appendChild(script);
    }
    function initJatos() {
      jatos.jQuery.when(
        // Load jQuery plugin to retry ajax calls: https://github.com/johnkpaul/jquery-ajax-retry
        jatos.jQuery.getScript("jatos-publix/javascripts/jquery.ajax-retry.min.js"),
        // Load JSON Patch library https://github.com/Starcounter-Jack/JSON-Patch
        jatos.jQuery.getScript("jatos-publix/javascripts/fast-json-patch.min.js")
      ).then(function() {
        jatos.studyResultUuid = window.location.pathname.split("/").reverse()[2];
        readIdCookie();
        heartbeatWorker = new Worker("jatos-publix/javascripts/heartbeat.js");
        heartbeatWorker.postMessage([jatos.studyResultUuid]);
        httpLoop.start();
      }).then(getInitData).then(showIdOverlay).then(openBatchChannelWithRetry).always(function() {
        initialized = true;
        readyForOnLoad();
      });
    }
    function readIdCookie() {
      const idCookieName = "JATOS_ID";
      const cookieRow = document.cookie.split("; ").filter((row) => row.includes(idCookieName)).find((row) => row.includes(jatos.studyResultUuid));
      if (!cookieRow) {
        console.error("readIdCookie: JATOS ID cookie for current studyResultUuid not found.");
        return;
      }
      const equalsIndex = cookieRow.indexOf("=");
      const idCookieValue = cookieRow.substring(equalsIndex + 1);
      if (!idCookieValue) {
        console.error(`readIdCookie: JATOS ID cookie value is empty for cookie "${cookieRow}".`);
        return;
      }
      const cookieParams = new URLSearchParams(idCookieValue);
      cookieParams.forEach((value, key) => jatos[key] = value);
      jatos.componentPos = parseInt(jatos.componentPos, 10);
    }
    function getInitData() {
      return jatos.jQuery.ajax({
        url: getURL("initData"),
        type: "GET",
        dataType: "json",
        timeout: jatos.httpTimeout,
        success: setInitData,
        error: (err) => console.error(getAjaxErrorMsg(err))
      }).retry({
        times: jatos.httpRetry,
        timeout: jatos.httpRetryWait
      });
    }
    function setInitData(initData) {
      jatos.batchProperties = initData.batchProperties;
      if (typeof jatos.batchProperties.batchInput != "undefined" && jatos.studyProperties.studyInput !== null) {
        jatos.batchJsonInput = jatos.jQuery.parseJSON(jatos.batchProperties.batchInput);
      } else {
        jatos.batchJsonInput = {};
      }
      jatos.batchInput = jatos.batchJsonInput;
      delete jatos.batchProperties.batchInput;
      try {
        jatos.studySessionData = JSON.parse(initData.studySessionData);
      } catch (e) {
        console.error(e.stack || e);
      }
      jatos.studyProperties = initData.studyProperties;
      if (typeof jatos.studyProperties.studyInput != "undefined" && jatos.studyProperties.studyInput !== null) {
        jatos.studyJsonInput = jatos.jQuery.parseJSON(jatos.studyProperties.studyInput);
      } else {
        jatos.studyJsonInput = {};
      }
      jatos.studyInput = jatos.studyJsonInput;
      delete jatos.studyProperties.studyInput;
      jatos.componentList = initData.componentList;
      jatos.studyLength = initData.componentList.length;
      jatos.componentProperties = initData.componentProperties;
      if (typeof jatos.componentProperties.componentInput != "undefined" && jatos.componentProperties.componentInput !== null) {
        jatos.componentJsonInput = jatos.jQuery.parseJSON(jatos.componentProperties.componentInput);
      } else {
        jatos.componentJsonInput = {};
      }
      jatos.componentInput = jatos.componentJsonInput;
      delete jatos.componentProperties.componentInput;
      jatos.urlQueryParameters = initData.urlQueryParameters;
      jatos.frameId = jatos.urlQueryParameters.frameId || void 0;
      jatos.studyCode = initData.studyCode;
    }
    jatos.onLoad = function(callback) {
      if (!jatosOnLoadEventFired) {
        window.addEventListener("jatosOnLoad", callback);
        readyForOnLoad();
      } else {
        callback();
      }
    };
    jatos.onload = jatos.onLoad;
    function readyForOnLoad() {
      if (!jatosOnLoadEventFired && initialized) {
        jatosOnLoadEventFired = true;
        window.dispatchEvent(jatosOnLoadEvent);
      }
    }
    jatos.onConnected = function(callback) {
      window.addEventListener("batchChannelAlive", callback);
    };
    jatos.onDisconnected = function(callback) {
      window.addEventListener("batchChannelDead", callback);
    };
    jatos.isConnected = function() {
      return batchChannelAlive;
    };
    function openBatchChannelWithRetry(backoffTime) {
      if (typeof backoffTime !== "number") backoffTime = jatos.channelOpeningBackoffTimeMin;
      return openBatchChannel().fail(function() {
        if (backoffTime < jatos.channelOpeningBackoffTimeMax) backoffTime *= 2;
        setTimeout(function() {
          openBatchChannelWithRetry(backoffTime);
        }, backoffTime);
      });
    }
    function openBatchChannel() {
      if (!webSocketSupported) {
        const errorMsg = "This browser does not support WebSockets. Can't open batch channel.";
        console.warn(errorMsg);
        return rejectedPromise(errorMsg);
      }
      if (batchChannel && batchChannel.readyState !== batchChannel.CLOSED) {
        return rejectedPromise("Can't open a WebSocket that is not in readyState CLOSED.");
      }
      if (endingStudy || startingComponent) {
        const errorMsg = "Won't open batch channel because study is about to move to the next component or finish.";
        console.info(errorMsg);
        return rejectedPromise(errorMsg);
      }
      if (studyRunInvalid) {
        const errorMsg = "Can't open batch channel. This study run is invalid.";
        console.warn(errorMsg);
        return rejectedPromise(errorMsg);
      }
      if (isDeferredPending(openingBatchChannelDeferred)) {
        const errorMsg = "Can open only one batch channel.";
        console.warn(errorMsg);
        return rejectedPromise(errorMsg);
      }
      openingBatchChannelDeferred = createDeferred();
      const channel = new WebSocket(
        (window.location.protocol === "https:" ? "wss://" : "ws://") + window.location.host + jatos.urlBasePath + "publix/" + jatos.studyResultUuid + "/batch/open"
      );
      batchChannel = channel;
      const openingDeferred = openingBatchChannelDeferred;
      channel.onopen = function() {
        if (batchChannel !== channel) return;
        channel.send('{"action":"READY"}');
        batchChannelHeartbeat();
        batchChannelClosedCheck();
      };
      channel.onmessage = function(event) {
        if (batchChannel !== channel) return;
        handleBatchMsg(event.data);
      };
      channel.onerror = function() {
        if (batchChannel !== channel) return;
        console.error("Batch channel error");
        openingDeferred.reject();
      };
      channel.onclose = function() {
        if (batchChannel !== channel) return;
        setBatchChannelDead();
        clearBatchChannel();
        openingDeferred.reject();
      };
      return openingBatchChannelDeferred.promise();
    }
    function reopenBatchChannel() {
      if (isDeferredPending(openingBatchChannelDeferred)) return;
      if (batchChannel instanceof WebSocket) batchChannel.close();
      clearBatchChannel();
      openBatchChannelWithRetry();
    }
    function batchChannelHeartbeat() {
      clearInterval(batchChannelHeartbeatTimer);
      batchChannelHeartbeatTimer = setInterval(function() {
        if (batchChannel.readyState === batchChannel.OPEN) {
          batchChannel.send('{"heartbeat":"ping"}');
          const timeout = setTimeout(
            handleBatchChannelHeartbeatFail,
            jatos.channelHeartbeatTimeoutTime
          );
          batchChannelHeartbeatTimeoutTimers.push(timeout);
        }
      }, jatos.channelHeartbeatInterval);
    }
    function handleBatchChannelHeartbeatFail() {
      console.warn("Batch channel heartbeat fail");
      setBatchChannelDead();
      reopenBatchChannel();
    }
    function batchChannelClosedCheck() {
      clearInterval(batchChannelClosedCheckTimer);
      batchChannelClosedCheckTimer = setInterval(function() {
        if (batchChannel.readyState === batchChannel.CLOSED) {
          console.info("Batch channel closed");
          clearInterval(batchChannelClosedCheckTimer);
          setBatchChannelDead();
          reopenBatchChannel();
        }
      }, jatos.channelClosedCheckInterval);
    }
    function clearBatchChannel() {
      batchSessionData = {};
      batchSessionVersion = null;
      clearBatchChannelHeartbeatTimeoutTimers();
      clearInterval(batchChannelHeartbeatTimer);
    }
    function clearBatchChannelHeartbeatTimeoutTimers() {
      batchChannelHeartbeatTimeoutTimers.forEach(function(timeout) {
        clearTimeout(timeout);
      });
      batchChannelHeartbeatTimeoutTimers = [];
    }
    function handleBatchMsg(msg) {
      let batchMsg;
      try {
        batchMsg = JSON.parse(msg);
      } catch (error) {
        console.error(error);
        return;
      }
      if (typeof batchMsg.heartbeat != "undefined" && batchMsg.heartbeat === "pong") {
        clearBatchChannelHeartbeatTimeoutTimers();
        setBatchChannelAlive();
        return;
      }
      if (typeof batchMsg.patches != "undefined") {
        const patchResults = jsonpatch.applyPatch(batchSessionData, batchMsg.patches);
        if (patchResults && patchResults.newDocument !== void 0) {
          batchSessionData = patchResults.newDocument;
        }
      }
      if (typeof batchMsg.data != "undefined") {
        if (batchMsg.data === null) {
          batchSessionData = {};
        } else {
          batchSessionData = batchMsg.data;
        }
      }
      if (typeof batchMsg.version != "undefined") {
        batchSessionVersion = batchMsg.version;
        if (isDeferredPending(openingBatchChannelDeferred)) {
          console.info("Batch channel opened");
          openingBatchChannelDeferred.resolve();
        }
      }
      if (typeof batchMsg.action != "undefined") {
        handleBatchAction(batchMsg);
      }
    }
    function handleBatchAction(batchMsg) {
      switch (batchMsg.action) {
        case "OPENED":
          setBatchChannelAlive();
          break;
        case "SESSION":
          batchMsg.patches.forEach(function(patch) {
            callWithArgs(onJatosBatchSession, patch.path, patch.op);
          });
          break;
        case "SESSION_ACK":
          if (batchSessionTimeouts.hasOwnProperty(batchMsg.id)) {
            batchSessionTimeouts[batchMsg.id].cancel("Batch session update successful");
          } else {
            console.error("Batch session got 'SESSION_ACK' with nonexistent ID " + batchMsg.id);
          }
          break;
        case "SESSION_FAIL":
          if (batchSessionTimeouts.hasOwnProperty(batchMsg.id)) {
            const errorMsg = batchMsg.errorMsg || "Batch session update failed";
            batchSessionTimeouts[batchMsg.id].trigger(errorMsg);
          } else {
            console.error("Batch session got 'SESSION_FAIL' with nonexistent ID " + batchMsg.id);
          }
          break;
        case "CLOSED":
          clearInterval(batchChannelClosedCheckTimer);
          setBatchChannelDead();
          studyRunInvalid = true;
          console.info("Batch channel closed by JATOS server");
          jatos.showOverlay({ text: "This study run is invalid.", showImg: false });
          break;
        case "ERROR":
          console.error(batchMsg.errorMsg);
          break;
      }
    }
    function setBatchChannelAlive() {
      if (!batchChannelAlive) {
        batchChannelAlive = true;
        window.dispatchEvent(batchChannelAliveEvent);
      }
    }
    function setBatchChannelDead() {
      if (batchChannelAlive) {
        batchChannelAlive = false;
        window.dispatchEvent(batchChannelDeadEvent);
      }
    }
    jatos.batchSession = {};
    jatos.batchSession.get = function(name) {
      const obj = jsonpatch.getValueByPointer(batchSessionData, "/" + name);
      return cloneJsonObj(obj);
    };
    jatos.batchSession.getAll = function() {
      const obj = jatos.batchSession.find("");
      return cloneJsonObj(obj);
    };
    jatos.batchSession.find = function(path) {
      const obj = jsonpatch.getValueByPointer(batchSessionData, path);
      return cloneJsonObj(obj);
    };
    jatos.batchSession.test = function(path, value) {
      const obj = jsonpatch.getValueByPointer(batchSessionData, path);
      return obj === value;
    };
    jatos.batchSession.defined = function(path) {
      return !jatos.batchSession.test(path, void 0);
    };
    jatos.batchSession.add = function(path, value, onSuccess, onFail) {
      const patch = generatePatch("add", path, value, null);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos.batchSession.set = function(name, value, onSuccess, onFail) {
      const patch = generatePatch("add", "/" + name, value, null);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos.batchSession.setAll = function(value, onSuccess, onFail) {
      return jatos.batchSession.replace("", value, onSuccess, onFail);
    };
    jatos.batchSession.remove = function(path, onSuccess, onFail) {
      const patch = generatePatch("remove", path, null, null);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos.batchSession.clear = function(onSuccess, onFail) {
      const patch = generatePatch("replace", "", {}, null);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos.batchSession.replace = function(path, value, onSuccess, onFail) {
      const patch = generatePatch("replace", path, value, null);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos.batchSession.copy = function(from, path, onSuccess, onFail) {
      const patch = generatePatch("copy", path, null, from);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos.batchSession.move = function(from, path, onSuccess, onFail) {
      const patch = generatePatch("move", path, null, from);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    function generatePatch(op, path, value, from) {
      const patch = {};
      patch.op = op;
      if (path !== null) {
        patch.path = path;
      }
      if (value !== null) {
        patch.value = value;
      }
      if (from !== null) {
        patch.from = from;
      }
      return patch;
    }
    function sendBatchSessionPatch(patches, onSuccess, onFail) {
      if (!batchChannel || batchChannel.readyState !== batchChannel.OPEN) {
        const errorMsg = `Can't send batch session patch. No open batch channel. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.error);
        return rejectedPromise(errorMsg);
      }
      if (jatos.batchSessionVersioning && isDeferredPending(sendingBatchSessionDeferred)) {
        const errorMsg = `Can send only one batch session patch at a time. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.error);
        return rejectedPromise(errorMsg);
      }
      if (studyRunInvalid) {
        const errorMsg = `Can't send batch session patch. This study run is invalid. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.warn);
        return rejectedPromise(errorMsg);
      }
      const deferred = createDeferred();
      if (jatos.batchSessionVersioning) sendingBatchSessionDeferred = deferred;
      const sessionActionId = batchSessionCounter++;
      const msgObj = {};
      msgObj.action = "SESSION";
      msgObj.id = sessionActionId;
      msgObj.patches = patches.constructor === Array ? patches : [patches];
      msgObj.version = batchSessionVersion;
      msgObj.versioning = !!jatos.batchSessionVersioning;
      try {
        batchChannel.send(JSON.stringify(msgObj));
        setChannelSendingTimeoutAndPromiseResolution(
          deferred,
          batchSessionTimeouts,
          sessionActionId,
          onSuccess,
          onFail
        );
      } catch (error) {
        callMany(error, onFail, console.error);
        deferred.reject();
      }
      return deferred.promise();
    }
    jatos.setHeartbeatPeriod = function(heartbeatPeriod) {
      if (typeof heartbeatPeriod == "number" && heartbeatWorker) {
        heartbeatWorker.postMessage([jatos.studyResultUuid, heartbeatPeriod]);
      }
    };
    jatos.onBatchSession = function(onBatchSession) {
      onJatosBatchSession = onBatchSession;
    };
    jatos.onError = function(onError) {
      console.warn("jatos.onError is abolished - use the specific function's error callback or Promise function");
    };
    installResultDataApi(jatos, {
      createDeferred,
      getURL,
      isInitialized: () => initialized,
      isInvalidComponentPosition: (pos) => isInvalidComponentPosition(jatos.componentList, pos),
      isStudyRunInvalid: () => studyRunInvalid,
      rejectedPromise,
      sendToHttpLoop: httpLoop.send
    });
    jatos.setStudySessionData = function(studySessionData, onSuccess, onFail) {
      jatos.studySessionData = studySessionData;
      const studySessionDataStr = JSON.stringify(studySessionData);
      const request = {
        url: getURL("../studySessionData"),
        data: studySessionDataStr,
        method: "POST",
        contentType: "text/plain; charset=UTF-8",
        timeout: jatos.httpTimeout,
        retry: jatos.httpRetry,
        retryWait: jatos.httpRetryWait
      };
      return httpLoop.send(request, onSuccess, onFail).promise();
    };
    installComponentNavigationApi(jatos, {
      beforeUnloadWarning,
      getURL,
      httpLoop,
      isEndingStudy: () => endingStudy,
      isInitialized: () => initialized,
      isStartingComponent: () => startingComponent,
      isStudyRunInvalid: () => studyRunInvalid,
      setStartingComponent: (value) => {
        startingComponent = value;
      }
    });
    jatos.joinGroup = function(callbacks) {
      groupChannelCallbacks = callbacks ? callbacks : {};
      return openGroupChannel();
    };
    function openGroupChannel() {
      if (!webSocketSupported) {
        const errorMsg = "This browser does not support WebSockets.";
        callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
        return rejectedPromise(errorMsg);
      }
      if (groupChannel && groupChannel.readyState !== groupChannel.CLOSED) {
        return rejectedPromise("Can't open a WebSocket that is not in readyState CLOSED.");
      }
      if (endingStudy || startingComponent) {
        const errorMsg = "Won't open group channel because study is about to move to the next component or finish.";
        callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
        return rejectedPromise(errorMsg);
      }
      if (studyRunInvalid) {
        const errorMsg = "Can't open group channel. This study run is invalid.";
        callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
        return rejectedPromise(errorMsg);
      }
      if (isDeferredPending(openingGroupChannelDeferred)) {
        const errorMsg = "Can open only one group channel";
        callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
        return rejectedPromise(errorMsg);
      }
      if (isDeferredPending(leavingGroupDeferred)) {
        const errorMsg = "Can't open group channel while leaving a group";
        callMany(errorMsg, console.error, groupChannelCallbacks.onError);
        return rejectedPromise(errorMsg);
      }
      if (isDeferredPending(reassigningGroupDeferred)) {
        const errorMsg = "Can't open group channel while reassigning a group";
        callMany(errorMsg, console.error, groupChannelCallbacks.onError);
        return rejectedPromise(errorMsg);
      }
      openingGroupChannelDeferred = createDeferred();
      groupChannel = new WebSocket(
        (window.location.protocol === "https:" ? "wss://" : "ws://") + window.location.host + jatos.urlBasePath + "publix/" + jatos.studyResultUuid + "/group/join"
      );
      groupChannel.onopen = function() {
        groupChannel.send('{"action":"READY"}');
        groupChannelHeartbeat();
        groupChannelClosedCheck();
      };
      groupChannel.onmessage = function(event) {
        handleGroupMsg(event.data);
      };
      groupChannel.onerror = function() {
        callMany("Group channel error", console.error, groupChannelCallbacks.onError);
        openingGroupChannelDeferred.reject();
      };
      groupChannel.onclose = function() {
        clearGroupChannel();
        call(groupChannelCallbacks.onClose);
        openingGroupChannelDeferred.reject();
      };
      return openingGroupChannelDeferred.promise();
    }
    function openGroupChannelWithRetry(backoffTime) {
      if (typeof backoffTime !== "number") backoffTime = jatos.channelOpeningBackoffTimeMin;
      openGroupChannel().fail(function() {
        if (backoffTime < jatos.channelOpeningBackoffTimeMax) backoffTime *= 2;
        setTimeout(function() {
          openGroupChannelWithRetry(backoffTime);
        }, backoffTime);
      });
    }
    function reopenGroupChannel() {
      if (isDeferredPending(openingGroupChannelDeferred) || isDeferredPending(reassigningGroupDeferred) || isDeferredPending(leavingGroupDeferred)) {
        return;
      }
      if (groupChannel && groupChannel.readyState !== groupChannel.CLOSED) {
        groupChannel.close();
      }
      clearGroupChannel();
      openGroupChannelWithRetry();
    }
    function groupChannelHeartbeat() {
      clearInterval(groupChannelHeartbeatTimer);
      groupChannelHeartbeatTimer = setInterval(function() {
        if (groupChannel.readyState === groupChannel.OPEN) {
          groupChannel.send('{"heartbeat":"ping"}');
          const timeout = setTimeout(function() {
            callMany("Group channel heartbeat fail", groupChannelCallbacks.onError, console.warn);
            reopenGroupChannel();
          }, jatos.channelHeartbeatTimeoutTime);
          groupChannelHeartbeatTimeoutTimers.push(timeout);
        }
      }, jatos.channelHeartbeatInterval);
    }
    function groupChannelClosedCheck() {
      clearInterval(groupChannelClosedCheckTimer);
      groupChannelClosedCheckTimer = setInterval(function() {
        if (groupChannel.readyState === groupChannel.CLOSED) {
          callMany("Group channel closed", console.info, groupChannelCallbacks.onError);
          clearInterval(groupChannelClosedCheckTimer);
          reopenGroupChannel();
        }
      }, jatos.channelClosedCheckInterval);
    }
    function clearGroupChannelHeartbeatTimeoutTimers() {
      groupChannelHeartbeatTimeoutTimers.forEach(function(timeout) {
        clearTimeout(timeout);
      });
      groupChannelHeartbeatTimeoutTimers = [];
    }
    function clearGroupChannel() {
      jatos.groupMemberId = null;
      jatos.groupResultId = null;
      jatos.groupMembers = [];
      jatos.groupChannels = [];
      groupSessionData = {};
      groupSessionVersion = null;
      groupState = null;
      clearGroupChannelHeartbeatTimeoutTimers();
      clearInterval(groupChannelHeartbeatTimer);
    }
    function handleGroupMsg(msg) {
      let groupMsg;
      try {
        groupMsg = JSON.parse(msg);
      } catch (error) {
        callMany(error, groupChannelCallbacks.onError, console.error);
        return;
      }
      if (typeof groupMsg.heartbeat != "undefined") {
        clearGroupChannelHeartbeatTimeoutTimers();
        return;
      }
      updateGroupVars(groupMsg);
      callGroupActionCallbacks(groupMsg);
      if (groupMsg.msg && groupChannelCallbacks.onMessage) {
        groupChannelCallbacks.onMessage(groupMsg.msg);
      }
    }
    function updateGroupVars(groupMsg) {
      if (typeof groupMsg.groupState != "undefined") {
        groupState = groupMsg.groupState;
      }
      if (typeof groupMsg.groupResultId != "undefined") {
        jatos.groupResultId = groupMsg.groupResultId.toString();
        showIdOverlay();
        jatos.groupMemberId = jatos.studyResultId;
      }
      if (groupMsg.action === "OPENED" && typeof groupMsg.members != "undefined") {
        jatos.groupMembers = groupMsg.members;
      } else if (typeof groupMsg.memberId != "undefined" && groupMsg.action === "JOINED" && !jatos.groupMembers.includes(groupMsg.memberId)) {
        jatos.groupMembers.push(groupMsg.memberId);
      } else if (typeof groupMsg.memberId != "undefined" && groupMsg.action === "LEFT") {
        jatos.groupMembers = jatos.groupMembers.filter(function(memberId) {
          return memberId !== groupMsg.memberId;
        });
      }
      if (groupMsg.action === "OPENED" && typeof groupMsg.channels != "undefined") {
        jatos.groupChannels = groupMsg.channels;
      } else if (typeof groupMsg.memberId != "undefined" && groupMsg.action === "CHANNEL_OPENED" && !jatos.groupChannels.includes(groupMsg.memberId)) {
        jatos.groupChannels.push(groupMsg.memberId);
      } else if (typeof groupMsg.memberId != "undefined" && (groupMsg.action === "CHANNEL_CLOSED" || groupMsg.action === "CLOSED")) {
        jatos.groupChannels = jatos.groupChannels.filter(function(memberId) {
          return memberId !== groupMsg.memberId;
        });
      }
      if (typeof groupMsg.sessionPatches != "undefined") {
        const patchResults = jsonpatch.applyPatch(groupSessionData, groupMsg.sessionPatches);
        if (patchResults && patchResults.newDocument !== void 0) {
          groupSessionData = patchResults.newDocument;
        }
      }
      if (typeof groupMsg.sessionData != "undefined") {
        if (groupMsg.sessionData === null) {
          groupSessionData = {};
        } else {
          groupSessionData = groupMsg.sessionData;
        }
      }
      if (typeof groupMsg.sessionVersion != "undefined") {
        groupSessionVersion = groupMsg.sessionVersion;
        if (isDeferredPending(openingGroupChannelDeferred)) {
          console.info("Group channel opened");
          openingGroupChannelDeferred.resolve();
        }
      }
    }
    function callGroupActionCallbacks(groupMsg) {
      if (!groupMsg.action) {
        return;
      }
      switch (groupMsg.action) {
        case "OPENED":
          callWithArgs(groupChannelCallbacks.onOpen, groupMsg.memberId);
          break;
        case "CLOSED":
          clearInterval(groupChannelClosedCheckTimer);
          console.info("Group channel closed by JATOS server");
          break;
        case "CHANNEL_OPENED":
          callWithArgs(groupChannelCallbacks.onMemberOpen, groupMsg.memberId);
          call(groupChannelCallbacks.onUpdate);
          break;
        case "CHANNEL_CLOSED":
          callWithArgs(groupChannelCallbacks.onMemberClose, groupMsg.memberId);
          call(groupChannelCallbacks.onUpdate);
          break;
        case "JOINED":
          if (groupMsg.memberId !== jatos.groupMemberId) {
            callWithArgs(groupChannelCallbacks.onMemberJoin, groupMsg.memberId);
            call(groupChannelCallbacks.onUpdate);
          }
          break;
        case "LEFT":
          if (groupMsg.memberId !== jatos.groupMemberId) {
            callWithArgs(groupChannelCallbacks.onMemberLeave, groupMsg.memberId);
            call(groupChannelCallbacks.onUpdate);
          }
          break;
        case "SESSION":
          groupMsg.sessionPatches.forEach(function(patch) {
            callWithArgs(groupChannelCallbacks.onGroupSession, patch.path, patch.op);
          });
          call(groupChannelCallbacks.onUpdate);
          break;
        case "FIXED":
          if (groupFixedTimeout) {
            groupFixedTimeout.cancel();
          }
          call(groupChannelCallbacks.onUpdate);
          break;
        case "SESSION_ACK":
          if (groupSessionTimeouts.hasOwnProperty(groupMsg.sessionActionId)) {
            groupSessionTimeouts[groupMsg.sessionActionId].cancel("Group session update successful");
          } else {
            console.warn("Group session got 'SESSION_ACK' with nonexistent ID " + groupMsg.sessionActionId);
          }
          break;
        case "SESSION_FAIL":
          if (groupSessionTimeouts.hasOwnProperty(groupMsg.sessionActionId)) {
            const errorMsg = groupMsg.errorMsg || "Group session update failed";
            groupSessionTimeouts[groupMsg.sessionActionId].trigger(errorMsg);
          } else {
            console.warn("Group session got 'SESSION_FAIL' with nonexistent ID " + groupMsg.sessionActionId);
          }
          break;
        case "ERROR":
          callMany(groupMsg.errorMsg, groupChannelCallbacks.onError, console.error);
          break;
      }
    }
    jatos.getGroupState = function() {
      return groupState;
    };
    jatos.isGroupFixed = function() {
      return groupState === "FIXED";
    };
    jatos.groupSession = {};
    jatos.groupSession.get = function(name) {
      const obj = jsonpatch.getValueByPointer(groupSessionData, "/" + name);
      return cloneJsonObj(obj);
    };
    jatos.groupSession.getAll = function() {
      const obj = jatos.groupSession.find("");
      return cloneJsonObj(obj);
    };
    jatos.groupSession.find = function(path) {
      const obj = jsonpatch.getValueByPointer(groupSessionData, path);
      return cloneJsonObj(obj);
    };
    jatos.groupSession.test = function(path, value) {
      const obj = jsonpatch.getValueByPointer(groupSessionData, path);
      return obj === value;
    };
    jatos.groupSession.defined = function(path) {
      return !jatos.groupSession.test(path, void 0);
    };
    jatos.groupSession.add = function(path, value, onSuccess, onFail) {
      const patch = generatePatch("add", path, value, null);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos.groupSession.set = function(name, value, onSuccess, onFail) {
      const patch = generatePatch("add", "/" + name, value, null);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos.groupSession.setAll = function(value, onSuccess, onFail) {
      return jatos.groupSession.replace("", value, onSuccess, onFail);
    };
    jatos.groupSession.remove = function(path, onSuccess, onFail) {
      const patch = generatePatch("remove", path, null, null);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos.groupSession.clear = function(onSuccess, onFail) {
      const patch = generatePatch("replace", "", {}, null);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos.groupSession.replace = function(path, value, onSuccess, onFail) {
      const patch = generatePatch("replace", path, value, null);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos.groupSession.copy = function(from, path, onSuccess, onFail) {
      const patch = generatePatch("copy", path, null, from);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos.groupSession.move = function(from, path, onSuccess, onFail) {
      const patch = generatePatch("move", path, null, from);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    function sendGroupSessionPatch(patches, onSuccess, onFail) {
      if (!groupChannel || groupChannel.readyState !== groupChannel.OPEN) {
        const errorMsg = `Can't send group session patch. No open group channel. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.error);
        return rejectedPromise(errorMsg);
      }
      if (jatos.groupSessionVersioning && isDeferredPending(sendingGroupSessionDeferred)) {
        const errorMsg = `Can send only one group session patch at a time. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.error);
        return rejectedPromise(errorMsg);
      }
      if (studyRunInvalid) {
        const errorMsg = `Can't send group session patch. This study run is invalid. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.warn);
        return rejectedPromise(errorMsg);
      }
      const deferred = createDeferred();
      if (jatos.groupSessionVersioning) sendingGroupSessionDeferred = deferred;
      const sessionActionId = groupSessionCounter++;
      const msgObj = {};
      msgObj.action = "SESSION";
      msgObj.sessionActionId = sessionActionId;
      msgObj.sessionPatches = patches.constructor === Array ? patches : [patches];
      msgObj.sessionVersion = groupSessionVersion;
      msgObj.sessionVersioning = !!jatos.groupSessionVersioning;
      try {
        groupChannel.send(JSON.stringify(msgObj));
        setChannelSendingTimeoutAndPromiseResolution(
          deferred,
          groupSessionTimeouts,
          sessionActionId,
          onSuccess,
          onFail
        );
      } catch (error) {
        callMany(error, onFail, console.error);
        deferred.reject();
      }
      return deferred.promise();
    }
    jatos.setGroupFixed = function(onSuccess, onFail) {
      if (!groupChannel || groupChannel.readyState !== groupChannel.OPEN) {
        const errorMsg = "Can't fix group. No open group channel.";
        callMany(errorMsg, onFail, console.error);
        return rejectedPromise(errorMsg);
      }
      if (isDeferredPending(sendingGroupFixedDeferred)) {
        const errorMsg = "Can fix group only once.";
        callMany(errorMsg, onFail, console.warn);
        return rejectedPromise(errorMsg);
      }
      if (studyRunInvalid) {
        const errorMsg = "Can't fix group. This study run is invalid.";
        callMany(errorMsg, onFail, console.warn);
        return rejectedPromise(errorMsg);
      }
      sendingGroupFixedDeferred = createDeferred();
      const msgObj = {};
      msgObj.action = "FIXED";
      try {
        groupChannel.send(JSON.stringify(msgObj));
        setGroupFixedTimeoutAndPromiseResolution(
          sendingGroupFixedDeferred,
          onSuccess,
          onFail
        );
      } catch (error) {
        callMany(error, onFail, console.error);
        sendingGroupFixedDeferred.reject();
      }
      return sendingGroupFixedDeferred.promise();
    };
    function setGroupFixedTimeoutAndPromiseResolution(deferred, onSuccess, onFail) {
      const timeoutId = setTimeout(() => {
        callWithArgs(onFail, "Timeout sending message");
        deferred.reject("Timeout sending message");
      }, jatos.channelSendingTimeoutTime);
      groupFixedTimeout = {
        cancel: () => {
          clearTimeout(timeoutId);
          callWithArgs(onSuccess, "success");
          deferred.resolve("success");
        }
      };
      deferred.always(() => {
        groupFixedTimeout = null;
      });
    }
    jatos.hasJoinedGroup = function() {
      return jatos.groupResultId !== null;
    };
    jatos.hasOpenGroupChannel = function() {
      return groupChannel && groupChannel.readyState === groupChannel.OPEN;
    };
    jatos.isMaxActiveMemberReached = function() {
      if (!jatos.batchProperties || jatos.batchProperties.maxActiveMembers === null) {
        return false;
      } else {
        return jatos.groupMembers.length >= jatos.batchProperties.maxActiveMembers;
      }
    };
    jatos.isMaxActiveMemberOpen = function() {
      if (!jatos.batchProperties || jatos.batchProperties.maxActiveMembers === null) {
        return false;
      } else {
        return jatos.groupChannels.length >= jatos.batchProperties.maxActiveMembers;
      }
    };
    jatos.isGroupOpen = function() {
      if (groupChannel && groupChannel.readyState === groupChannel.OPEN) {
        return jatos.groupMembers.length === jatos.groupChannels.length;
      } else {
        return false;
      }
    };
    jatos.sendGroupMsg = function(msg) {
      if (groupChannel && groupChannel.readyState === groupChannel.OPEN) {
        const msgObj = {};
        msgObj.msg = msg;
        groupChannel.send(JSON.stringify(msgObj));
      }
    };
    jatos.sendGroupMsgTo = function(recipient, msg) {
      if (groupChannel && groupChannel.readyState === groupChannel.OPEN) {
        const msgObj = {};
        msgObj.recipient = recipient;
        msgObj.msg = msg;
        groupChannel.send(JSON.stringify(msgObj));
      }
    };
    jatos.reassignGroup = function(onSuccess, onFail) {
      if (isDeferredPending(openingGroupChannelDeferred)) {
        const errorMsg = "Can't reassign a group if not joined yet.";
        callMany(errorMsg, console.error, onFail);
        return rejectedPromise(errorMsg);
      }
      if (isDeferredPending(leavingGroupDeferred)) {
        const errorMsg = "Can't reassign a group during leaving.";
        callMany(errorMsg, console.error, onFail);
        return rejectedPromise(errorMsg);
      }
      if (isDeferredPending(reassigningGroupDeferred)) {
        const errorMsg = "Can't reassign a group twice at the same time.";
        callMany(errorMsg, console.warn, onFail);
        return rejectedPromise(errorMsg);
      }
      if (!groupChannel || groupChannel.readyState !== groupChannel.OPEN) {
        const errorMsg = "Can't reassign group. Group channel not open.";
        callMany(errorMsg, console.error, onFail);
        return rejectedPromise(errorMsg);
      }
      if (studyRunInvalid) {
        const errorMsg = "Can't reassign group. This study run is invalid.";
        callMany(errorMsg, console.warn, onFail);
        return rejectedPromise(errorMsg);
      }
      reassigningGroupDeferred = createDeferred();
      jatos.jQuery.ajax({
        url: getURL("../group/reassign"),
        processData: false,
        type: "GET",
        timeout: jatos.httpTimeout,
        statusCode: {
          200: function() {
            call(onSuccess);
            reassigningGroupDeferred.resolve();
          },
          204: function() {
            call(onFail);
            reassigningGroupDeferred.reject();
          }
        },
        error: function(err) {
          const errMsg = getAjaxErrorMsg(err);
          callMany(errMsg, console.error, onFail);
          reassigningGroupDeferred.reject(errMsg);
        }
      });
      return reassigningGroupDeferred.promise();
    };
    jatos.leaveGroup = function(onSuccess, onError) {
      if (isDeferredPending(openingGroupChannelDeferred)) {
        const errorMsg = "Can't leave group if not joined yet.";
        callMany(errorMsg, onError, console.error);
        return rejectedPromise(errorMsg);
      }
      if (isDeferredPending(reassigningGroupDeferred)) {
        const errorMsg = "Can't leave group during reassigning.";
        callMany(errorMsg, onError, console.error);
        return rejectedPromise(errorMsg);
      }
      if (isDeferredPending(leavingGroupDeferred)) {
        const errorMsg = "Can leave only once.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      if (studyRunInvalid) {
        const errorMsg = "Can't leave group. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      leavingGroupDeferred = createDeferred();
      jatos.jQuery.ajax({
        url: getURL("../group/leave"),
        processData: false,
        type: "GET",
        timeout: jatos.httpTimeout,
        success: function(response) {
          clearInterval(groupChannelClosedCheckTimer);
          callWithArgs(onSuccess, response);
          leavingGroupDeferred.resolve(response);
        },
        error: function(err) {
          var errMsg = getAjaxErrorMsg(err);
          callMany(errMsg, onError, console.error);
          leavingGroupDeferred.reject(errMsg);
        }
      }).retry({
        times: jatos.httpRetry,
        timeout: jatos.httpRetryWait
      });
      return leavingGroupDeferred.promise();
    };
    jatos.abortStudyWithoutRedirect = function(message, onSuccess, onError) {
      if (!initialized) {
        const errorMsg = "jatos.js not yet initialized.";
        callMany(errorMsg, onError, console.error);
        return rejectedPromise(errorMsg);
      }
      if (studyRunInvalid) {
        const errorMsg = "Can't abort study. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      if (endingStudy) {
        const errorMsg = "Can end/abort study only once.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      endingStudy = true;
      var url = getURL("../abort");
      if (typeof message != "undefined") {
        url = url + "?message=" + message;
      }
      var request = {
        url,
        method: "GET",
        timeout: jatos.httpTimeout,
        retry: jatos.httpRetry,
        retryWait: jatos.httpRetryWait
      };
      jatos.showBeforeUnloadWarning(false);
      var deferred = httpLoop.send(request, onSuccess, onError);
      setTimeout(function() {
        if (httpLoop.isBusy() && isDeferredPending(deferred)) {
          jatos.showOverlay(jatos.waitSendDataOverlayConfig);
        }
      }, 1e3);
      deferred.done(function() {
        window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        heartbeatWorker.terminate();
        httpLoop.terminate();
        clearInterval(batchChannelClosedCheckTimer);
        clearInterval(groupChannelClosedCheckTimer);
      });
      deferred.always(jatos.removeOverlays);
      return deferred.promise();
    };
    jatos.abortStudyAjax = function(message, onSuccess, onError) {
      return jatos.abortStudyWithoutRedirect(message, onSuccess, onError);
    };
    jatos.abortStudyAndRedirect = function(url, message, onSuccess, onError) {
      jatos.abortStudyWithoutRedirect(message, onSuccess, onError).done(function() {
        window.location.href = url;
      });
    };
    jatos.abortStudy = function(message, showEndPage = true) {
      if (studyRunInvalid) {
        console.warn("Can't abort study. This study run is invalid.");
        return;
      }
      if (!showEndPage) {
        return jatos.abortStudyWithoutRedirect(message);
      }
      const isInIframe = window.self !== window.top;
      if (isInIframe && jatos.workerType === "Jatos" && parent.onIframeComplete) {
        return jatos.abortStudyWithoutRedirect(message).done(() => parent.onIframeComplete(jatos.urlQueryParameters.frameId, jatos.studyId));
      }
      if (endingStudy) {
        console.warn("Can end/abort study only once");
        return;
      }
      endingStudy = true;
      function abort() {
        window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        var url = getURL("../abort");
        if (typeof message == "undefined") {
          window.location.href = url;
        } else {
          window.location.href = url + "?message=" + message;
        }
      }
      if (httpLoop.isBusy()) {
        setTimeout(jatos.showOverlay, 1e3, jatos.waitSendDataOverlayConfig);
      }
      httpLoop.whenIdle(abort);
    };
    jatos.endStudyWithoutRedirect = function(param1, param2, param3, param4, param5) {
      if (!initialized) {
        const errorMsg = "jatos.js not yet initialized.";
        console.error(errorMsg);
        return rejectedPromise(errorMsg);
      }
      var resultData, successful, message, onSuccess, onError;
      if (typeof param1 === "string" || typeof param1 === "object") {
        resultData = param1;
        successful = param2;
        message = param3;
        onSuccess = param4;
        onError = param5;
      } else if (typeof param1 === "boolean") {
        successful = param1;
        message = param2;
        onSuccess = param3;
        onError = param4;
      }
      if (studyRunInvalid) {
        const errorMsg = "Can't end study. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      if (endingStudy) {
        const errorMsg = "Can end/abort study only once.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      endingStudy = true;
      if (resultData) jatos.appendResultData(resultData);
      var url = getURL("../end");
      if (typeof successful == "boolean" && typeof message == "string") {
        url = url + "?" + jatos.jQuery.param({
          "successful": successful,
          "message": message
        });
      } else if (typeof successful == "boolean" && typeof message != "string") {
        url = url + "?" + jatos.jQuery.param({
          "successful": successful
        });
      } else if (typeof successful != "boolean" && typeof message == "string") {
        url = url + "?" + jatos.jQuery.param({
          "message": message
        });
      }
      var request = {
        url,
        method: "GET",
        timeout: jatos.httpTimeout,
        retry: jatos.httpRetry,
        retryWait: jatos.httpRetryWait
      };
      jatos.showBeforeUnloadWarning(false);
      var deferred = httpLoop.send(request, onSuccess, onError);
      setTimeout(function() {
        if (httpLoop.isBusy() && isDeferredPending(deferred)) {
          jatos.showOverlay(jatos.waitSendDataOverlayConfig);
        }
      }, 1e3);
      deferred.done(function() {
        window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        heartbeatWorker.terminate();
        httpLoop.terminate();
        clearInterval(batchChannelClosedCheckTimer);
        clearInterval(groupChannelClosedCheckTimer);
      });
      deferred.always(jatos.removeOverlays);
      return deferred.promise();
    };
    jatos.endStudyAjax = function(param1, param2, param3, param4, param5) {
      return jatos.endStudyWithoutRedirect(param1, param2, param3, param4, param5);
    };
    jatos.endStudyAndRedirect = function(url, param1, param2, param3, param4, param5) {
      jatos.endStudyWithoutRedirect(param1, param2, param3, param4, param5).done(function() {
        window.location.href = url;
      });
    };
    jatos.endStudy = function(param1, param2, param3, param4) {
      if (!initialized) {
        console.error("jatos.js not yet initialized");
        return;
      }
      if (studyRunInvalid) {
        console.warn("Can't end study. This study run is invalid.");
        return;
      }
      var resultData, successful, message, showEndPage;
      if (typeof param1 === "string" || typeof param1 === "object") {
        resultData = param1;
        successful = param2;
        message = param3;
        showEndPage = param4;
      } else if (typeof param1 === "boolean") {
        successful = param1;
        message = param2;
        showEndPage = param3;
      }
      if (typeof showEndPage !== "undefined" && !showEndPage) {
        if (resultData) {
          return jatos.endStudyWithoutRedirect(resultData, successful, message);
        } else {
          return jatos.endStudyWithoutRedirect(successful, message);
        }
      }
      const isInIframe = window.self !== window.top;
      if (isInIframe && jatos.workerType === "Jatos" && parent.onIframeComplete) {
        const endIframe = () => {
          parent.onIframeComplete(jatos.urlQueryParameters.frameId, jatos.studyId);
        };
        if (resultData) {
          jatos.endStudyWithoutRedirect(resultData, successful, message).done(endIframe);
        } else {
          jatos.endStudyWithoutRedirect(successful, message).done(endIframe);
        }
        return;
      }
      if (endingStudy) {
        console.warn("Can end/abort study only once");
        return;
      }
      endingStudy = true;
      if (resultData) jatos.appendResultData(resultData);
      function end() {
        window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        var url = getURL("../end");
        if (typeof successful == "boolean" && typeof message == "string") {
          url = url + "?" + jatos.jQuery.param({
            "successful": successful,
            "message": message
          });
        } else if (typeof successful == "boolean" && typeof message != "string") {
          url = url + "?" + jatos.jQuery.param({
            "successful": successful
          });
        } else if (typeof successful != "boolean" && typeof message == "string") {
          url = url + "?" + jatos.jQuery.param({
            "message": message
          });
        }
        window.location.href = url;
      }
      if (httpLoop.isBusy()) {
        setTimeout(jatos.showOverlay, 1e3, jatos.waitSendDataOverlayConfig);
      }
      httpLoop.whenIdle(end);
    };
    function getURL(path) {
      return new URL(path, window.location.href).toString();
    }
    jatos.getHttpLoopCounter = function() {
      return httpLoop.getCounter();
    };
    jatos.logError = function(logErrorMsg) {
      console.warn("jatos.logError is abolished - use jatos.log instead");
    };
    jatos.log = function(logMsg) {
      if (!initialized) return;
      var request = {
        url: getURL("log"),
        method: "POST",
        data: logMsg,
        contentType: "text/plain; charset=UTF-8",
        timeout: jatos.httpTimeout,
        retry: jatos.httpRetry,
        retryWait: jatos.httpRetryWait
      };
      httpLoop.send(request);
    };
    jatos.catchAndLogErrors = function() {
      window.addEventListener("error", function(e) {
        jatos.log(`Via 'error' event in ${e.filename}:${e.lineno} - ${e.message}`);
      });
      window.addEventListener("unhandledrejection", function(e) {
        jatos.log(`Via 'unhandledrejection' event in ${e.filename}:${e.lineno} - ${e.message}`);
      });
      var errorLog = console.error;
      var warnLog = console.warn;
      console.error = function(message) {
        jatos.log("Via console.error - " + message);
        errorLog.apply(this, arguments);
      };
      console.warn = function(message) {
        jatos.log("Via console.warn - " + message);
        warnLog.apply(this, arguments);
      };
    };
    jatos.addJatosIds = function(obj = {}) {
      obj.studyCode = jatos.studyCode;
      obj.studyId = jatos.studyId;
      obj.studyTitle = jatos.studyProperties.title;
      obj.batchId = jatos.batchId;
      obj.batchTitle = jatos.batchProperties.title;
      obj.componentId = jatos.componentId;
      obj.componentPos = jatos.componentPos;
      obj.componentTitle = jatos.componentProperties.title;
      obj.workerId = jatos.workerId;
      obj.studyResultId = jatos.studyResultId;
      obj.componentResultId = jatos.componentResultId;
      obj.groupResultId = jatos.groupResultId;
      obj.groupMemberId = jatos.groupMemberId;
      return obj;
    };
    jatos.onLoad(function() {
      if (showBeforeUnloadWarning && !jatos.componentProperties.reloadable) {
        window.addEventListener("beforeunload", beforeUnloadWarning, { capture: true });
      }
    });
    var beforeUnloadWarning = function(event) {
      event.preventDefault();
      event.returnValue = "Are you sure you want to leave?";
    };
    jatos.showBeforeUnloadWarning = function(show) {
      showBeforeUnloadWarning = show;
      if (show) {
        window.addEventListener("beforeunload", beforeUnloadWarning, { capture: true });
      } else {
        window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
      }
    };
    jatos.showOverlay = function(config) {
      if (config && typeof config.show == "boolean" && !config.show) return;
      if (config && typeof config.id == "string") {
        const el = document.getElementById(config.id);
        if (el) {
          if (config && config.text) el.textContent = config.text;
          return el;
        }
      }
      const div = document.createElement("div");
      let divStyle = "color: black;font-family: Sans-Serif;font-size: 30px;letter-spacing: 2px;opacity: 0.6;text-shadow: -1px 0 white, 0 1px white, 1px 0 white, 0 -1px white;z-index: 9999;position: absolute;left: 50%;top: 50%;transform: translate(-50%, -50%);display: flex;align-items: center;justify-content: center;flex-direction: column;";
      if (config && typeof config.style == "string") divStyle += ";" + config.style;
      div.style.cssText = divStyle;
      if (config && typeof config.id == "string") div.id = config.id;
      div.classList.add("jatosOverlay");
      if (config && typeof config.className == "string") div.classList.add(config.className);
      div.textContent = config ? config.text : "Please wait";
      const showImg = config && typeof config.showImg == "boolean" ? config.showImg : true;
      if (showImg) {
        var imgUrl = config && typeof config.imgUrl == "string" ? config.imgUrl : "jatos-publix/images/waiting.gif";
        var waitingImg = document.createElement("img");
        waitingImg.src = imgUrl;
        waitingImg.style.marginTop = "10px";
        div.appendChild(waitingImg);
      }
      const keep = config && typeof config.keep == "boolean" ? config.keep : false;
      div.setAttribute("data-keep", keep);
      if (config && typeof config.timeout == "number") {
        setTimeout(() => div.remove(), config.timeout);
      }
      document.body.appendChild(div);
      return div;
    };
    jatos.removeOverlay = () => jatos.removeOverlays();
    jatos.removeOverlays = function(force) {
      document.querySelectorAll(".jatosOverlay").forEach((el) => {
        if (el.dataset.keep === "false" || force) el.remove();
      });
    };
    function showIdOverlay() {
      if (jatos.workerType !== "Jatos") return;
      const idObj = {};
      if (jatos.frameId) idObj["frame"] = jatos.frameId;
      if (jatos.workerId) idObj["worker"] = jatos.workerId;
      if (jatos.studyResultId) idObj["study result"] = jatos.studyResultId;
      if (jatos.groupResultId) idObj["group"] = jatos.groupResultId;
      const text = Object.entries(idObj).map(([key, value]) => `${key}: ${value}`).join("\n");
      jatos.showOverlay({
        id: "idOverlay",
        text,
        style: "position:fixed;top:unset;left:4px;bottom:4px;transform:unset;font-size:10px;letter-spacing:0px;white-space:pre;line-height:normal;letter-spacing:normal;word-spacing:normal;text-align:left;",
        keep: true,
        showImg: false
      });
    }
    jatos.addAbortButton = function(config) {
      var buttonText = config && typeof config.text == "string" ? config.text : "Cancel";
      var confirm = config && typeof config.confirm == "boolean" ? config.confirm : true;
      var confirmText = config && typeof config.confirmText == "string" ? config.confirmText : "Do you really want to cancel this study?";
      var tooltip = config && typeof config.tooltip == "string" ? config.tooltip : "Cancels this study and deletes all already submitted data";
      var msg = config && typeof config.msg == "string" ? config.msg : "Worker decided to abort";
      var style = "color:black;font-family:Sans-Serif;font-size:20px;letter-spacing:2px;position:fixed;margin:2em 0 0 2em;bottom:1em;right:1em;opacity:0.6;z-index:9999;cursor:pointer;text-shadow:-1px 0 white, 0 1px white, 1px 0 white, 0 -1px white;";
      if (config && typeof config.style == "string") style += ";" + config.style;
      var text = document.createTextNode(buttonText);
      var buttonDiv = document.createElement("div");
      buttonDiv.appendChild(text);
      buttonDiv.style.cssText = style;
      buttonDiv.setAttribute("title", tooltip);
      buttonDiv.addEventListener("click", function() {
        if (!confirm || window.confirm(confirmText)) {
          if (config && typeof config.action == "function") {
            config.action(msg);
          } else {
            jatos.abortStudy(msg);
          }
        }
      });
      document.body.appendChild(buttonDiv);
    };
    function getAjaxErrorMsg(jqxhr) {
      if (jqxhr.statusText === "timeout") {
        return "JATOS server not responding";
      } else {
        if (jqxhr.responseText) {
          return jqxhr.statusText + ": " + jqxhr.responseText;
        } else {
          return jqxhr.statusText + ": Error during Ajax call to JATOS server.";
        }
      }
    }
    function setChannelSendingTimeoutAndPromiseResolution(deferred, sessionTimeouts, sessionActionId, onSuccess, onFail) {
      var timeoutId = setTimeout(function() {
        callWithArgs(onFail, "Timeout sending session patch");
        deferred.reject("Timeout sending session patch");
      }, jatos.channelSendingTimeoutTime);
      sessionTimeouts[sessionActionId] = {
        cancel: function(msg) {
          clearTimeout(timeoutId);
          callWithArgs(onSuccess, msg);
          deferred.resolve(msg);
        },
        trigger: function(msg) {
          clearTimeout(timeoutId);
          callWithArgs(onFail, msg);
          deferred.reject(msg);
        }
      };
      deferred.always(function() {
        delete sessionTimeouts[sessionActionId];
      });
    }
  })();
})();
