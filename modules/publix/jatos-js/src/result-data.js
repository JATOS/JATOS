import {callMany, callWithArgs} from "./utils/callbacks.js";

export function installResultDataApi(jatos, dependencies) {
    const {
        createDeferred,
        getURL,
        isInitialized,
        isInvalidComponentPosition,
        isStudyRunInvalid,
        rejectedPromise,
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
     * @return {JatosPromise}
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
     * @return {JatosPromise}
     */
    jatos.appendResultData = function (resultData, onSuccess, onError) {
        return submitOrAppendResultData(resultData, true, onSuccess, onError);
    };

    /**
     * Does the sending of the result data. Uses PUT for submitResultData and
     * POST for appendResultData.
     */
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
     * @param {(Blob|string|Object)} obj - Data to be uploaded as a file. A Blob
     * 										will be uploaded right away. A string
     * 										is turned into a Blob. An object is
     * 										first turned into a JSON string	andl
     * 										then into a Blob.
     * @param {string} filename - Name of the uploaded file
     * @param {Function} [onSuccess] - Called in case of success
     * @param {Function} [onError] - Called in case of error
     * @return {JatosPromise}
     */
    jatos.uploadResultFile = function (obj, filename, onSuccess, onError) {
        if (isStudyRunInvalid()) {
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
     * text it returns the content as a string. If the file contains JSON, it returns
     * the JSON already parsed as an object. All other mime types are returned as a Blob.
     *
     * @param {string} filename - Name of the uploaded file
     * @param {Function} [onSuccess] - Called in case of success
     * @param {Function} [onError] - Called in case of error
     * @return {JatosPromise}
     *
     * Additionally one can specify the component ID (in case different components uploaded
     * files with the same filename):
     * @param {number} componentPos - Position of the component to look for the file
     * @param {string} filename - Name of the uploaded file
     * @param {Function} [onSuccess] - Called in case of success
     * @param {Function} [onError] - Called in case of error
     * @return {JatosPromise}
     */
    jatos.downloadResultFile = function (param1, param2, param3, param4) {
        if (!isInitialized()) {
            const errorMsg = "jatos.js not yet initialized";
            console.error(errorMsg);
            return rejectedPromise(errorMsg);
        }

        let componentPos, filename, onSuccess, onError;
        if (typeof param1 === 'number') {
            componentPos = param1;
            filename = param2;
            onSuccess = param3;
            onError = param4;
        } else if (typeof param1 === 'string') {
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
            if (isInvalidComponentPosition(componentPos)) {
                const errorMsg = "Component position does not exist.";
                callMany(errorMsg, onError, console.error);
                return rejectedPromise(errorMsg);
            }
            const componentId = jatos.componentList[componentPos - 1].id;
            url += "?componentId=" + componentId;
        }

        const deferred = createDeferred();
        // Use XMLHttpRequest instead of jQuery because jQuery cannot handle JSON within a Blob
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
