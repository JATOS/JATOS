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
  var jQuery = {};
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
  jQuery.extend = function() {
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
          if (deep && copy && (jQuery.isPlainObject(copy) || (copyIsArray = Array.isArray(copy)))) {
            src = target[name];
            if (copyIsArray && !Array.isArray(src)) {
              clone = [];
            } else if (!copyIsArray && !jQuery.isPlainObject(src)) {
              clone = {};
            } else {
              clone = src;
            }
            copyIsArray = false;
            target[name] = jQuery.extend(deep, clone, copy);
          } else if (copy !== void 0) {
            target[name] = copy;
          }
        }
      }
    }
    return target;
  };
  jQuery.isPlainObject = function(obj) {
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
  jQuery.each = function(obj, callback) {
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
  jQuery.inArray = function(elem, arr, i) {
    return arr == null ? -1 : indexOf.call(arr, elem, i);
  };
  jQuery.each(
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
    jQuery.each(options.match(rnothtmlwhite) || [], function(_, flag) {
      object[flag] = true;
    });
    return object;
  }
  jQuery.Callbacks = function(options) {
    options = typeof options === "string" ? createOptions(options) : jQuery.extend({}, options);
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
            jQuery.each(args, function(_, arg) {
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
        jQuery.each(arguments, function(_, arg) {
          var index;
          while ((index = jQuery.inArray(arg, list, index)) > -1) {
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
        return fn ? jQuery.inArray(fn, list) > -1 : list.length > 0;
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
  jQuery.extend({
    Deferred: function(func) {
      var tuples = [
        // action, add listener, callbacks,
        // ... .then handlers, argument index, [final state]
        [
          "notify",
          "progress",
          jQuery.Callbacks("memory"),
          jQuery.Callbacks("memory"),
          2
        ],
        [
          "resolve",
          "done",
          jQuery.Callbacks("once memory"),
          jQuery.Callbacks("once memory"),
          0,
          "resolved"
        ],
        [
          "reject",
          "fail",
          jQuery.Callbacks("once memory"),
          jQuery.Callbacks("once memory"),
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
          return jQuery.Deferred(function(newDefer) {
            jQuery.each(tuples, function(_i, tuple) {
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
                  if (jQuery.Deferred.exceptionHook) {
                    jQuery.Deferred.exceptionHook(
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
                if (jQuery.Deferred.getErrorHook) {
                  process.error = jQuery.Deferred.getErrorHook();
                } else if (jQuery.Deferred.getStackHook) {
                  process.error = jQuery.Deferred.getStackHook();
                }
                globalThis.setTimeout(process);
              }
            };
          }
          return jQuery.Deferred(function(newDefer) {
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
          return obj != null ? jQuery.extend(obj, promise) : promise;
        }
      }, deferred = {};
      jQuery.each(tuples, function(i, tuple) {
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
  var Deferred = jQuery.Deferred;

  // src/jatos-promise.js
  function createDeferred() {
    return (
      /** @type {JatosDeferred} */
      Deferred()
    );
  }
  function rejectedPromise(error) {
    return createDeferred().reject(error).promise();
  }
  function isDeferredPending(deferred) {
    return deferred !== void 0 && deferred.state() === "pending";
  }

  // src/http-transport.js
  function requestHttp(options) {
    const deferred = createDeferred();
    let attemptsLeft = options.retry?.times ?? 1;
    function attempt() {
      const xhr = new XMLHttpRequest();
      xhr.open(options.method || "GET", options.url, true);
      xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");
      xhr.setRequestHeader("Accept", options.dataType === "json" ? "application/json, text/javascript, */*; q=0.01" : "*/*");
      xhr.timeout = options.timeout || 0;
      function fail(textStatus, error = textStatus) {
        const response = {
          status: xhr.status,
          statusText: textStatus === "timeout" ? "timeout" : xhr.statusText || textStatus,
          responseText: xhr.responseText
        };
        options.error?.(response, textStatus, error);
        options.statusCode?.[xhr.status]?.(response, textStatus, error);
        if (attemptsLeft > 1) {
          attemptsLeft--;
          let delay = options.retry?.timeout;
          const retryAfter = xhr.getResponseHeader("Retry-After");
          if (retryAfter) {
            const parsed = isNaN(retryAfter) ? Date.parse(retryAfter) - Date.now() : parseInt(retryAfter, 10) * 1e3;
            if (!isNaN(parsed) && parsed >= 0) delay = parsed;
          }
          if (delay) setTimeout(attempt, delay);
          else attempt();
        } else {
          deferred.reject(response, textStatus, error);
        }
      }
      xhr.onload = () => {
        if (!(xhr.status >= 200 && xhr.status < 300) && xhr.status !== 304) {
          fail("error", xhr.statusText);
          return;
        }
        let data;
        const textStatus = xhr.status === 204 ? "nocontent" : xhr.status === 304 ? "notmodified" : "success";
        if (xhr.status !== 204 && xhr.status !== 304) {
          data = xhr.responseText;
          const json = options.dataType === "json" || !options.dataType && /\bjson\b/i.test(xhr.getResponseHeader("Content-Type") || "");
          if (json) {
            try {
              data = JSON.parse(data);
            } catch (error) {
              fail("parsererror", error);
              return;
            }
          }
        }
        options.success?.(data, textStatus, xhr);
        options.statusCode?.[xhr.status]?.(data, textStatus, xhr);
        deferred.resolve(data, textStatus, xhr);
      };
      xhr.onerror = () => fail("error");
      xhr.ontimeout = () => fail("timeout");
      xhr.onabort = () => fail("abort");
      xhr.send(null);
    }
    attempt();
    return deferred.promise();
  }
  function getHttpErrorMessage(response) {
    if (response.statusText === "timeout") {
      return "JATOS server not responding";
    }
    return response.statusText + ": " + (response.responseText || "Error during Ajax call to JATOS server.");
  }

  // src/logging.js
  function installLoggingApi(jatos2, { getURL, isInitialized, sendToHttpLoop }) {
    jatos2.onError = function(onError) {
      console.warn("jatos.onError is abolished - use the specific function's error callback or Promise function");
    };
    jatos2.logError = function(logErrorMsg) {
      console.warn("jatos.logError is abolished - use jatos.log instead");
    };
    jatos2.log = function(logMsg) {
      if (!isInitialized()) return;
      const request = {
        url: getURL("log"),
        method: "POST",
        data: logMsg,
        contentType: "text/plain; charset=UTF-8",
        timeout: jatos2.httpTimeout,
        retry: jatos2.httpRetry,
        retryWait: jatos2.httpRetryWait
      };
      sendToHttpLoop(request);
    };
    jatos2.catchAndLogErrors = function() {
      window.addEventListener("error", function(e) {
        jatos2.log(`Via 'error' event in ${e.filename}:${e.lineno} - ${e.message}`);
      });
      window.addEventListener("unhandledrejection", function(e) {
        jatos2.log(`Via 'unhandledrejection' event in ${e.filename}:${e.lineno} - ${e.message}`);
      });
      const errorLog = console.error;
      const warnLog = console.warn;
      console.error = function(message) {
        jatos2.log("Via console.error - " + message);
        errorLog.apply(this, arguments);
      };
      console.warn = function(message) {
        jatos2.log("Via console.warn - " + message);
        warnLog.apply(this, arguments);
      };
    };
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
      studyRunState,
      getURL,
      isInitialized,
      isInvalidComponentPosition: isInvalidComponentPosition2,
      sendToHttpLoop
    } = dependencies;
    jatos2.submitResultData = function(resultData, onSuccess, onError) {
      return submitOrAppendResultData(resultData, false, onSuccess, onError);
    };
    jatos2.appendResultData = function(resultData, onSuccess, onError) {
      return submitOrAppendResultData(resultData, true, onSuccess, onError);
    };
    function submitOrAppendResultData(resultData, append, onSuccess, onError) {
      if (studyRunState.invalid) {
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
      if (studyRunState.invalid) {
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
      if (studyRunState.invalid) {
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

  // node_modules/fast-json-patch/module/helpers.mjs
  /*!
   * https://github.com/Starcounter-Jack/JSON-Patch
   * (c) 2017-2022 Joachim Wester
   * MIT licensed
   */
  var __extends = /* @__PURE__ */ (function() {
    var extendStatics = function(d, b) {
      extendStatics = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(d2, b2) {
        d2.__proto__ = b2;
      } || function(d2, b2) {
        for (var p in b2) if (b2.hasOwnProperty(p)) d2[p] = b2[p];
      };
      return extendStatics(d, b);
    };
    return function(d, b) {
      extendStatics(d, b);
      function __() {
        this.constructor = d;
      }
      d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
  })();
  var _hasOwnProperty = Object.prototype.hasOwnProperty;
  function hasOwnProperty(obj, key) {
    return _hasOwnProperty.call(obj, key);
  }
  function _objectKeys(obj) {
    if (Array.isArray(obj)) {
      var keys_1 = new Array(obj.length);
      for (var k = 0; k < keys_1.length; k++) {
        keys_1[k] = "" + k;
      }
      return keys_1;
    }
    if (Object.keys) {
      return Object.keys(obj);
    }
    var keys = [];
    for (var i in obj) {
      if (hasOwnProperty(obj, i)) {
        keys.push(i);
      }
    }
    return keys;
  }
  function _deepClone(obj) {
    switch (typeof obj) {
      case "object":
        return JSON.parse(JSON.stringify(obj));
      //Faster than ES5 clone - http://jsperf.com/deep-cloning-of-objects/5
      case "undefined":
        return null;
      //this is how JSON.stringify behaves for array items
      default:
        return obj;
    }
  }
  function isInteger(str) {
    var i = 0;
    var len = str.length;
    var charCode;
    while (i < len) {
      charCode = str.charCodeAt(i);
      if (charCode >= 48 && charCode <= 57) {
        i++;
        continue;
      }
      return false;
    }
    return true;
  }
  function unescapePathComponent(path) {
    return path.replace(/~1/g, "/").replace(/~0/g, "~");
  }
  function hasUndefined(obj) {
    if (obj === void 0) {
      return true;
    }
    if (obj) {
      if (Array.isArray(obj)) {
        for (var i_1 = 0, len = obj.length; i_1 < len; i_1++) {
          if (hasUndefined(obj[i_1])) {
            return true;
          }
        }
      } else if (typeof obj === "object") {
        var objKeys = _objectKeys(obj);
        var objKeysLength = objKeys.length;
        for (var i = 0; i < objKeysLength; i++) {
          if (hasUndefined(obj[objKeys[i]])) {
            return true;
          }
        }
      }
    }
    return false;
  }
  function patchErrorMessageFormatter(message, args) {
    var messageParts = [message];
    for (var key in args) {
      var value = typeof args[key] === "object" ? JSON.stringify(args[key], null, 2) : args[key];
      if (typeof value !== "undefined") {
        messageParts.push(key + ": " + value);
      }
    }
    return messageParts.join("\n");
  }
  var PatchError = (
    /** @class */
    (function(_super) {
      __extends(PatchError2, _super);
      function PatchError2(message, name, index, operation, tree) {
        var _newTarget = this.constructor;
        var _this = _super.call(this, patchErrorMessageFormatter(message, { name, index, operation, tree })) || this;
        _this.name = name;
        _this.index = index;
        _this.operation = operation;
        _this.tree = tree;
        Object.setPrototypeOf(_this, _newTarget.prototype);
        _this.message = patchErrorMessageFormatter(message, { name, index, operation, tree });
        return _this;
      }
      return PatchError2;
    })(Error)
  );

  // node_modules/fast-json-patch/module/core.mjs
  var JsonPatchError = PatchError;
  var objOps = {
    add: function(obj, key, document2) {
      obj[key] = this.value;
      return { newDocument: document2 };
    },
    remove: function(obj, key, document2) {
      var removed = obj[key];
      delete obj[key];
      return { newDocument: document2, removed };
    },
    replace: function(obj, key, document2) {
      var removed = obj[key];
      obj[key] = this.value;
      return { newDocument: document2, removed };
    },
    move: function(obj, key, document2) {
      var removed = getValueByPointer(document2, this.path);
      if (removed) {
        removed = _deepClone(removed);
      }
      var originalValue = applyOperation(document2, { op: "remove", path: this.from }).removed;
      applyOperation(document2, { op: "add", path: this.path, value: originalValue });
      return { newDocument: document2, removed };
    },
    copy: function(obj, key, document2) {
      var valueToCopy = getValueByPointer(document2, this.from);
      applyOperation(document2, { op: "add", path: this.path, value: _deepClone(valueToCopy) });
      return { newDocument: document2 };
    },
    test: function(obj, key, document2) {
      return { newDocument: document2, test: _areEquals(obj[key], this.value) };
    },
    _get: function(obj, key, document2) {
      this.value = obj[key];
      return { newDocument: document2 };
    }
  };
  var arrOps = {
    add: function(arr, i, document2) {
      if (isInteger(i)) {
        arr.splice(i, 0, this.value);
      } else {
        arr[i] = this.value;
      }
      return { newDocument: document2, index: i };
    },
    remove: function(arr, i, document2) {
      var removedList = arr.splice(i, 1);
      return { newDocument: document2, removed: removedList[0] };
    },
    replace: function(arr, i, document2) {
      var removed = arr[i];
      arr[i] = this.value;
      return { newDocument: document2, removed };
    },
    move: objOps.move,
    copy: objOps.copy,
    test: objOps.test,
    _get: objOps._get
  };
  function getValueByPointer(document2, pointer) {
    if (pointer == "") {
      return document2;
    }
    var getOriginalDestination = { op: "_get", path: pointer };
    applyOperation(document2, getOriginalDestination);
    return getOriginalDestination.value;
  }
  function applyOperation(document2, operation, validateOperation, mutateDocument, banPrototypeModifications, index) {
    if (validateOperation === void 0) {
      validateOperation = false;
    }
    if (mutateDocument === void 0) {
      mutateDocument = true;
    }
    if (banPrototypeModifications === void 0) {
      banPrototypeModifications = true;
    }
    if (index === void 0) {
      index = 0;
    }
    if (validateOperation) {
      if (typeof validateOperation == "function") {
        validateOperation(operation, 0, document2, operation.path);
      } else {
        validator(operation, 0);
      }
    }
    if (operation.path === "") {
      var returnValue = { newDocument: document2 };
      if (operation.op === "add") {
        returnValue.newDocument = operation.value;
        return returnValue;
      } else if (operation.op === "replace") {
        returnValue.newDocument = operation.value;
        returnValue.removed = document2;
        return returnValue;
      } else if (operation.op === "move" || operation.op === "copy") {
        returnValue.newDocument = getValueByPointer(document2, operation.from);
        if (operation.op === "move") {
          returnValue.removed = document2;
        }
        return returnValue;
      } else if (operation.op === "test") {
        returnValue.test = _areEquals(document2, operation.value);
        if (returnValue.test === false) {
          throw new JsonPatchError("Test operation failed", "TEST_OPERATION_FAILED", index, operation, document2);
        }
        returnValue.newDocument = document2;
        return returnValue;
      } else if (operation.op === "remove") {
        returnValue.removed = document2;
        returnValue.newDocument = null;
        return returnValue;
      } else if (operation.op === "_get") {
        operation.value = document2;
        return returnValue;
      } else {
        if (validateOperation) {
          throw new JsonPatchError("Operation `op` property is not one of operations defined in RFC-6902", "OPERATION_OP_INVALID", index, operation, document2);
        } else {
          return returnValue;
        }
      }
    } else {
      if (!mutateDocument) {
        document2 = _deepClone(document2);
      }
      var path = operation.path || "";
      var keys = path.split("/");
      var obj = document2;
      var t = 1;
      var len = keys.length;
      var existingPathFragment = void 0;
      var key = void 0;
      var validateFunction = void 0;
      if (typeof validateOperation == "function") {
        validateFunction = validateOperation;
      } else {
        validateFunction = validator;
      }
      while (true) {
        key = keys[t];
        if (key && key.indexOf("~") != -1) {
          key = unescapePathComponent(key);
        }
        if (banPrototypeModifications && (key == "__proto__" || key == "prototype" && t > 0 && keys[t - 1] == "constructor")) {
          throw new TypeError("JSON-Patch: modifying `__proto__` or `constructor/prototype` prop is banned for security reasons, if this was on purpose, please set `banPrototypeModifications` flag false and pass it to this function. More info in fast-json-patch README");
        }
        if (validateOperation) {
          if (existingPathFragment === void 0) {
            if (obj[key] === void 0) {
              existingPathFragment = keys.slice(0, t).join("/");
            } else if (t == len - 1) {
              existingPathFragment = operation.path;
            }
            if (existingPathFragment !== void 0) {
              validateFunction(operation, 0, document2, existingPathFragment);
            }
          }
        }
        t++;
        if (Array.isArray(obj)) {
          if (key === "-") {
            key = obj.length;
          } else {
            if (validateOperation && !isInteger(key)) {
              throw new JsonPatchError("Expected an unsigned base-10 integer value, making the new referenced value the array element with the zero-based index", "OPERATION_PATH_ILLEGAL_ARRAY_INDEX", index, operation, document2);
            } else if (isInteger(key)) {
              key = ~~key;
            }
          }
          if (t >= len) {
            if (validateOperation && operation.op === "add" && key > obj.length) {
              throw new JsonPatchError("The specified index MUST NOT be greater than the number of elements in the array", "OPERATION_VALUE_OUT_OF_BOUNDS", index, operation, document2);
            }
            var returnValue = arrOps[operation.op].call(operation, obj, key, document2);
            if (returnValue.test === false) {
              throw new JsonPatchError("Test operation failed", "TEST_OPERATION_FAILED", index, operation, document2);
            }
            return returnValue;
          }
        } else {
          if (t >= len) {
            var returnValue = objOps[operation.op].call(operation, obj, key, document2);
            if (returnValue.test === false) {
              throw new JsonPatchError("Test operation failed", "TEST_OPERATION_FAILED", index, operation, document2);
            }
            return returnValue;
          }
        }
        obj = obj[key];
        if (validateOperation && t < len && (!obj || typeof obj !== "object")) {
          throw new JsonPatchError("Cannot perform operation at the desired path", "OPERATION_PATH_UNRESOLVABLE", index, operation, document2);
        }
      }
    }
  }
  function applyPatch(document2, patch, validateOperation, mutateDocument, banPrototypeModifications) {
    if (mutateDocument === void 0) {
      mutateDocument = true;
    }
    if (banPrototypeModifications === void 0) {
      banPrototypeModifications = true;
    }
    if (validateOperation) {
      if (!Array.isArray(patch)) {
        throw new JsonPatchError("Patch sequence must be an array", "SEQUENCE_NOT_AN_ARRAY");
      }
    }
    if (!mutateDocument) {
      document2 = _deepClone(document2);
    }
    var results = new Array(patch.length);
    for (var i = 0, length_1 = patch.length; i < length_1; i++) {
      results[i] = applyOperation(document2, patch[i], validateOperation, true, banPrototypeModifications, i);
      document2 = results[i].newDocument;
    }
    results.newDocument = document2;
    return results;
  }
  function validator(operation, index, document2, existingPathFragment) {
    if (typeof operation !== "object" || operation === null || Array.isArray(operation)) {
      throw new JsonPatchError("Operation is not an object", "OPERATION_NOT_AN_OBJECT", index, operation, document2);
    } else if (!objOps[operation.op]) {
      throw new JsonPatchError("Operation `op` property is not one of operations defined in RFC-6902", "OPERATION_OP_INVALID", index, operation, document2);
    } else if (typeof operation.path !== "string") {
      throw new JsonPatchError("Operation `path` property is not a string", "OPERATION_PATH_INVALID", index, operation, document2);
    } else if (operation.path.indexOf("/") !== 0 && operation.path.length > 0) {
      throw new JsonPatchError('Operation `path` property must start with "/"', "OPERATION_PATH_INVALID", index, operation, document2);
    } else if ((operation.op === "move" || operation.op === "copy") && typeof operation.from !== "string") {
      throw new JsonPatchError("Operation `from` property is not present (applicable in `move` and `copy` operations)", "OPERATION_FROM_REQUIRED", index, operation, document2);
    } else if ((operation.op === "add" || operation.op === "replace" || operation.op === "test") && operation.value === void 0) {
      throw new JsonPatchError("Operation `value` property is not present (applicable in `add`, `replace` and `test` operations)", "OPERATION_VALUE_REQUIRED", index, operation, document2);
    } else if ((operation.op === "add" || operation.op === "replace" || operation.op === "test") && hasUndefined(operation.value)) {
      throw new JsonPatchError("Operation `value` property is not present (applicable in `add`, `replace` and `test` operations)", "OPERATION_VALUE_CANNOT_CONTAIN_UNDEFINED", index, operation, document2);
    } else if (document2) {
      if (operation.op == "add") {
        var pathLen = operation.path.split("/").length;
        var existingPathLen = existingPathFragment.split("/").length;
        if (pathLen !== existingPathLen + 1 && pathLen !== existingPathLen) {
          throw new JsonPatchError("Cannot perform an `add` operation at the desired path", "OPERATION_PATH_CANNOT_ADD", index, operation, document2);
        }
      } else if (operation.op === "replace" || operation.op === "remove" || operation.op === "_get") {
        if (operation.path !== existingPathFragment) {
          throw new JsonPatchError("Cannot perform the operation at a path that does not exist", "OPERATION_PATH_UNRESOLVABLE", index, operation, document2);
        }
      } else if (operation.op === "move" || operation.op === "copy") {
        var existingValue = { op: "_get", path: operation.from, value: void 0 };
        var error = validate([existingValue], document2);
        if (error && error.name === "OPERATION_PATH_UNRESOLVABLE") {
          throw new JsonPatchError("Cannot perform the operation from a path that does not exist", "OPERATION_FROM_UNRESOLVABLE", index, operation, document2);
        }
      }
    }
  }
  function validate(sequence, document2, externalValidator) {
    try {
      if (!Array.isArray(sequence)) {
        throw new JsonPatchError("Patch sequence must be an array", "SEQUENCE_NOT_AN_ARRAY");
      }
      if (document2) {
        applyPatch(_deepClone(document2), _deepClone(sequence), externalValidator || true);
      } else {
        externalValidator = externalValidator || validator;
        for (var i = 0; i < sequence.length; i++) {
          externalValidator(sequence[i], i, document2, void 0);
        }
      }
    } catch (e) {
      if (e instanceof JsonPatchError) {
        return e;
      } else {
        throw e;
      }
    }
  }
  function _areEquals(a, b) {
    if (a === b)
      return true;
    if (a && b && typeof a == "object" && typeof b == "object") {
      var arrA = Array.isArray(a), arrB = Array.isArray(b), i, length, key;
      if (arrA && arrB) {
        length = a.length;
        if (length != b.length)
          return false;
        for (i = length; i-- !== 0; )
          if (!_areEquals(a[i], b[i]))
            return false;
        return true;
      }
      if (arrA != arrB)
        return false;
      var keys = Object.keys(a);
      length = keys.length;
      if (length !== Object.keys(b).length)
        return false;
      for (i = length; i-- !== 0; )
        if (!b.hasOwnProperty(keys[i]))
          return false;
      for (i = length; i-- !== 0; ) {
        key = keys[i];
        if (!_areEquals(a[key], b[key]))
          return false;
      }
      return true;
    }
    return a !== a && b !== b;
  }

  // src/utils/clone-json.js
  function cloneJsonObj(obj) {
    if (null === obj || "object" != typeof obj) return obj;
    if (obj instanceof Array) {
      const copy = [];
      const len = obj.length;
      for (let i = 0; i < len; i++) {
        copy[i] = cloneJsonObj(obj[i]);
      }
      return copy;
    }
    if (obj instanceof Object) {
      const copy = {};
      for (const attr in obj) {
        if (obj.hasOwnProperty(attr)) copy[attr] = cloneJsonObj(obj[attr]);
      }
      return copy;
    }
    throw new Error("Unable to copy obj! Its type isn't supported.");
  }

  // src/channel-session.js
  /*! fast-json-patch 3.1.1
   * (The MIT License)
   *
   * Copyright (c) 2013, 2014, 2020 Joachim Wester
   *
   * Permission is hereby granted, free of charge, to any person obtaining
   * a copy of this software and associated documentation files (the
   * 'Software'), to deal in the Software without restriction, including
   * without limitation the rights to use, copy, modify, merge, publish,
   * distribute, sublicense, and/or sell copies of the Software, and to
   * permit persons to whom the Software is furnished to do so, subject to
   * the following conditions:
   *
   * The above copyright notice and this permission notice shall be
   * included in all copies or substantial portions of the Software.
   *
   * THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
   * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
   * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
   * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
   * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
   * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
   * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
   */
  function createSessionApi(getData, sendPatch) {
    const session = {};
    session.get = function(name) {
      const obj = getValueByPointer(getData(), "/" + name);
      return cloneJsonObj(obj);
    };
    session.getAll = function() {
      const obj = session.find("");
      return cloneJsonObj(obj);
    };
    session.find = function(path) {
      const obj = getValueByPointer(getData(), path);
      return cloneJsonObj(obj);
    };
    session.test = function(path, value) {
      const obj = getValueByPointer(getData(), path);
      return obj === value;
    };
    session.defined = function(path) {
      return !session.test(path, void 0);
    };
    session.add = function(path, value, onSuccess, onFail) {
      const patch = generatePatch("add", path, value, null);
      return sendPatch(patch, onSuccess, onFail);
    };
    session.set = function(name, value, onSuccess, onFail) {
      const patch = generatePatch("add", "/" + name, value, null);
      return sendPatch(patch, onSuccess, onFail);
    };
    session.setAll = function(value, onSuccess, onFail) {
      return session.replace("", value, onSuccess, onFail);
    };
    session.remove = function(path, onSuccess, onFail) {
      const patch = generatePatch("remove", path, null, null);
      return sendPatch(patch, onSuccess, onFail);
    };
    session.clear = function(onSuccess, onFail) {
      const patch = generatePatch("replace", "", {}, null);
      return sendPatch(patch, onSuccess, onFail);
    };
    session.replace = function(path, value, onSuccess, onFail) {
      const patch = generatePatch("replace", path, value, null);
      return sendPatch(patch, onSuccess, onFail);
    };
    session.copy = function(from, path, onSuccess, onFail) {
      const patch = generatePatch("copy", path, null, from);
      return sendPatch(patch, onSuccess, onFail);
    };
    session.move = function(from, path, onSuccess, onFail) {
      const patch = generatePatch("move", path, null, from);
      return sendPatch(patch, onSuccess, onFail);
    };
    return session;
  }
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
  function applySessionUpdate(data, patches, snapshot) {
    if (patches !== void 0) {
      const results = applyPatch(data, patches);
      if (results && results.newDocument !== void 0) {
        data = results.newDocument;
      }
    }
    if (snapshot !== void 0) {
      data = snapshot === null ? {} : snapshot;
    }
    return data;
  }

  // src/channels.js
  function createChannels(jatos2, dependencies) {
    const {
      requestHttp: requestHttp2,
      studyRunState,
      getURL,
      showIdOverlay
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
    let batchSessionVersion;
    let groupSessionVersion;
    jatos2.batchSessionVersioning = true;
    jatos2.groupSessionVersioning = true;
    let batchChannel;
    let groupChannel;
    let groupChannelCallbacks;
    const batchHeartbeat = createHeartbeat(() => batchChannel, handleBatchChannelHeartbeatFail);
    const groupHeartbeat = createHeartbeat(() => groupChannel, () => {
      callMany("Group channel heartbeat fail", groupChannelCallbacks.onError, console.warn);
      reopenGroupChannel();
    });
    const batchClosedCheck = createClosedCheck(
      () => batchChannel,
      () => console.info("Batch channel closed"),
      () => {
        setBatchChannelDead();
        reopenBatchChannel();
      }
    );
    const groupClosedCheck = createClosedCheck(
      () => groupChannel,
      () => callMany("Group channel closed", console.info, groupChannelCallbacks.onError),
      reopenGroupChannel
    );
    const webSocketSupported = "WebSocket" in window;
    let openingBatchChannelDeferred;
    let openingGroupChannelDeferred;
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
      if (studyRunState.ending || studyRunState.starting) {
        const errorMsg = "Won't open batch channel because study is about to move to the next component or finish.";
        console.info(errorMsg);
        return rejectedPromise(errorMsg);
      }
      if (studyRunState.invalid) {
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
        batchHeartbeat.start();
        batchClosedCheck.start();
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
    function createHeartbeat(getChannel, onFailure) {
      let interval;
      let timeouts = [];
      function start() {
        clearInterval(interval);
        interval = setInterval(() => {
          const channel = getChannel();
          if (channel.readyState === channel.OPEN) {
            channel.send('{"heartbeat":"ping"}');
            timeouts.push(setTimeout(onFailure, jatos2.channelHeartbeatTimeoutTime));
          }
        }, jatos2.channelHeartbeatInterval);
      }
      function acknowledge() {
        timeouts.forEach((timeout) => clearTimeout(timeout));
        timeouts = [];
      }
      function stop() {
        acknowledge();
        clearInterval(interval);
      }
      return { start, acknowledge, stop };
    }
    function handleBatchChannelHeartbeatFail() {
      console.warn("Batch channel heartbeat fail");
      setBatchChannelDead();
      reopenBatchChannel();
    }
    function createClosedCheck(getChannel, notifyClosed, reconnect) {
      let interval;
      function start() {
        stop();
        interval = setInterval(() => {
          const channel = getChannel();
          if (channel.readyState === channel.CLOSED) {
            notifyClosed();
            stop();
            reconnect();
          }
        }, jatos2.channelClosedCheckInterval);
      }
      function stop() {
        clearInterval(interval);
      }
      return { start, stop };
    }
    function clearBatchChannel() {
      batchSessionData = {};
      batchSessionVersion = null;
      batchHeartbeat.stop();
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
        batchHeartbeat.acknowledge();
        setBatchChannelAlive();
        return;
      }
      batchSessionData = applySessionUpdate(batchSessionData, batchMsg.patches, batchMsg.data);
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
          batchClosedCheck.stop();
          setBatchChannelDead();
          studyRunState.invalid = true;
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
    const sendBatchSessionPatch = createSessionSender({
      kind: "batch",
      getChannel: () => batchChannel,
      isVersioning: () => jatos2.batchSessionVersioning,
      timeouts: batchSessionTimeouts,
      createMessage: (id, patches, versioning) => ({
        action: "SESSION",
        id,
        patches,
        version: batchSessionVersion,
        versioning
      })
    });
    jatos2.batchSession = createSessionApi(() => batchSessionData, sendBatchSessionPatch);
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
      if (studyRunState.ending || studyRunState.starting) {
        const errorMsg = "Won't open group channel because study is about to move to the next component or finish.";
        callMany(errorMsg, console.warn, groupChannelCallbacks.onError);
        return rejectedPromise(errorMsg);
      }
      if (studyRunState.invalid) {
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
        groupHeartbeat.start();
        groupClosedCheck.start();
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
    function clearGroupChannel() {
      jatos2.groupMemberId = null;
      jatos2.groupResultId = null;
      jatos2.groupMembers = [];
      jatos2.groupChannels = [];
      groupSessionData = {};
      groupSessionVersion = null;
      groupState = null;
      groupHeartbeat.stop();
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
        groupHeartbeat.acknowledge();
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
      groupSessionData = applySessionUpdate(groupSessionData, groupMsg.sessionPatches, groupMsg.sessionData);
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
          groupClosedCheck.stop();
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
    const sendGroupSessionPatch = createSessionSender({
      kind: "group",
      getChannel: () => groupChannel,
      isVersioning: () => jatos2.groupSessionVersioning,
      timeouts: groupSessionTimeouts,
      createMessage: (id, patches, versioning) => ({
        action: "SESSION",
        sessionActionId: id,
        sessionPatches: patches,
        sessionVersion: groupSessionVersion,
        sessionVersioning: versioning
      })
    });
    jatos2.groupSession = createSessionApi(() => groupSessionData, sendGroupSessionPatch);
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
      if (studyRunState.invalid) {
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
      if (studyRunState.invalid) {
        const errorMsg = "Can't reassign group. This study run is invalid.";
        callMany(errorMsg, console.warn, onFail);
        return rejectedPromise(errorMsg);
      }
      reassigningGroupDeferred = createDeferred();
      requestHttp2({
        url: getURL("../group/reassign"),
        method: "GET",
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
          const errMsg = getHttpErrorMessage(err);
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
      if (studyRunState.invalid) {
        const errorMsg = "Can't leave group. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      leavingGroupDeferred = createDeferred();
      requestHttp2({
        url: getURL("../group/leave"),
        retry: { times: jatos2.httpRetry, timeout: jatos2.httpRetryWait },
        method: "GET",
        timeout: jatos2.httpTimeout,
        success: function(response) {
          groupClosedCheck.stop();
          callWithArgs(onSuccess, response);
          leavingGroupDeferred.resolve(response);
        },
        error: function(err) {
          const errMsg = getHttpErrorMessage(err);
          callMany(errMsg, onError, console.error);
          leavingGroupDeferred.reject(errMsg);
        }
      });
      return leavingGroupDeferred.promise();
    };
    function createSessionSender({ kind, getChannel, isVersioning, timeouts, createMessage }) {
      let counter = 0;
      let pending;
      return function sendSessionPatch(patches, onSuccess, onError) {
        const channel = getChannel();
        if (!channel || channel.readyState !== channel.OPEN) {
          const error = `Can't send ${kind} session patch. No open ${kind} channel. Patch: ${patches.op} ${patches.path}.`;
          callMany(error, onError, console.error);
          return rejectedPromise(error);
        }
        if (isVersioning() && isDeferredPending(pending)) {
          const error = `Can send only one ${kind} session patch at a time. Patch: ${patches.op} ${patches.path}.`;
          callMany(error, onError, console.error);
          return rejectedPromise(error);
        }
        if (studyRunState.invalid) {
          const error = `Can't send ${kind} session patch. This study run is invalid. Patch: ${patches.op} ${patches.path}.`;
          callMany(error, onError, console.warn);
          return rejectedPromise(error);
        }
        const deferred = createDeferred();
        if (isVersioning()) pending = deferred;
        const id = counter++;
        const message = createMessage(
          id,
          patches.constructor === Array ? patches : [patches],
          !!isVersioning()
        );
        try {
          channel.send(JSON.stringify(message));
          setChannelSendingTimeoutAndPromiseResolution(deferred, timeouts, id, onSuccess, onError);
        } catch (error) {
          callMany(error, onError, console.error);
          deferred.reject();
        }
        return deferred.promise();
      };
    }
    function setChannelSendingTimeoutAndPromiseResolution(deferred, sessionTimeouts, sessionActionId, onSuccess, onError) {
      const timeoutId = setTimeout(function() {
        callWithArgs(onError, "Timeout sending session patch");
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
          callWithArgs(onError, msg);
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
        batchClosedCheck.stop();
        groupClosedCheck.stop();
      }
    };
  }

  // src/browser-ui.js
  function createBrowserUi(jatos2) {
    jatos2.waitSendDataOverlayConfig = {
      text: "Sending data. Please wait."
    };
    let showBeforeUnloadWarning = true;
    function onLoad() {
      if (showBeforeUnloadWarning && !jatos2.componentProperties.reloadable) {
        window.addEventListener("beforeunload", beforeUnloadWarning, { capture: true });
      }
    }
    function beforeUnloadWarning(event) {
      event.preventDefault();
      event.returnValue = "Are you sure you want to leave?";
    }
    function removeBeforeUnloadWarning() {
      window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
    }
    jatos2.showBeforeUnloadWarning = function(show) {
      showBeforeUnloadWarning = show;
      if (show) {
        window.addEventListener("beforeunload", beforeUnloadWarning, { capture: true });
      } else {
        removeBeforeUnloadWarning();
      }
    };
    jatos2.showOverlay = function(config) {
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
        const imgUrl = config && typeof config.imgUrl == "string" ? config.imgUrl : "jatos-publix/images/waiting.gif";
        const waitingImg = document.createElement("img");
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
    jatos2.removeOverlay = () => jatos2.removeOverlays();
    jatos2.removeOverlays = function(force) {
      document.querySelectorAll(".jatosOverlay").forEach((el) => {
        if (el.dataset.keep === "false" || force) el.remove();
      });
    };
    function showIdOverlay() {
      if (jatos2.workerType !== "Jatos") return;
      const idObj = {};
      if (jatos2.frameId) idObj["frame"] = jatos2.frameId;
      if (jatos2.workerId) idObj["worker"] = jatos2.workerId;
      if (jatos2.studyResultId) idObj["study result"] = jatos2.studyResultId;
      if (jatos2.groupResultId) idObj["group"] = jatos2.groupResultId;
      const text = Object.entries(idObj).map(([key, value]) => `${key}: ${value}`).join("\n");
      jatos2.showOverlay({
        id: "idOverlay",
        text,
        style: "position:fixed;top:unset;left:4px;bottom:4px;transform:unset;font-size:10px;letter-spacing:0px;white-space:pre;line-height:normal;letter-spacing:normal;word-spacing:normal;text-align:left;",
        keep: true,
        showImg: false
      });
    }
    jatos2.addAbortButton = function(config) {
      const buttonText = config && typeof config.text == "string" ? config.text : "Cancel";
      const confirm = config && typeof config.confirm == "boolean" ? config.confirm : true;
      const confirmText = config && typeof config.confirmText == "string" ? config.confirmText : "Do you really want to cancel this study?";
      const tooltip = config && typeof config.tooltip == "string" ? config.tooltip : "Cancels this study and deletes all already submitted data";
      const msg = config && typeof config.msg == "string" ? config.msg : "Worker decided to abort";
      let style = "color:black;font-family:Sans-Serif;font-size:20px;letter-spacing:2px;position:fixed;margin:2em 0 0 2em;bottom:1em;right:1em;opacity:0.6;z-index:9999;cursor:pointer;text-shadow:-1px 0 white, 0 1px white, 1px 0 white, 0 -1px white;";
      if (config && typeof config.style == "string") style += ";" + config.style;
      const text = document.createTextNode(buttonText);
      const buttonDiv = document.createElement("div");
      buttonDiv.appendChild(text);
      buttonDiv.style.cssText = style;
      buttonDiv.setAttribute("title", tooltip);
      buttonDiv.addEventListener("click", function() {
        if (!confirm || window.confirm(confirmText)) {
          if (config && typeof config.action == "function") {
            config.action(msg);
          } else {
            jatos2.abortStudy(msg);
          }
        }
      });
      document.body.appendChild(buttonDiv);
    };
    return { onLoad, showIdOverlay, removeBeforeUnloadWarning };
  }

  // src/initialization.js
  function createInitialization(jatos2, dependencies) {
    const { requestHttp: requestHttp2, getURL, showIdOverlay, httpLoop, channels } = dependencies;
    let initialized = false;
    let jatosOnLoadEventFired = false;
    const jatosOnLoadEvent = new Event("jatosOnLoad");
    let heartbeatWorker;
    function initJatos() {
      createDeferred().resolve().promise().then(function() {
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
      return requestHttp2({
        url: getURL("initData"),
        retry: { times: jatos2.httpRetry, timeout: jatos2.httpRetryWait },
        method: "GET",
        dataType: "json",
        timeout: jatos2.httpTimeout,
        success: setInitData,
        error: (err) => console.error(getHttpErrorMessage(err))
      });
    }
    function setInitData(initData) {
      jatos2.batchProperties = initData.batchProperties;
      if (typeof jatos2.batchProperties.batchInput != "undefined" && jatos2.studyProperties.studyInput !== null) {
        jatos2.batchJsonInput = JSON.parse(jatos2.batchProperties.batchInput);
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
        jatos2.studyJsonInput = JSON.parse(jatos2.studyProperties.studyInput);
      } else {
        jatos2.studyJsonInput = {};
      }
      jatos2.studyInput = jatos2.studyJsonInput;
      delete jatos2.studyProperties.studyInput;
      jatos2.componentList = initData.componentList;
      jatos2.studyLength = initData.componentList.length;
      jatos2.componentProperties = initData.componentProperties;
      if (typeof jatos2.componentProperties.componentInput != "undefined" && jatos2.componentProperties.componentInput !== null) {
        jatos2.componentJsonInput = JSON.parse(jatos2.componentProperties.componentInput);
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
      start: initJatos,
      isInitialized: () => initialized,
      terminateHeartbeat: () => heartbeatWorker.terminate()
    };
  }

  // src/utils/query.js
  function encodeQuery(parameters) {
    return Object.entries(parameters).map(
      ([key, value]) => encodeURIComponent(key) + "=" + encodeURIComponent(value == null ? "" : value)
    ).join("&");
  }

  // src/study-run.js
  function createStudyRunState() {
    return { starting: false, ending: false, invalid: false };
  }
  function installStudyRunApi(jatos2, dependencies) {
    const {
      studyRunState,
      removeBeforeUnloadWarning,
      getURL,
      httpLoop,
      isInitialized,
      stopStudyRun
    } = dependencies;
    jatos2.setStudySessionData = function(studySessionData, onSuccess, onError) {
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
      return httpLoop.send(request, onSuccess, onError).promise();
    };
    jatos2.startComponent = function(componentIdOrUuid, resultData, messageOrOnError, onError) {
      if (!isInitialized()) {
        console.error("jatos.js not yet initialized");
        return;
      }
      let componentUuid;
      let message;
      if (typeof componentIdOrUuid === "number") {
        componentUuid = jatos2.componentList.find((c) => c.id === componentIdOrUuid).uuid;
      } else {
        componentUuid = componentIdOrUuid;
      }
      ({ message, onError } = normalizeStartComponentArguments(messageOrOnError, onError));
      if (studyRunState.invalid) {
        callMany("Can't start component. This study run is invalid.", onError, console.warn);
        return;
      }
      if (studyRunState.starting) {
        callMany("Can start only one component at the same time", onError, console.warn);
        return;
      }
      if (studyRunState.ending) {
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
      studyRunState.starting = true;
      if (resultData) jatos2.appendResultData(resultData);
      jatos2.setStudySessionData(jatos2.studySessionData);
      const start = function() {
        removeBeforeUnloadWarning();
        let url = getURL("../" + componentUuid + "/start");
        if (message) url = url + "?" + encodeQuery({ "message": message });
        window.location.href = url;
      };
      if (httpLoop.isBusy()) {
        setTimeout(jatos2.showOverlay, 1e3, jatos2.waitSendDataOverlayConfig);
      }
      httpLoop.whenIdle(start);
    };
    jatos2.startComponentByPos = function(componentPos, resultData, messageOrOnError, onError) {
      if (isInvalidComponentPosition(jatos2.componentList, componentPos)) {
        onError = parseLookupErrorCallback(messageOrOnError, onError);
        callMany("Component position does not exist", onError, console.error);
        return;
      }
      const componentUuid = jatos2.componentList[componentPos - 1].uuid;
      jatos2.startComponent(componentUuid, resultData, messageOrOnError, onError);
    };
    jatos2.startComponentByTitle = function(title, resultData, messageOrOnError, onError) {
      const component = jatos2.componentList.find((component2) => component2.title === title);
      if (!component) {
        onError = parseLookupErrorCallback(messageOrOnError, onError);
        callMany(`Component with title ${title} does not exist`, onError, console.error);
        return;
      }
      const componentUuid = component.uuid;
      jatos2.startComponent(componentUuid, resultData, messageOrOnError, onError);
    };
    jatos2.startNextComponent = function(resultData, messageOrOnError, onError) {
      const { message } = normalizeStartComponentArguments(messageOrOnError, onError);
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
          jatos2.startComponent(nextComponentUuid, resultData, messageOrOnError, onError);
          break;
        }
      }
    };
    jatos2.startLastComponent = function(resultData, messageOrOnError, onError) {
      const lastActiveComponent = jatos2.componentList.reverse().find((c) => c.active);
      jatos2.startComponent(lastActiveComponent.uuid, resultData, messageOrOnError, onError);
    };
    jatos2.abortStudyWithoutRedirect = function(message, onSuccess, onError) {
      if (!isInitialized()) {
        const errorMsg = "jatos.js not yet initialized.";
        callMany(errorMsg, onError, console.error);
        return rejectedPromise(errorMsg);
      }
      if (studyRunState.invalid) {
        const errorMsg = "Can't abort study. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      if (studyRunState.ending) {
        const errorMsg = "Can end/abort study only once.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      studyRunState.ending = true;
      let url = getURL("../abort");
      if (typeof message != "undefined") {
        url = url + "?message=" + message;
      }
      const request = {
        url,
        method: "GET",
        timeout: jatos2.httpTimeout,
        retry: jatos2.httpRetry,
        retryWait: jatos2.httpRetryWait
      };
      jatos2.showBeforeUnloadWarning(false);
      const deferred = httpLoop.send(request, onSuccess, onError);
      setTimeout(function() {
        if (httpLoop.isBusy() && isDeferredPending(deferred)) {
          jatos2.showOverlay(jatos2.waitSendDataOverlayConfig);
        }
      }, 1e3);
      deferred.done(function() {
        removeBeforeUnloadWarning();
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
      if (studyRunState.invalid) {
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
      if (studyRunState.ending) {
        console.warn("Can end/abort study only once");
        return;
      }
      studyRunState.ending = true;
      function abort() {
        removeBeforeUnloadWarning();
        const url = getURL("../abort");
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
    jatos2.endStudyWithoutRedirect = function(resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError) {
      if (!isInitialized()) {
        const errorMsg = "jatos.js not yet initialized.";
        console.error(errorMsg);
        return rejectedPromise(errorMsg);
      }
      let resultData, successful, message, onSuccess;
      ({ resultData, successful, message, onSuccess, onError } = normalizeEndStudyArguments(
        resultDataOrSuccessful,
        successfulOrMessage,
        messageOrOnSuccess,
        onSuccessOrOnError,
        onError
      ));
      if (studyRunState.invalid) {
        const errorMsg = "Can't end study. This study run is invalid.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      if (studyRunState.ending) {
        const errorMsg = "Can end/abort study only once.";
        callMany(errorMsg, onError, console.warn);
        return rejectedPromise(errorMsg);
      }
      studyRunState.ending = true;
      if (resultData) jatos2.appendResultData(resultData);
      let url = getURL("../end");
      if (typeof successful == "boolean" && typeof message == "string") {
        url = url + "?" + encodeQuery({
          "successful": successful,
          "message": message
        });
      } else if (typeof successful == "boolean" && typeof message != "string") {
        url = url + "?" + encodeQuery({
          "successful": successful
        });
      } else if (typeof successful != "boolean" && typeof message == "string") {
        url = url + "?" + encodeQuery({
          "message": message
        });
      }
      const request = {
        url,
        method: "GET",
        timeout: jatos2.httpTimeout,
        retry: jatos2.httpRetry,
        retryWait: jatos2.httpRetryWait
      };
      jatos2.showBeforeUnloadWarning(false);
      const deferred = httpLoop.send(request, onSuccess, onError);
      setTimeout(function() {
        if (httpLoop.isBusy() && isDeferredPending(deferred)) {
          jatos2.showOverlay(jatos2.waitSendDataOverlayConfig);
        }
      }, 1e3);
      deferred.done(function() {
        removeBeforeUnloadWarning();
        stopStudyRun();
      });
      deferred.always(jatos2.removeOverlays);
      return deferred.promise();
    };
    jatos2.endStudyAjax = function(resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError) {
      return jatos2.endStudyWithoutRedirect(resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError);
    };
    jatos2.endStudyAndRedirect = function(url, resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError) {
      jatos2.endStudyWithoutRedirect(resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError).done(function() {
        window.location.href = url;
      });
    };
    jatos2.endStudy = function(resultDataOrSuccessful, successfulOrMessage, messageOrShowEndPage, showEndPage) {
      if (!isInitialized()) {
        console.error("jatos.js not yet initialized");
        return;
      }
      if (studyRunState.invalid) {
        console.warn("Can't end study. This study run is invalid.");
        return;
      }
      let resultData, successful, message;
      ({ resultData, successful, message, showEndPage } = normalizeEndStudyPageArguments(
        resultDataOrSuccessful,
        successfulOrMessage,
        messageOrShowEndPage,
        showEndPage
      ));
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
      if (studyRunState.ending) {
        console.warn("Can end/abort study only once");
        return;
      }
      studyRunState.ending = true;
      if (resultData) jatos2.appendResultData(resultData);
      function end() {
        removeBeforeUnloadWarning();
        let url = getURL("../end");
        if (typeof successful == "boolean" && typeof message == "string") {
          url = url + "?" + encodeQuery({
            "successful": successful,
            "message": message
          });
        } else if (typeof successful == "boolean" && typeof message != "string") {
          url = url + "?" + encodeQuery({
            "successful": successful
          });
        } else if (typeof successful != "boolean" && typeof message == "string") {
          url = url + "?" + encodeQuery({
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
  function normalizeStartComponentArguments(messageOrOnError, onError) {
    if (typeof messageOrOnError === "string") return { message: messageOrOnError, onError };
    if (typeof messageOrOnError === "function") return { onError: messageOrOnError };
    return {};
  }
  function parseLookupErrorCallback(messageOrOnError, onError) {
    if (typeof messageOrOnError === "function") return messageOrOnError;
    if (typeof onError === "function") return onError;
  }
  function hasResultDataArgument(value) {
    return typeof value === "string" || typeof value === "object";
  }
  function normalizeEndStudyArguments(resultDataOrSuccessful, successfulOrMessage, messageOrOnSuccess, onSuccessOrOnError, onError) {
    if (hasResultDataArgument(resultDataOrSuccessful)) {
      return {
        resultData: resultDataOrSuccessful,
        successful: successfulOrMessage,
        message: messageOrOnSuccess,
        onSuccess: onSuccessOrOnError,
        onError
      };
    }
    if (typeof resultDataOrSuccessful === "boolean") {
      return {
        successful: resultDataOrSuccessful,
        message: successfulOrMessage,
        onSuccess: messageOrOnSuccess,
        onError: onSuccessOrOnError
      };
    }
    return {};
  }
  function normalizeEndStudyPageArguments(resultDataOrSuccessful, successfulOrMessage, messageOrShowEndPage, showEndPage) {
    if (hasResultDataArgument(resultDataOrSuccessful)) {
      return {
        resultData: resultDataOrSuccessful,
        successful: successfulOrMessage,
        message: messageOrShowEndPage,
        showEndPage
      };
    }
    if (typeof resultDataOrSuccessful === "boolean") {
      return {
        successful: resultDataOrSuccessful,
        message: successfulOrMessage,
        showEndPage: messageOrShowEndPage
      };
    }
    return {};
  }

  // src/index.js
  /*!
   * jatos.js (JATOS JavaScript Library)
   * http://www.jatos.org
   * Licensed under Apache License 2.0
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
    const studyRunState = createStudyRunState();
    const browserUi = createBrowserUi(jatos);
    const httpLoop = createHttpLoop({
      isInitialized: () => initialization.isInitialized()
    });
    const channels = createChannels(jatos, {
      requestHttp,
      studyRunState,
      getURL,
      showIdOverlay: browserUi.showIdOverlay
    });
    const initialization = createInitialization(jatos, {
      requestHttp,
      getURL,
      showIdOverlay: browserUi.showIdOverlay,
      httpLoop,
      channels
    });
    jatos.onLoad(browserUi.onLoad);
    initialization.start();
    installResultDataApi(jatos, {
      studyRunState,
      getURL,
      isInitialized: () => initialization.isInitialized(),
      isInvalidComponentPosition: (pos) => isInvalidComponentPosition(jatos.componentList, pos),
      sendToHttpLoop: httpLoop.send
    });
    installStudyRunApi(jatos, {
      studyRunState,
      removeBeforeUnloadWarning: browserUi.removeBeforeUnloadWarning,
      getURL,
      httpLoop,
      isInitialized: () => initialization.isInitialized(),
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
    installLoggingApi(jatos, {
      getURL,
      isInitialized: () => initialization.isInitialized(),
      sendToHttpLoop: httpLoop.send
    });
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
  })();
})();
