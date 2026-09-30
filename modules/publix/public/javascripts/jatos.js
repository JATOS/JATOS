var jatos;
(() => {
  // src/vendor/jquery-deferred.js
  /*!
   * jQuery Deferred/Callbacks v3.7.1 extraction
   * https://github.com/jquery/jquery/tree/3.7.1
   * Vendored source and adaptations documented in vendor/README.md.
   *
   * Copyright OpenJS Foundation and other contributors, https://openjsf.org/
   *
   * Permission is hereby granted, free of charge, to any person obtaining
   * a copy of this software and associated documentation files (the
   * "Software"), to deal in the Software without restriction, including
   * without limitation the rights to use, copy, modify, merge, publish,
   * distribute, sublicense, and/or sell copies of the Software, and to
   * permit persons to whom the Software is furnished to do so, subject to
   * the following conditions:
   *
   * The above copyright notice and this permission notice shall be
   * included in all copies or substantial portions of the Software.
   *
   * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
   * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
   * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
   * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
   * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
   * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
   * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
   */
  var jQuery2 = {};
  var class2type = {};
  var toString = class2type.toString;
  var hasOwn = class2type.hasOwnProperty;
  var fnToString = hasOwn.toString;
  var ObjectFunctionString = fnToString.call(Object);
  var getProto = Object.getPrototypeOf;
  var indexOf = [].indexOf;
  function isFunction(obj) {
    return typeof obj === "function" && typeof obj.nodeType !== "number" && typeof obj.item !== "function";
  }
  function isWindow(obj) {
    return obj != null && obj === obj.window;
  }
  function toType(obj) {
    if (obj == null) {
      return obj + "";
    }
    return typeof obj === "object" || typeof obj === "function" ? class2type[toString.call(obj)] || "object" : typeof obj;
  }
  var rnothtmlwhite = /[^\x20\t\r\n\f]+/g;
  jQuery2.extend = function() {
    var options, name, src, copy, copyIsArray, clone, target = arguments[0] || {}, i = 1, length = arguments.length, deep = false;
    if (typeof target === "boolean") {
      deep = target;
      target = arguments[i] || {};
      i++;
    }
    if (typeof target !== "object" && !isFunction(target)) {
      target = {};
    }
    if (i === length) {
      target = this;
      i--;
    }
    for (; i < length; i++) {
      if ((options = arguments[i]) != null) {
        for (name in options) {
          copy = options[name];
          if (name === "__proto__" || target === copy) {
            continue;
          }
          if (deep && copy && (jQuery2.isPlainObject(copy) || (copyIsArray = Array.isArray(copy)))) {
            src = target[name];
            if (copyIsArray && !Array.isArray(src)) {
              clone = [];
            } else if (!copyIsArray && !jQuery2.isPlainObject(src)) {
              clone = {};
            } else {
              clone = src;
            }
            copyIsArray = false;
            target[name] = jQuery2.extend(deep, clone, copy);
          } else if (copy !== void 0) {
            target[name] = copy;
          }
        }
      }
    }
    return target;
  };
  jQuery2.isPlainObject = function(obj) {
    var proto, Ctor;
    if (!obj || toString.call(obj) !== "[object Object]") {
      return false;
    }
    proto = getProto(obj);
    if (!proto) {
      return true;
    }
    Ctor = hasOwn.call(proto, "constructor") && proto.constructor;
    return typeof Ctor === "function" && fnToString.call(Ctor) === ObjectFunctionString;
  };
  jQuery2.each = function(obj, callback) {
    var length, i = 0;
    if (isArrayLike(obj)) {
      length = obj.length;
      for (; i < length; i++) {
        if (callback.call(obj[i], i, obj[i]) === false) {
          break;
        }
      }
    } else {
      for (i in obj) {
        if (callback.call(obj[i], i, obj[i]) === false) {
          break;
        }
      }
    }
    return obj;
  }, // Retrieve the text value of an array of DOM nodes
  jQuery2.inArray = function(elem, arr, i) {
    return arr == null ? -1 : indexOf.call(arr, elem, i);
  };
  jQuery2.each(
    "Boolean Number String Function Array Date RegExp Object Error Symbol".split(" "),
    function(_i, name) {
      class2type["[object " + name + "]"] = name.toLowerCase();
    }
  );
  function isArrayLike(obj) {
    var length = !!obj && "length" in obj && obj.length, type = toType(obj);
    if (isFunction(obj) || isWindow(obj)) {
      return false;
    }
    return type === "array" || length === 0 || typeof length === "number" && length > 0 && length - 1 in obj;
  }
  function createOptions(options) {
    var object = {};
    jQuery2.each(options.match(rnothtmlwhite) || [], function(_, flag) {
      object[flag] = true;
    });
    return object;
  }
  jQuery2.Callbacks = function(options) {
    options = typeof options === "string" ? createOptions(options) : jQuery2.extend({}, options);
    var firing, memory, fired, locked, list = [], queue = [], firingIndex = -1, fire = function() {
      locked = locked || options.once;
      fired = firing = true;
      for (; queue.length; firingIndex = -1) {
        memory = queue.shift();
        while (++firingIndex < list.length) {
          if (list[firingIndex].apply(memory[0], memory[1]) === false && options.stopOnFalse) {
            firingIndex = list.length;
            memory = false;
          }
        }
      }
      if (!options.memory) {
        memory = false;
      }
      firing = false;
      if (locked) {
        if (memory) {
          list = [];
        } else {
          list = "";
        }
      }
    }, self = {
      // Add a callback or a collection of callbacks to the list
      add: function() {
        if (list) {
          if (memory && !firing) {
            firingIndex = list.length - 1;
            queue.push(memory);
          }
          (function add(args) {
            jQuery2.each(args, function(_, arg) {
              if (isFunction(arg)) {
                if (!options.unique || !self.has(arg)) {
                  list.push(arg);
                }
              } else if (arg && arg.length && toType(arg) !== "string") {
                add(arg);
              }
            });
          })(arguments);
          if (memory && !firing) {
            fire();
          }
        }
        return this;
      },
      // Remove a callback from the list
      remove: function() {
        jQuery2.each(arguments, function(_, arg) {
          var index;
          while ((index = jQuery2.inArray(arg, list, index)) > -1) {
            list.splice(index, 1);
            if (index <= firingIndex) {
              firingIndex--;
            }
          }
        });
        return this;
      },
      // Check if a given callback is in the list.
      // If no argument is given, return whether or not list has callbacks attached.
      has: function(fn) {
        return fn ? jQuery2.inArray(fn, list) > -1 : list.length > 0;
      },
      // Remove all callbacks from the list
      empty: function() {
        if (list) {
          list = [];
        }
        return this;
      },
      // Disable .fire and .add
      // Abort any current/pending executions
      // Clear all callbacks and values
      disable: function() {
        locked = queue = [];
        list = memory = "";
        return this;
      },
      disabled: function() {
        return !list;
      },
      // Disable .fire
      // Also disable .add unless we have memory (since it would have no effect)
      // Abort any pending executions
      lock: function() {
        locked = queue = [];
        if (!memory && !firing) {
          list = memory = "";
        }
        return this;
      },
      locked: function() {
        return !!locked;
      },
      // Call all callbacks with the given context and arguments
      fireWith: function(context, args) {
        if (!locked) {
          args = args || [];
          args = [context, args.slice ? args.slice() : args];
          queue.push(args);
          if (!firing) {
            fire();
          }
        }
        return this;
      },
      // Call all the callbacks with the given arguments
      fire: function() {
        self.fireWith(this, arguments);
        return this;
      },
      // To know if the callbacks have already been called at least once
      fired: function() {
        return !!fired;
      }
    };
    return self;
  };
  function Identity(v) {
    return v;
  }
  function Thrower(ex) {
    throw ex;
  }
  jQuery2.extend({
    Deferred: function(func) {
      var tuples = [
        // action, add listener, callbacks,
        // ... .then handlers, argument index, [final state]
        [
          "notify",
          "progress",
          jQuery2.Callbacks("memory"),
          jQuery2.Callbacks("memory"),
          2
        ],
        [
          "resolve",
          "done",
          jQuery2.Callbacks("once memory"),
          jQuery2.Callbacks("once memory"),
          0,
          "resolved"
        ],
        [
          "reject",
          "fail",
          jQuery2.Callbacks("once memory"),
          jQuery2.Callbacks("once memory"),
          1,
          "rejected"
        ]
      ], state = "pending", promise = {
        state: function() {
          return state;
        },
        always: function() {
          deferred.done(arguments).fail(arguments);
          return this;
        },
        "catch": function(fn) {
          return promise.then(null, fn);
        },
        // Keep pipe for back-compat
        pipe: function() {
          var fns = arguments;
          return jQuery2.Deferred(function(newDefer) {
            jQuery2.each(tuples, function(_i, tuple) {
              var fn = isFunction(fns[tuple[4]]) && fns[tuple[4]];
              deferred[tuple[1]](function() {
                var returned = fn && fn.apply(this, arguments);
                if (returned && isFunction(returned.promise)) {
                  returned.promise().progress(newDefer.notify).done(newDefer.resolve).fail(newDefer.reject);
                } else {
                  newDefer[tuple[0] + "With"](
                    this,
                    fn ? [returned] : arguments
                  );
                }
              });
            });
            fns = null;
          }).promise();
        },
        then: function(onFulfilled, onRejected, onProgress) {
          var maxDepth = 0;
          function resolve(depth, deferred2, handler, special) {
            return function() {
              var that = this, args = arguments, mightThrow = function() {
                var returned, then;
                if (depth < maxDepth) {
                  return;
                }
                returned = handler.apply(that, args);
                if (returned === deferred2.promise()) {
                  throw new TypeError("Thenable self-resolution");
                }
                then = returned && // Support: Promises/A+ section 2.3.4
                // https://promisesaplus.com/#point-64
                // Only check objects and functions for thenability
                (typeof returned === "object" || typeof returned === "function") && returned.then;
                if (isFunction(then)) {
                  if (special) {
                    then.call(
                      returned,
                      resolve(maxDepth, deferred2, Identity, special),
                      resolve(maxDepth, deferred2, Thrower, special)
                    );
                  } else {
                    maxDepth++;
                    then.call(
                      returned,
                      resolve(maxDepth, deferred2, Identity, special),
                      resolve(maxDepth, deferred2, Thrower, special),
                      resolve(
                        maxDepth,
                        deferred2,
                        Identity,
                        deferred2.notifyWith
                      )
                    );
                  }
                } else {
                  if (handler !== Identity) {
                    that = void 0;
                    args = [returned];
                  }
                  (special || deferred2.resolveWith)(that, args);
                }
              }, process = special ? mightThrow : function() {
                try {
                  mightThrow();
                } catch (e) {
                  if (jQuery2.Deferred.exceptionHook) {
                    jQuery2.Deferred.exceptionHook(
                      e,
                      process.error
                    );
                  }
                  if (depth + 1 >= maxDepth) {
                    if (handler !== Thrower) {
                      that = void 0;
                      args = [e];
                    }
                    deferred2.rejectWith(that, args);
                  }
                }
              };
              if (depth) {
                process();
              } else {
                if (jQuery2.Deferred.getErrorHook) {
                  process.error = jQuery2.Deferred.getErrorHook();
                } else if (jQuery2.Deferred.getStackHook) {
                  process.error = jQuery2.Deferred.getStackHook();
                }
                globalThis.setTimeout(process);
              }
            };
          }
          return jQuery2.Deferred(function(newDefer) {
            tuples[0][3].add(
              resolve(
                0,
                newDefer,
                isFunction(onProgress) ? onProgress : Identity,
                newDefer.notifyWith
              )
            );
            tuples[1][3].add(
              resolve(
                0,
                newDefer,
                isFunction(onFulfilled) ? onFulfilled : Identity
              )
            );
            tuples[2][3].add(
              resolve(
                0,
                newDefer,
                isFunction(onRejected) ? onRejected : Thrower
              )
            );
          }).promise();
        },
        // Get a promise for this deferred
        // If obj is provided, the promise aspect is added to the object
        promise: function(obj) {
          return obj != null ? jQuery2.extend(obj, promise) : promise;
        }
      }, deferred = {};
      jQuery2.each(tuples, function(i, tuple) {
        var list = tuple[2], stateString = tuple[5];
        promise[tuple[1]] = list.add;
        if (stateString) {
          list.add(
            function() {
              state = stateString;
            },
            // rejected_callbacks.disable
            // fulfilled_callbacks.disable
            tuples[3 - i][2].disable,
            // rejected_handlers.disable
            // fulfilled_handlers.disable
            tuples[3 - i][3].disable,
            // progress_callbacks.lock
            tuples[0][2].lock,
            // progress_handlers.lock
            tuples[0][3].lock
          );
        }
        list.add(tuple[3].fire);
        deferred[tuple[0]] = function() {
          deferred[tuple[0] + "With"](this === deferred ? void 0 : this, arguments);
          return this;
        };
        deferred[tuple[0] + "With"] = list.fireWith;
      });
      promise.promise(deferred);
      if (func) {
        func.call(deferred, deferred);
      }
      return deferred;
    }
  });
  var Deferred = jQuery2.Deferred;

  // src/jatos-promise.js
  function createDeferred() {
    return Deferred();
  }
  function rejectedPromise(error) {
    return createDeferred().reject(error).promise();
  }
  function isDeferredPending(deferred) {
    return deferred !== void 0 && deferred.state() === "pending";
  }

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

  // src/result-data.js
  function installResultDataApi(jatos2, dependencies) {
    const {
      getURL,
      isInitialized,
      isInvalidComponentPosition: isInvalidComponentPosition2,
      isStudyRunInvalid,
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
        return rejectedPromise(errorMsg);
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
        return rejectedPromise(errorMsg);
      }
      if (typeof filename !== "string" || 0 === filename.length) {
        const errorMsg = "No filename specified.";
        callMany(errorMsg, onError, console.error);
        return rejectedPromise(errorMsg);
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
        return rejectedPromise(errorMsg);
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
        return rejectedPromise(errorMsg);
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
        return rejectedPromise(errorMsg);
      }
      if (isStudyRunInvalid()) {
        const errorMsg = "Can't download file. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      if (typeof filename !== "string" || 0 === filename.length) {
        const errorMsg = "No filename specified.";
        callMany(errorMsg, onError, console.error);
        return rejectedPromise(errorMsg);
      }
      let url = getURL("../files/" + encodeURI(filename));
      if (componentPos) {
        if (isInvalidComponentPosition2(componentPos)) {
          const errorMsg = "Component position does not exist.";
          callMany(errorMsg, onError, console.error);
          return rejectedPromise(errorMsg);
        }
        const componentId = jatos2.componentList[componentPos - 1].id;
        url += "?componentId=" + componentId;
      }
      const deferred = createDeferred();
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
  function createHttpLoop({ isInitialized }) {
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
        return createDeferred().reject();
      }
      const deferred = createDeferred();
      deferred.done(function() {
        call(onSuccess);
      });
      deferred.fail(function(err) {
        callWithArgs(onError, err);
      });
      request.id = counter++;
      waitingRequests[request.id] = deferred;
      if (!isDeferredPending(idleDeferred)) {
        idleDeferred = createDeferred();
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

  // src/channels.js
  function createChannels(jatos2, dependencies) {
    const {
      getURL,
      getAjaxErrorMsg,
      showIdOverlay,
      isEndingStudy,
      isStartingComponent,
      isStudyRunInvalid,
      setStudyRunInvalid
    } = dependencies;
    jatos2.groupMemberId = null;
    jatos2.groupResultId = null;
    jatos2.groupMembers = [];
    jatos2.groupChannels = [];
    let groupState = null;
    let groupSessionData = {};
    let batchSessionData = {};
    jatos2.channelSendingTimeoutTime = 1e4;
    jatos2.channelHeartbeatInterval = 1e4;
    jatos2.channelHeartbeatTimeoutTime = 1e4;
    jatos2.channelClosedCheckInterval = 2e3;
    jatos2.channelOpeningBackoffTimeMin = 1e3;
    jatos2.channelOpeningBackoffTimeMax = 12e4;
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
    jatos2.batchSessionVersioning = true;
    jatos2.groupSessionVersioning = true;
    let batchChannel;
    let groupChannel;
    let groupChannelCallbacks;
    const webSocketSupported = "WebSocket" in window;
    let openingBatchChannelDeferred;
    let sendingBatchSessionDeferred;
    let openingGroupChannelDeferred;
    let sendingGroupSessionDeferred;
    let sendingGroupFixedDeferred;
    let reassigningGroupDeferred;
    let leavingGroupDeferred;
    const batchChannelAliveEvent = new Event("batchChannelAlive");
    const batchChannelDeadEvent = new Event("batchChannelDead");
    let onJatosBatchSession;
    let batchChannelAlive = false;
    jatos2.onConnected = function(callback) {
      window.addEventListener("batchChannelAlive", callback);
    };
    jatos2.onDisconnected = function(callback) {
      window.addEventListener("batchChannelDead", callback);
    };
    jatos2.isConnected = function() {
      return batchChannelAlive;
    };
    function openBatchChannelWithRetry(backoffTime) {
      if (typeof backoffTime !== "number") backoffTime = jatos2.channelOpeningBackoffTimeMin;
      return openBatchChannel().fail(function() {
        if (backoffTime < jatos2.channelOpeningBackoffTimeMax) backoffTime *= 2;
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
      if (isEndingStudy() || isStartingComponent()) {
        const errorMsg = "Won't open batch channel because study is about to move to the next component or finish.";
        console.info(errorMsg);
        return rejectedPromise(errorMsg);
      }
      if (isStudyRunInvalid()) {
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
        (window.location.protocol === "https:" ? "wss://" : "ws://") + window.location.host + jatos2.urlBasePath + "publix/" + jatos2.studyResultUuid + "/batch/open"
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
            jatos2.channelHeartbeatTimeoutTime
          );
          batchChannelHeartbeatTimeoutTimers.push(timeout);
        }
      }, jatos2.channelHeartbeatInterval);
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
      }, jatos2.channelClosedCheckInterval);
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
          setStudyRunInvalid(true);
          console.info("Batch channel closed by JATOS server");
          jatos2.showOverlay({ text: "This study run is invalid.", showImg: false });
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
    jatos2.batchSession = {};
    jatos2.batchSession.get = function(name) {
      const obj = jsonpatch.getValueByPointer(batchSessionData, "/" + name);
      return cloneJsonObj(obj);
    };
    jatos2.batchSession.getAll = function() {
      const obj = jatos2.batchSession.find("");
      return cloneJsonObj(obj);
    };
    jatos2.batchSession.find = function(path) {
      const obj = jsonpatch.getValueByPointer(batchSessionData, path);
      return cloneJsonObj(obj);
    };
    jatos2.batchSession.test = function(path, value) {
      const obj = jsonpatch.getValueByPointer(batchSessionData, path);
      return obj === value;
    };
    jatos2.batchSession.defined = function(path) {
      return !jatos2.batchSession.test(path, void 0);
    };
    jatos2.batchSession.add = function(path, value, onSuccess, onFail) {
      const patch = generatePatch("add", path, value, null);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.batchSession.set = function(name, value, onSuccess, onFail) {
      const patch = generatePatch("add", "/" + name, value, null);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.batchSession.setAll = function(value, onSuccess, onFail) {
      return jatos2.batchSession.replace("", value, onSuccess, onFail);
    };
    jatos2.batchSession.remove = function(path, onSuccess, onFail) {
      const patch = generatePatch("remove", path, null, null);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.batchSession.clear = function(onSuccess, onFail) {
      const patch = generatePatch("replace", "", {}, null);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.batchSession.replace = function(path, value, onSuccess, onFail) {
      const patch = generatePatch("replace", path, value, null);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.batchSession.copy = function(from, path, onSuccess, onFail) {
      const patch = generatePatch("copy", path, null, from);
      return sendBatchSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.batchSession.move = function(from, path, onSuccess, onFail) {
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
      if (jatos2.batchSessionVersioning && isDeferredPending(sendingBatchSessionDeferred)) {
        const errorMsg = `Can send only one batch session patch at a time. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.error);
        return rejectedPromise(errorMsg);
      }
      if (isStudyRunInvalid()) {
        const errorMsg = `Can't send batch session patch. This study run is invalid. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.warn);
        return rejectedPromise(errorMsg);
      }
      const deferred = createDeferred();
      if (jatos2.batchSessionVersioning) sendingBatchSessionDeferred = deferred;
      const sessionActionId = batchSessionCounter++;
      const msgObj = {};
      msgObj.action = "SESSION";
      msgObj.id = sessionActionId;
      msgObj.patches = patches.constructor === Array ? patches : [patches];
      msgObj.version = batchSessionVersion;
      msgObj.versioning = !!jatos2.batchSessionVersioning;
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
    jatos2.onBatchSession = function(onBatchSession) {
      onJatosBatchSession = onBatchSession;
    };
    jatos2.joinGroup = function(callbacks) {
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
      if (isEndingStudy() || isStartingComponent()) {
        const errorMsg = "Won't open group channel because study is about to move to the next component or finish.";
        callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
        return rejectedPromise(errorMsg);
      }
      if (isStudyRunInvalid()) {
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
        (window.location.protocol === "https:" ? "wss://" : "ws://") + window.location.host + jatos2.urlBasePath + "publix/" + jatos2.studyResultUuid + "/group/join"
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
      if (typeof backoffTime !== "number") backoffTime = jatos2.channelOpeningBackoffTimeMin;
      openGroupChannel().fail(function() {
        if (backoffTime < jatos2.channelOpeningBackoffTimeMax) backoffTime *= 2;
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
          }, jatos2.channelHeartbeatTimeoutTime);
          groupChannelHeartbeatTimeoutTimers.push(timeout);
        }
      }, jatos2.channelHeartbeatInterval);
    }
    function groupChannelClosedCheck() {
      clearInterval(groupChannelClosedCheckTimer);
      groupChannelClosedCheckTimer = setInterval(function() {
        if (groupChannel.readyState === groupChannel.CLOSED) {
          callMany("Group channel closed", console.info, groupChannelCallbacks.onError);
          clearInterval(groupChannelClosedCheckTimer);
          reopenGroupChannel();
        }
      }, jatos2.channelClosedCheckInterval);
    }
    function clearGroupChannelHeartbeatTimeoutTimers() {
      groupChannelHeartbeatTimeoutTimers.forEach(function(timeout) {
        clearTimeout(timeout);
      });
      groupChannelHeartbeatTimeoutTimers = [];
    }
    function clearGroupChannel() {
      jatos2.groupMemberId = null;
      jatos2.groupResultId = null;
      jatos2.groupMembers = [];
      jatos2.groupChannels = [];
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
        jatos2.groupResultId = groupMsg.groupResultId.toString();
        showIdOverlay();
        jatos2.groupMemberId = jatos2.studyResultId;
      }
      if (groupMsg.action === "OPENED" && typeof groupMsg.members != "undefined") {
        jatos2.groupMembers = groupMsg.members;
      } else if (typeof groupMsg.memberId != "undefined" && groupMsg.action === "JOINED" && !jatos2.groupMembers.includes(groupMsg.memberId)) {
        jatos2.groupMembers.push(groupMsg.memberId);
      } else if (typeof groupMsg.memberId != "undefined" && groupMsg.action === "LEFT") {
        jatos2.groupMembers = jatos2.groupMembers.filter(function(memberId) {
          return memberId !== groupMsg.memberId;
        });
      }
      if (groupMsg.action === "OPENED" && typeof groupMsg.channels != "undefined") {
        jatos2.groupChannels = groupMsg.channels;
      } else if (typeof groupMsg.memberId != "undefined" && groupMsg.action === "CHANNEL_OPENED" && !jatos2.groupChannels.includes(groupMsg.memberId)) {
        jatos2.groupChannels.push(groupMsg.memberId);
      } else if (typeof groupMsg.memberId != "undefined" && (groupMsg.action === "CHANNEL_CLOSED" || groupMsg.action === "CLOSED")) {
        jatos2.groupChannels = jatos2.groupChannels.filter(function(memberId) {
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
          if (groupMsg.memberId !== jatos2.groupMemberId) {
            callWithArgs(groupChannelCallbacks.onMemberJoin, groupMsg.memberId);
            call(groupChannelCallbacks.onUpdate);
          }
          break;
        case "LEFT":
          if (groupMsg.memberId !== jatos2.groupMemberId) {
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
    jatos2.getGroupState = function() {
      return groupState;
    };
    jatos2.isGroupFixed = function() {
      return groupState === "FIXED";
    };
    jatos2.groupSession = {};
    jatos2.groupSession.get = function(name) {
      const obj = jsonpatch.getValueByPointer(groupSessionData, "/" + name);
      return cloneJsonObj(obj);
    };
    jatos2.groupSession.getAll = function() {
      const obj = jatos2.groupSession.find("");
      return cloneJsonObj(obj);
    };
    jatos2.groupSession.find = function(path) {
      const obj = jsonpatch.getValueByPointer(groupSessionData, path);
      return cloneJsonObj(obj);
    };
    jatos2.groupSession.test = function(path, value) {
      const obj = jsonpatch.getValueByPointer(groupSessionData, path);
      return obj === value;
    };
    jatos2.groupSession.defined = function(path) {
      return !jatos2.groupSession.test(path, void 0);
    };
    jatos2.groupSession.add = function(path, value, onSuccess, onFail) {
      const patch = generatePatch("add", path, value, null);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.groupSession.set = function(name, value, onSuccess, onFail) {
      const patch = generatePatch("add", "/" + name, value, null);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.groupSession.setAll = function(value, onSuccess, onFail) {
      return jatos2.groupSession.replace("", value, onSuccess, onFail);
    };
    jatos2.groupSession.remove = function(path, onSuccess, onFail) {
      const patch = generatePatch("remove", path, null, null);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.groupSession.clear = function(onSuccess, onFail) {
      const patch = generatePatch("replace", "", {}, null);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.groupSession.replace = function(path, value, onSuccess, onFail) {
      const patch = generatePatch("replace", path, value, null);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.groupSession.copy = function(from, path, onSuccess, onFail) {
      const patch = generatePatch("copy", path, null, from);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    jatos2.groupSession.move = function(from, path, onSuccess, onFail) {
      const patch = generatePatch("move", path, null, from);
      return sendGroupSessionPatch(patch, onSuccess, onFail);
    };
    function sendGroupSessionPatch(patches, onSuccess, onFail) {
      if (!groupChannel || groupChannel.readyState !== groupChannel.OPEN) {
        const errorMsg = `Can't send group session patch. No open group channel. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.error);
        return rejectedPromise(errorMsg);
      }
      if (jatos2.groupSessionVersioning && isDeferredPending(sendingGroupSessionDeferred)) {
        const errorMsg = `Can send only one group session patch at a time. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.error);
        return rejectedPromise(errorMsg);
      }
      if (isStudyRunInvalid()) {
        const errorMsg = `Can't send group session patch. This study run is invalid. Patch: ${patches.op} ${patches.path}.`;
        callMany(errorMsg, onFail, console.warn);
        return rejectedPromise(errorMsg);
      }
      const deferred = createDeferred();
      if (jatos2.groupSessionVersioning) sendingGroupSessionDeferred = deferred;
      const sessionActionId = groupSessionCounter++;
      const msgObj = {};
      msgObj.action = "SESSION";
      msgObj.sessionActionId = sessionActionId;
      msgObj.sessionPatches = patches.constructor === Array ? patches : [patches];
      msgObj.sessionVersion = groupSessionVersion;
      msgObj.sessionVersioning = !!jatos2.groupSessionVersioning;
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
    jatos2.setGroupFixed = function(onSuccess, onFail) {
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
      if (isStudyRunInvalid()) {
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
      }, jatos2.channelSendingTimeoutTime);
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
    jatos2.hasJoinedGroup = function() {
      return jatos2.groupResultId !== null;
    };
    jatos2.hasOpenGroupChannel = function() {
      return groupChannel && groupChannel.readyState === groupChannel.OPEN;
    };
    jatos2.isMaxActiveMemberReached = function() {
      if (!jatos2.batchProperties || jatos2.batchProperties.maxActiveMembers === null) {
        return false;
      } else {
        return jatos2.groupMembers.length >= jatos2.batchProperties.maxActiveMembers;
      }
    };
    jatos2.isMaxActiveMemberOpen = function() {
      if (!jatos2.batchProperties || jatos2.batchProperties.maxActiveMembers === null) {
        return false;
      } else {
        return jatos2.groupChannels.length >= jatos2.batchProperties.maxActiveMembers;
      }
    };
    jatos2.isGroupOpen = function() {
      if (groupChannel && groupChannel.readyState === groupChannel.OPEN) {
        return jatos2.groupMembers.length === jatos2.groupChannels.length;
      } else {
        return false;
      }
    };
    jatos2.sendGroupMsg = function(msg) {
      if (groupChannel && groupChannel.readyState === groupChannel.OPEN) {
        const msgObj = {};
        msgObj.msg = msg;
        groupChannel.send(JSON.stringify(msgObj));
      }
    };
    jatos2.sendGroupMsgTo = function(recipient, msg) {
      if (groupChannel && groupChannel.readyState === groupChannel.OPEN) {
        const msgObj = {};
        msgObj.recipient = recipient;
        msgObj.msg = msg;
        groupChannel.send(JSON.stringify(msgObj));
      }
    };
    jatos2.reassignGroup = function(onSuccess, onFail) {
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
      if (isStudyRunInvalid()) {
        const errorMsg = "Can't reassign group. This study run is invalid.";
        callMany(errorMsg, console.warn, onFail);
        return rejectedPromise(errorMsg);
      }
      reassigningGroupDeferred = createDeferred();
      jatos2.jQuery.ajax({
        url: getURL("../group/reassign"),
        processData: false,
        type: "GET",
        timeout: jatos2.httpTimeout,
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
    jatos2.leaveGroup = function(onSuccess, onError) {
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
      if (isStudyRunInvalid()) {
        const errorMsg = "Can't leave group. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      leavingGroupDeferred = createDeferred();
      jatos2.jQuery.ajax({
        url: getURL("../group/leave"),
        processData: false,
        type: "GET",
        timeout: jatos2.httpTimeout,
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
        times: jatos2.httpRetry,
        timeout: jatos2.httpRetryWait
      });
      return leavingGroupDeferred.promise();
    };
    function setChannelSendingTimeoutAndPromiseResolution(deferred, sessionTimeouts, sessionActionId, onSuccess, onFail) {
      var timeoutId = setTimeout(function() {
        callWithArgs(onFail, "Timeout sending session patch");
        deferred.reject("Timeout sending session patch");
      }, jatos2.channelSendingTimeoutTime);
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
    return {
      openBatchChannelWithRetry,
      // Preserve the existing end/abort cleanup: only stop closed-channel checks.
      stopClosedChecks: () => {
        clearInterval(batchChannelClosedCheckTimer);
        clearInterval(groupChannelClosedCheckTimer);
      }
    };
  }

  // src/initialization.js
  function createInitialization(jatos2, dependencies) {
    const { getURL, getAjaxErrorMsg, showIdOverlay, httpLoop, channels } = dependencies;
    let initialized = false;
    let jatosOnLoadEventFired = false;
    const jatosOnLoadEvent = new Event("jatosOnLoad");
    let heartbeatWorker;
    jatos2.jQuery = {};
    function start() {
      getScript("jatos-publix/javascripts/jquery-3.7.1.min.js", function() {
        jatos2.jQuery = jQuery.noConflict(true);
        jatos2.jQuery.ajaxSetup({
          cache: true
        });
        initJatos();
      });
    }
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
      jatos2.jQuery.when(
        // Load jQuery plugin to retry ajax calls: https://github.com/johnkpaul/jquery-ajax-retry
        jatos2.jQuery.getScript("jatos-publix/javascripts/jquery.ajax-retry.min.js"),
        // Load JSON Patch library https://github.com/Starcounter-Jack/JSON-Patch
        jatos2.jQuery.getScript("jatos-publix/javascripts/fast-json-patch.min.js")
      ).then(function() {
        jatos2.studyResultUuid = window.location.pathname.split("/").reverse()[2];
        readIdCookie();
        heartbeatWorker = new Worker("jatos-publix/javascripts/heartbeat.js");
        heartbeatWorker.postMessage([jatos2.studyResultUuid]);
        httpLoop.start();
      }).then(getInitData).then(showIdOverlay).then(channels.openBatchChannelWithRetry).always(function() {
        initialized = true;
        readyForOnLoad();
      });
    }
    function readIdCookie() {
      const idCookieName = "JATOS_ID";
      const cookieRow = document.cookie.split("; ").filter((row) => row.includes(idCookieName)).find((row) => row.includes(jatos2.studyResultUuid));
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
      cookieParams.forEach((value, key) => jatos2[key] = value);
      jatos2.componentPos = parseInt(jatos2.componentPos, 10);
    }
    function getInitData() {
      return jatos2.jQuery.ajax({
        url: getURL("initData"),
        type: "GET",
        dataType: "json",
        timeout: jatos2.httpTimeout,
        success: setInitData,
        error: (err) => console.error(getAjaxErrorMsg(err))
      }).retry({
        times: jatos2.httpRetry,
        timeout: jatos2.httpRetryWait
      });
    }
    function setInitData(initData) {
      jatos2.batchProperties = initData.batchProperties;
      if (typeof jatos2.batchProperties.batchInput != "undefined" && jatos2.studyProperties.studyInput !== null) {
        jatos2.batchJsonInput = jatos2.jQuery.parseJSON(jatos2.batchProperties.batchInput);
      } else {
        jatos2.batchJsonInput = {};
      }
      jatos2.batchInput = jatos2.batchJsonInput;
      delete jatos2.batchProperties.batchInput;
      try {
        jatos2.studySessionData = JSON.parse(initData.studySessionData);
      } catch (e) {
        console.error(e.stack || e);
      }
      jatos2.studyProperties = initData.studyProperties;
      if (typeof jatos2.studyProperties.studyInput != "undefined" && jatos2.studyProperties.studyInput !== null) {
        jatos2.studyJsonInput = jatos2.jQuery.parseJSON(jatos2.studyProperties.studyInput);
      } else {
        jatos2.studyJsonInput = {};
      }
      jatos2.studyInput = jatos2.studyJsonInput;
      delete jatos2.studyProperties.studyInput;
      jatos2.componentList = initData.componentList;
      jatos2.studyLength = initData.componentList.length;
      jatos2.componentProperties = initData.componentProperties;
      if (typeof jatos2.componentProperties.componentInput != "undefined" && jatos2.componentProperties.componentInput !== null) {
        jatos2.componentJsonInput = jatos2.jQuery.parseJSON(jatos2.componentProperties.componentInput);
      } else {
        jatos2.componentJsonInput = {};
      }
      jatos2.componentInput = jatos2.componentJsonInput;
      delete jatos2.componentProperties.componentInput;
      jatos2.urlQueryParameters = initData.urlQueryParameters;
      jatos2.frameId = jatos2.urlQueryParameters.frameId || void 0;
      jatos2.studyCode = initData.studyCode;
    }
    jatos2.onLoad = function(callback) {
      if (!jatosOnLoadEventFired) {
        window.addEventListener("jatosOnLoad", callback);
        readyForOnLoad();
      } else {
        callback();
      }
    };
    jatos2.onload = jatos2.onLoad;
    function readyForOnLoad() {
      if (!jatosOnLoadEventFired && initialized) {
        jatosOnLoadEventFired = true;
        window.dispatchEvent(jatosOnLoadEvent);
      }
    }
    jatos2.setHeartbeatPeriod = function(heartbeatPeriod) {
      if (typeof heartbeatPeriod == "number" && heartbeatWorker) {
        heartbeatWorker.postMessage([jatos2.studyResultUuid, heartbeatPeriod]);
      }
    };
    return {
      start,
      isInitialized: () => initialized,
      terminateHeartbeat: () => heartbeatWorker.terminate()
    };
  }

  // src/study-run.js
  function installStudyRunApi(jatos2, dependencies) {
    const {
      beforeUnloadWarning,
      getURL,
      httpLoop,
      isEndingStudy,
      isInitialized,
      isStartingComponent,
      isStudyRunInvalid,
      setStartingComponent,
      setEndingStudy,
      stopStudyRun
    } = dependencies;
    jatos2.setStudySessionData = function(studySessionData, onSuccess, onFail) {
      jatos2.studySessionData = studySessionData;
      const studySessionDataStr = JSON.stringify(studySessionData);
      const request = {
        url: getURL("../studySessionData"),
        data: studySessionDataStr,
        method: "POST",
        contentType: "text/plain; charset=UTF-8",
        timeout: jatos2.httpTimeout,
        retry: jatos2.httpRetry,
        retryWait: jatos2.httpRetryWait
      };
      return httpLoop.send(request, onSuccess, onFail).promise();
    };
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
    jatos2.abortStudyWithoutRedirect = function(message, onSuccess, onError) {
      if (!isInitialized()) {
        const errorMsg = "jatos.js not yet initialized.";
        callMany(errorMsg, onError, console.error);
        return rejectedPromise(errorMsg);
      }
      if (isStudyRunInvalid()) {
        const errorMsg = "Can't abort study. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      if (isEndingStudy()) {
        const errorMsg = "Can end/abort study only once.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      setEndingStudy(true);
      var url = getURL("../abort");
      if (typeof message != "undefined") {
        url = url + "?message=" + message;
      }
      var request = {
        url,
        method: "GET",
        timeout: jatos2.httpTimeout,
        retry: jatos2.httpRetry,
        retryWait: jatos2.httpRetryWait
      };
      jatos2.showBeforeUnloadWarning(false);
      var deferred = httpLoop.send(request, onSuccess, onError);
      setTimeout(function() {
        if (httpLoop.isBusy() && isDeferredPending(deferred)) {
          jatos2.showOverlay(jatos2.waitSendDataOverlayConfig);
        }
      }, 1e3);
      deferred.done(function() {
        window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        stopStudyRun();
      });
      deferred.always(jatos2.removeOverlays);
      return deferred.promise();
    };
    jatos2.abortStudyAjax = function(message, onSuccess, onError) {
      return jatos2.abortStudyWithoutRedirect(message, onSuccess, onError);
    };
    jatos2.abortStudyAndRedirect = function(url, message, onSuccess, onError) {
      jatos2.abortStudyWithoutRedirect(message, onSuccess, onError).done(function() {
        window.location.href = url;
      });
    };
    jatos2.abortStudy = function(message, showEndPage = true) {
      if (isStudyRunInvalid()) {
        console.warn("Can't abort study. This study run is invalid.");
        return;
      }
      if (!showEndPage) {
        return jatos2.abortStudyWithoutRedirect(message);
      }
      const isInIframe = window.self !== window.top;
      if (isInIframe && jatos2.workerType === "Jatos" && parent.onIframeComplete) {
        return jatos2.abortStudyWithoutRedirect(message).done(() => parent.onIframeComplete(jatos2.urlQueryParameters.frameId, jatos2.studyId));
      }
      if (isEndingStudy()) {
        console.warn("Can end/abort study only once");
        return;
      }
      setEndingStudy(true);
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
        setTimeout(jatos2.showOverlay, 1e3, jatos2.waitSendDataOverlayConfig);
      }
      httpLoop.whenIdle(abort);
    };
    jatos2.endStudyWithoutRedirect = function(param1, param2, param3, param4, param5) {
      if (!isInitialized()) {
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
      if (isStudyRunInvalid()) {
        const errorMsg = "Can't end study. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      if (isEndingStudy()) {
        const errorMsg = "Can end/abort study only once.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      setEndingStudy(true);
      if (resultData) jatos2.appendResultData(resultData);
      var url = getURL("../end");
      if (typeof successful == "boolean" && typeof message == "string") {
        url = url + "?" + jatos2.jQuery.param({
          "successful": successful,
          "message": message
        });
      } else if (typeof successful == "boolean" && typeof message != "string") {
        url = url + "?" + jatos2.jQuery.param({
          "successful": successful
        });
      } else if (typeof successful != "boolean" && typeof message == "string") {
        url = url + "?" + jatos2.jQuery.param({
          "message": message
        });
      }
      var request = {
        url,
        method: "GET",
        timeout: jatos2.httpTimeout,
        retry: jatos2.httpRetry,
        retryWait: jatos2.httpRetryWait
      };
      jatos2.showBeforeUnloadWarning(false);
      var deferred = httpLoop.send(request, onSuccess, onError);
      setTimeout(function() {
        if (httpLoop.isBusy() && isDeferredPending(deferred)) {
          jatos2.showOverlay(jatos2.waitSendDataOverlayConfig);
        }
      }, 1e3);
      deferred.done(function() {
        window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        stopStudyRun();
      });
      deferred.always(jatos2.removeOverlays);
      return deferred.promise();
    };
    jatos2.endStudyAjax = function(param1, param2, param3, param4, param5) {
      return jatos2.endStudyWithoutRedirect(param1, param2, param3, param4, param5);
    };
    jatos2.endStudyAndRedirect = function(url, param1, param2, param3, param4, param5) {
      jatos2.endStudyWithoutRedirect(param1, param2, param3, param4, param5).done(function() {
        window.location.href = url;
      });
    };
    jatos2.endStudy = function(param1, param2, param3, param4) {
      if (!isInitialized()) {
        console.error("jatos.js not yet initialized");
        return;
      }
      if (isStudyRunInvalid()) {
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
          return jatos2.endStudyWithoutRedirect(resultData, successful, message);
        } else {
          return jatos2.endStudyWithoutRedirect(successful, message);
        }
      }
      const isInIframe = window.self !== window.top;
      if (isInIframe && jatos2.workerType === "Jatos" && parent.onIframeComplete) {
        const endIframe = () => {
          parent.onIframeComplete(jatos2.urlQueryParameters.frameId, jatos2.studyId);
        };
        if (resultData) {
          jatos2.endStudyWithoutRedirect(resultData, successful, message).done(endIframe);
        } else {
          jatos2.endStudyWithoutRedirect(successful, message).done(endIframe);
        }
        return;
      }
      if (isEndingStudy()) {
        console.warn("Can end/abort study only once");
        return;
      }
      setEndingStudy(true);
      if (resultData) jatos2.appendResultData(resultData);
      function end() {
        window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        var url = getURL("../end");
        if (typeof successful == "boolean" && typeof message == "string") {
          url = url + "?" + jatos2.jQuery.param({
            "successful": successful,
            "message": message
          });
        } else if (typeof successful == "boolean" && typeof message != "string") {
          url = url + "?" + jatos2.jQuery.param({
            "successful": successful
          });
        } else if (typeof successful != "boolean" && typeof message == "string") {
          url = url + "?" + jatos2.jQuery.param({
            "message": message
          });
        }
        window.location.href = url;
      }
      if (httpLoop.isBusy()) {
        setTimeout(jatos2.showOverlay, 1e3, jatos2.waitSendDataOverlayConfig);
      }
      httpLoop.whenIdle(end);
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
    jatos.waitSendDataOverlayConfig = {
      text: "Sending data. Please wait."
    };
    let startingComponent = false;
    let endingStudy = false;
    let studyRunInvalid = false;
    let showBeforeUnloadWarning = true;
    const httpLoop = createHttpLoop({
      isInitialized: () => initialization.isInitialized()
    });
    const channels = createChannels(jatos, {
      getURL,
      getAjaxErrorMsg,
      showIdOverlay,
      isEndingStudy: () => endingStudy,
      isStartingComponent: () => startingComponent,
      isStudyRunInvalid: () => studyRunInvalid,
      setStudyRunInvalid: (value) => {
        studyRunInvalid = value;
      }
    });
    const initialization = createInitialization(jatos, {
      getURL,
      getAjaxErrorMsg,
      showIdOverlay,
      httpLoop,
      channels
    });
    initialization.start();
    jatos.onError = function(onError) {
      console.warn("jatos.onError is abolished - use the specific function's error callback or Promise function");
    };
    installResultDataApi(jatos, {
      getURL,
      isInitialized: () => initialization.isInitialized(),
      isInvalidComponentPosition: (pos) => isInvalidComponentPosition(jatos.componentList, pos),
      isStudyRunInvalid: () => studyRunInvalid,
      sendToHttpLoop: httpLoop.send
    });
    installStudyRunApi(jatos, {
      beforeUnloadWarning,
      getURL,
      httpLoop,
      isEndingStudy: () => endingStudy,
      isInitialized: () => initialization.isInitialized(),
      isStartingComponent: () => startingComponent,
      isStudyRunInvalid: () => studyRunInvalid,
      setStartingComponent: (value) => {
        startingComponent = value;
      },
      setEndingStudy: (value) => {
        endingStudy = value;
      },
      stopStudyRun: () => {
        initialization.terminateHeartbeat();
        httpLoop.terminate();
        channels.stopClosedChecks();
      }
    });
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
      if (!initialization.isInitialized()) return;
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
  })();
})();
