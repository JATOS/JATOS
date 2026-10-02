/** @typedef {import("./jatos-promise.js").JatosPromise} JatosPromise */

import {createDeferred, rejectedPromise} from "./jatos-promise.js";
import {callMany, callWithArgs} from "./utils/callbacks.js";

export function installResultDataApi(jatos, dependencies) {
    const {
        studyRunState,
        getURL,
        isInitialized,
        isInvalidComponentPosition,
        sendToHttpLoop
    } = dependencies;

    /**
     * Posts result data for the currently running component back to the JATOS
     * server. Already stored result data for this component will be overwritten.
     * It offers callbacks, either as parameter or via Promise,
     * to signal success or failure in the transfer.
     *
     * @param {(Object|string)} resultData - String or object to be submitted
     * @param {Function} [onSuccess] - Called in case of success
     * @param {Function} [onError] - Called in case of error
     * @returns {JatosPromise}
     */
    jatos.submitResultData = function (resultData, onSuccess, onError) {
        return submitOrAppendResultData(resultData, false, onSuccess, onError);
    };

    /**
     * Appends result data for the currently running component back to the JATOS
     * server. Contrary to jatos.submitResultData it does not overwrite the result
     * data. It offers callbacks, either as parameter or via Promise,
     * to signal success or failure in the transfer.
     *
     * @param {(Object|string)} resultData - String or object to be appended
     * @param {Function} [onSuccess] - Called in case of success
     * @param {Function} [onError] - Called in case of error
     * @returns {JatosPromise}
     */
    jatos.appendResultData = function (resultData, onSuccess, onError) {
        return submitOrAppendResultData(resultData, true, onSuccess, onError);
    };

    /**
     * Sends result data using PUT for submitResultData and POST for appendResultData.
     */
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
            timeout: jatos.httpTimeout,
            retry: jatos.httpRetry,
            retryWait: jatos.httpRetryWait
        };
        let deferred = sendToHttpLoop(request, onSuccess, onError)
        deferred.fail(function (err, status) {
            if (status === 413) {
                jatos.showOverlay({ text: "Couldn't send result data: too large!", id: "result413", timeout: 8000, showImg: false });
            }
        });
        return deferred.promise();
    }

    /**
     * Uploads a file that will be saved on the JATOS server.
     *
     * @param {(Blob|string|Object)} obj - Data to upload. A Blob is uploaded as-is,
     * a string becomes a text Blob, and an object is serialized as JSON before it
     * is converted to a Blob.
     * @param {string} filename - Name of the uploaded file
     * @param {Function} [onSuccess] - Called in case of success
     * @param {Function} [onError] - Called in case of error
     * @returns {JatosPromise}
     */
    jatos.uploadResultFile = function (obj, filename, onSuccess, onError) {
        if (studyRunState.invalid) {
            const errorMsg = "Can't upload file. This study run is invalid.";
            callMany(errorMsg, onError, console.warn);
            return rejectedPromise(errorMsg);
        }
        if (typeof filename !== "string" || 0 === filename.length) {
            const errorMsg = "No filename specified."
            callMany(errorMsg, onError, console.error);
            return rejectedPromise(errorMsg);
        }

        let blob;
        if (obj instanceof Blob) {
            blob = obj;
        } else if (typeof obj === "string") {
            blob = new Blob([obj], { type: 'text/plain' });
        } else if (obj === Object(obj)) {
            // Object can be stringified to JSON
            blob = new Blob([JSON.stringify(obj, null, 2)], { type: 'application/json' });
        } else {
            const errorMsg = "Only string, Object or Blob allowed.";
            callMany(errorMsg, onError, console.error);
            return rejectedPromise(errorMsg);
        }

        let request = {
            url: getURL("files/" + encodeURI(filename)),
            blob: blob,
            filename: filename,
            method: 'POST',
            timeout: jatos.httpTimeout,
            retry: jatos.httpRetry,
            retryWait: jatos.httpRetryWait
        };
        let deferred = sendToHttpLoop(request, onSuccess, onError)
        deferred.fail(function (err, status) {
            if (status === 413) {
                jatos.showOverlay({ text: "Couldn't send result file: too large!", id: "result413", timeout: 8000, showImg: false });
            }
        });
        return deferred.promise();
    };

    /**
     * Downloads a file from the JATOS server. Can only download a file that was previously
     * uploaded with jatos.uploadResultFile in the same study run. If the file contains
     *  text, it returns the content as a string. If the file contains JSON, it returns
     * parsed JSON as an object. All other MIME types are returned as a Blob.
     *
     * Call as `(filename, onSuccess?, onError?)`, or include the component position as
     * `(componentPos, filename, onSuccess?, onError?)` when components uploaded files
     * with the same name.
     *
     * @param {string|number} filenameOrComponentPos - Filename, or the component position
     * @param {string|Function} [filenameOrOnSuccess] - Filename with a component position; otherwise success callback
     * @param {Function} [onSuccessOrOnError] - Success callback with a component position; otherwise error callback
     * @param {Function} [onError] - Error callback when a component position is supplied
     * @returns {JatosPromise}
     */
    jatos.downloadResultFile = function (filenameOrComponentPos, filenameOrOnSuccess,
                                         onSuccessOrOnError, onError) {
        if (!isInitialized()) {
            const errorMsg = "jatos.js not yet initialized";
            console.error(errorMsg);
            return rejectedPromise(errorMsg);
        }

        let componentPos, filename, onSuccess;
        if (typeof filenameOrComponentPos === 'number') {
            componentPos = filenameOrComponentPos;
            filename = filenameOrOnSuccess;
            onSuccess = onSuccessOrOnError;
        } else if (typeof filenameOrComponentPos === 'string') {
            filename = filenameOrComponentPos;
            onSuccess = filenameOrOnSuccess;
            onError = onSuccessOrOnError;
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
            if (isInvalidComponentPosition(componentPos)) {
                const errorMsg = "Component position does not exist.";
                callMany(errorMsg, onError, console.error);
                return rejectedPromise(errorMsg);
            }
            const componentId = jatos.componentList[componentPos - 1].id;
            url += "?componentId=" + componentId;
        }

        const deferred = createDeferred();
        // Fetch as a Blob so its MIME type determines whether to return parsed JSON,
        // text, or the Blob itself.
        const xhr = new XMLHttpRequest();
        xhr.open("GET", url, true);
        xhr.responseType = "blob";
        xhr.onload = function () {
            if (this.status === 200) {
                const blob = xhr.response;
                if (blob.type === "application/json") {
                    const jsonReader = new FileReader();
                    jsonReader.addEventListener("loadend", function () {
                        const obj = JSON.parse(jsonReader.result);
                        callWithArgs(onSuccess, obj);
                        deferred.resolve(obj);
                    });
                    jsonReader.readAsText(blob);
                } else if (blob.type === "text/plain") {
                    const textReader = new FileReader();
                    textReader.addEventListener("loadend", function () {
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
        xhr.onerror = function () {
            const error = "Download of " + filename + " returned " + xhr.statusText;
            callMany(error, onError, console.error);
            deferred.reject(error);
        };
        xhr.send(null);

        return deferred.promise();
    };
}
