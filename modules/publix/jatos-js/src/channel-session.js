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

import {applyPatch, getValueByPointer} from "fast-json-patch/module/core.mjs";

import {cloneJsonObj} from "./utils/clone-json.js";

/** @typedef {import("./jatos-promise.js").JatosPromise} JatosPromise */

/** Creates a session API over live channel data and its channel-specific sender. */
export function createSessionApi(getData, sendPatch) {
    const session = {};

    /**
     * Getter for a field in the session data. Takes a name
     * and returns the matching value, or undefined if the name does not
     * correspond to an existing field. Works only on the first
     * level of the object tree. For all other levels use
     * session.find. Gets the object from the
     * locally stored copy of the session and does not call
     * the server.
     * @param {string} name - name of the field
     * @return {object}
     */
    session.get = function (name) {
        const obj = getValueByPointer(getData(), "/" + name);
        return cloneJsonObj(obj);
    };

    /**
     * Returns the complete session data (might be bad performance-wise)
     * Gets the object from the locally stored copy of the session
     * and does not call the server.
     * @return {object}
     */
    session.getAll = function () {
        const obj = session.find("");
        return cloneJsonObj(obj);
    };

    /**
     * Getter for a field in the session data. Takes a
     * JSON Pointer and returns the matching value, or undefined if
     * the pointer does not correspond to an existing field. Gets the
     * object from the locally stored copy of the session
     * and does not call the server.
     * @param {string} path - JSON pointer path
     * @return {object}
     */
    session.find = function (path) {
        const obj = getValueByPointer(getData(), path);
        return cloneJsonObj(obj);
    };

    /**
     * This function defines the JSON Patch test operation but it
     * does not use the 'test' operation of the JSON patch
     * implementation, but uses the JSON pointer implementation
     * instead.
     * @param {string} path - JSON pointer path to be tested
     * @param {object} value - value to be tested
     * @return {boolean}
     */
    session.test = function (path, value) {
        const obj = getValueByPointer(getData(), path);
        return obj === value;
    };

    /**
     * Check if the field under the given path exists.
     * @param {string} path - JSON pointer path
     * @return {boolean}
     */
    session.defined = function (path) {
        return !session.test(path, undefined);
    };

    /**
     * JSON Patch add operation
     * @param {string} path - JSON pointer path
     * @param {object} value - value to be stored
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    session.add = function (path, value, onSuccess, onFail) {
        const patch = generatePatch("add", path, value, null);
        return sendPatch(patch, onSuccess, onFail);
    };

    /**
     * Like JSON Patch add operation, but instead of a path accepts
     * a name of the field to be stored. Works only on the first level
     * of the object tree.
     * @param {string} name - name of the field
     * @param {object} value - value to be stored
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    session.set = function (name, value, onSuccess, onFail) {
        const patch = generatePatch("add", "/" + name, value, null);
        return sendPatch(patch, onSuccess, onFail);
    };

    /**
     * Replaces the whole session data (might be bad performance-wise)
     * @param {object} value - value to be stored in the session
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    session.setAll = function (value, onSuccess, onFail) {
        return session.replace("", value, onSuccess, onFail);
    };

    /**
     * JSON Patch remove operation
     * @param {string} path - JSON pointer path to the field that should be removed
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    session.remove = function (path, onSuccess, onFail) {
        const patch = generatePatch("remove", path, null, null);
        return sendPatch(patch, onSuccess, onFail);
    };

    /**
     * Clears the session data.
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    session.clear = function (onSuccess, onFail) {
        const patch = generatePatch("replace", "", {}, null);
        return sendPatch(patch, onSuccess, onFail);
    };

    /**
     * JSON Patch replace operation
     * @param {string} path - JSON pointer path
     * @param {object} value - value to be replaced with
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    session.replace = function (path, value, onSuccess, onFail) {
        const patch = generatePatch("replace", path, value, null);
        return sendPatch(patch, onSuccess, onFail);
    };

    /**
     * JSON Patch copy operation
     * @param {string} from - JSON pointer path to the origin
     * @param {string} path - JSON pointer path to the target
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    session.copy = function (from, path, onSuccess, onFail) {
        const patch = generatePatch("copy", path, null, from);
        return sendPatch(patch, onSuccess, onFail);
    };

    /**
     * JSON Patch move operation
     * @param {string} from - JSON pointer path to the origin
     * @param {string} path - JSON pointer path to the target
     * @param {Function} [onSuccess] - Called if this patch was successfully applied on the server and the client side
     * @param {Function} [onFail] - Called if this patch failed
     * @return {JatosPromise}
     */
    session.move = function (from, path, onSuccess, onFail) {
        const patch = generatePatch("move", path, null, from);
        return sendPatch(patch, onSuccess, onFail);
    };

    return session;
}

/**
 * Generates an abstract JSON Patch
 */
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

/**
 * Applies incoming patches before an optional full snapshot. A null snapshot
 * clears the session; a root patch retains JSON Patch's replacement document.
 * Version tracking and callbacks remain the channel's responsibility.
 */
export function applySessionUpdate(data, patches, snapshot) {
    if (patches !== undefined) {
        const results = applyPatch(data, patches);
        if (results && results.newDocument !== undefined) {
            data = results.newDocument;
        }
    }
    if (snapshot !== undefined) {
        data = snapshot === null ? {} : snapshot;
    }
    return data;
}
