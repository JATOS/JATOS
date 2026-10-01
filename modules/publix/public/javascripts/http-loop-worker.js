"use strict";
(() => {
  // src/workers/http-loop-worker.js
  /*!
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
   * Author Kristian Lange
   * Licensed under Apache License 2.0
   *
   * Uses "HTML5 `FormData` polyfill for Browsers."
   * https://github.com/jimmywarting/FormData
   * Copyright (c) 2017 Jimmy Wärting
   * Licensed under the MIT license.
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
    if (requests.length == 0) {
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
      if (xhr.status == 200) {
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
      if (xhr.status && xhr.status == 400 || xhr.status && xhr.status == 413 || "retry" in request === false || request.retry <= 0) {
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
  (function() {
    if (typeof Blob !== "undefined" && (typeof FormData === "undefined" || !FormData.prototype.keys)) {
      let normalizeValue = function([name, value, filename]) {
        if (value instanceof Blob) {
          value = new File([value], filename, {
            type: value.type,
            lastModified: value.lastModified
          });
        }
        return [name, value];
      }, ensureArgs = function(args, expected) {
        if (args.length < expected) {
          throw new TypeError(`${expected} argument required, but only ${args.length} present.`);
        }
      }, normalizeArgs = function(name, value, filename) {
        return value instanceof Blob ? [String(name), value, filename !== void 0 ? filename + "" : typeof value.name === "string" ? value.name : "blob"] : [String(name), String(value)];
      }, normalizeLinefeeds = function(value) {
        return value.replace(/\r\n/g, "\n").replace(/\n/g, "\r\n");
      }, each = function(arr, cb) {
        for (let i = 0; i < arr.length; i++) {
          cb(arr[i]);
        }
      };
      const global = typeof window === "object" ? window : typeof self === "object" ? self : this;
      const _FormData = global.FormData;
      const _send = global.XMLHttpRequest && global.XMLHttpRequest.prototype.send;
      const _fetch = global.Request && global.fetch;
      const _sendBeacon = global.navigator && global.navigator.sendBeacon;
      const stringTag = global.Symbol && Symbol.toStringTag;
      if (stringTag) {
        if (!Blob.prototype[stringTag]) {
          Blob.prototype[stringTag] = "Blob";
        }
        if ("File" in global && !File.prototype[stringTag]) {
          File.prototype[stringTag] = "File";
        }
      }
      try {
        new File([], "");
      } catch (a) {
        global.File = function File2(b, d, c) {
          const blob = new Blob(b, c);
          const t = c && void 0 !== c.lastModified ? new Date(c.lastModified) : /* @__PURE__ */ new Date();
          Object.defineProperties(blob, {
            name: {
              value: d
            },
            lastModifiedDate: {
              value: t
            },
            lastModified: {
              value: +t
            },
            toString: {
              value() {
                return "[object File]";
              }
            }
          });
          if (stringTag) {
            Object.defineProperty(blob, stringTag, {
              value: "File"
            });
          }
          return blob;
        };
      }
      class FormDataPolyfill {
        /**
         * FormData class
         *
         * @param {HTMLElement=} form
         */
        constructor(form) {
          this._data = [];
          if (!form) return this;
          const self2 = this;
          each(form.elements, (elm) => {
            if (!elm.name || elm.disabled || elm.type === "submit" || elm.type === "button") return;
            if (elm.type === "file") {
              const files = elm.files && elm.files.length ? elm.files : [new File([], "", { type: "application/octet-stream" })];
              each(files, (file) => {
                self2.append(elm.name, file);
              });
            } else if (elm.type === "select-multiple" || elm.type === "select-one") {
              each(elm.options, (opt) => {
                !opt.disabled && opt.selected && self2.append(elm.name, opt.value);
              });
            } else if (elm.type === "checkbox" || elm.type === "radio") {
              if (elm.checked) self2.append(elm.name, elm.value);
            } else {
              const value = elm.type === "textarea" ? normalizeLinefeeds(elm.value) : elm.value;
              self2.append(elm.name, value);
            }
          });
        }
        /**
         * Append a field
         *
         * @param   {string}           name      field name
         * @param   {string|Blob|File} value     string / blob / file
         * @param   {string=}          filename  filename to use with blob
         * @return  {undefined}
         */
        append() {
          ensureArgs(arguments, 2);
          var [a, s, d] = normalizeArgs.apply(null, arguments);
          this._data.push([a, s, d]);
        }
        /**
         * Delete all fields values given name
         *
         * @param   {string}  name  Field name
         * @return  {undefined}
         */
        delete(name) {
          ensureArgs(arguments, 1);
          const res = [];
          name = String(name);
          each(this._data, (entry) => {
            if (entry[0] !== name) {
              res.push(entry);
            }
          });
          this._data = res;
        }
        /**
         * Iterate over all fields as [name, value]
         *
         * @return {Iterator}
         */
        *entries() {
          for (var i = 0; i < this._data.length; i++) {
            yield normalizeValue(this._data[i]);
          }
        }
        /**
         * Iterate over all fields
         *
         * @param   {Function}  callback  Executed for each item with parameters (value, name, thisArg)
         * @param   {Object=}   thisArg   `this` context for callback function
         * @return  {undefined}
         */
        forEach(callback, thisArg) {
          ensureArgs(arguments, 1);
          for (const [name, value] of this) {
            callback.call(thisArg, value, name, this);
          }
        }
        /**
         * Return first field value given name
         * or null if non existen
         *
         * @param   {string}  name      Field name
         * @return  {string|File|null}  value Fields value
         */
        get(name) {
          ensureArgs(arguments, 1);
          const entries = this._data;
          name = String(name);
          for (let i = 0; i < entries.length; i++) {
            if (entries[i][0] === name) {
              return normalizeValue(this._data[i])[1];
            }
          }
          return null;
        }
        /**
         * Return all fields values given name
         *
         * @param   {string}  name  Fields name
         * @return  {Array}         [{String|File}]
         */
        getAll(name) {
          ensureArgs(arguments, 1);
          const result = [];
          name = String(name);
          for (let i = 0; i < this._data.length; i++) {
            if (this._data[i][0] === name) {
              result.push(normalizeValue(this._data[i])[1]);
            }
          }
          return result;
        }
        /**
         * Check for field name existence
         *
         * @param   {string}   name  Field name
         * @return  {boolean}
         */
        has(name) {
          ensureArgs(arguments, 1);
          name = String(name);
          for (let i = 0; i < this._data.length; i++) {
            if (this._data[i][0] === name) {
              return true;
            }
          }
          return false;
        }
        /**
         * Iterate over all fields name
         *
         * @return {Iterator}
         */
        *keys() {
          for (const [name] of this) {
            yield name;
          }
        }
        /**
         * Overwrite all values given name
         *
         * @param   {string}    name      Filed name
         * @param   {string}    value     Field value
         * @param   {string=}   filename  Filename (optional)
         * @return  {undefined}
         */
        set(name) {
          ensureArgs(arguments, 2);
          name = String(name);
          const result = [];
          let replaced = false;
          for (let i = 0; i < this._data.length; i++) {
            const match = this._data[i][0] === name;
            if (match) {
              if (!replaced) {
                result[i] = normalizeArgs.apply(null, arguments);
                replaced = true;
              }
            } else {
              result.push(this._data[i]);
            }
          }
          if (!replaced) {
            result.push(normalizeArgs.apply(null, arguments));
          }
          this._data = result;
        }
        /**
         * Iterate over all fields
         *
         * @return {Iterator}
         */
        *values() {
          for (const [, value] of this) {
            yield value;
          }
        }
        /**
         * Return a native (perhaps degraded) FormData with only a `append` method
         * Can throw if it's not supported
         *
         * @return {FormData}
         */
        ["_asNative"]() {
          const fd = new _FormData();
          for (const [name, value] of this) {
            fd.append(name, value);
          }
          return fd;
        }
        /**
         * [_blob description]
         *
         * @return {Blob} [description]
         */
        ["_blob"]() {
          const boundary = "----formdata-polyfill-" + Math.random();
          const chunks = [];
          for (const [name, value] of this) {
            chunks.push(`--${boundary}\r
`);
            if (value instanceof Blob) {
              chunks.push(
                `Content-Disposition: form-data; name="${name}"; filename="${value.name}"\r
`,
                `Content-Type: ${value.type || "application/octet-stream"}\r
\r
`,
                value,
                "\r\n"
              );
            } else {
              chunks.push(
                `Content-Disposition: form-data; name="${name}"\r
\r
${value}\r
`
              );
            }
          }
          chunks.push(`--${boundary}--`);
          return new Blob(chunks, {
            type: "multipart/form-data; boundary=" + boundary
          });
        }
        /**
         * The class itself is iterable
         * alias for formdata.entries()
         *
         * @return  {Iterator}
         */
        [Symbol.iterator]() {
          return this.entries();
        }
        /**
         * Create the default string description.
         *
         * @return  {string} [object FormData]
         */
        toString() {
          return "[object FormData]";
        }
      }
      if (stringTag) {
        FormDataPolyfill.prototype[stringTag] = "FormData";
      }
      if (_send) {
        const setRequestHeader = global.XMLHttpRequest.prototype.setRequestHeader;
        global.XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
          setRequestHeader.call(this, name, value);
          if (name.toLowerCase() === "content-type") this._hasContentType = true;
        };
        global.XMLHttpRequest.prototype.send = function(data) {
          if (data instanceof FormDataPolyfill) {
            const blob = data["_blob"]();
            if (!this._hasContentType) this.setRequestHeader("Content-Type", blob.type);
            _send.call(this, blob);
          } else {
            _send.call(this, data);
          }
        };
      }
      if (_fetch) {
        const _fetch2 = global.fetch;
        global.fetch = function(input, init) {
          if (init && init.body && init.body instanceof FormDataPolyfill) {
            init.body = init.body["_blob"]();
          }
          return _fetch2.call(this, input, init);
        };
      }
      if (_sendBeacon) {
        global.navigator.sendBeacon = function(url, data) {
          if (data instanceof FormDataPolyfill) {
            data = data["_asNative"]();
          }
          return _sendBeacon.call(this, url, data);
        };
      }
      global["FormData"] = FormDataPolyfill;
    }
  })();
})();
