/**
 * Defines callback function to be called if jatos.js produces an error.
 * @deprecated Use each function's error callback or returned JatosPromise instead.
 */
export function onError() {
    console.warn("jatos.onError is abolished - use the specific function's error callback or Promise function");
}

/**
 * Logs a message within the JATOS log on the server side.
 * @deprecated Use `jatos.log` instead.
 */
export function logError() {
    console.warn("jatos.logError is abolished - use jatos.log instead");
}

/**
 * Logs a message within the JATOS log on the server side.
 */
export function log(jatos, {getURL, isInitialized, sendToHttpLoop}, logMsg) {
    if (!isInitialized()) return;

    const request = {
        url: getURL("log"),
        method: "POST",
        data: logMsg,
        contentType: "text/plain; charset=UTF-8",
        timeout: jatos.httpTimeout,
        retry: jatos.httpRetry,
        retryWait: jatos.httpRetryWait
    };
    sendToHttpLoop(request);
}

/**
 * Convenience function that sends all 'error' and 'unhandledrejection'
 * events and console.error and console.warn calls to JATOS server log
 */
export function catchAndLogErrors(jatos) {
    window.addEventListener('error', function (e) {
        jatos.log(`Via 'error' event in ${e.filename}:${e.lineno} - ${e.message}`);
    });
    window.addEventListener('unhandledrejection', function (e) {
        jatos.log(`Via 'unhandledrejection' event in ${e.filename}:${e.lineno} - ${e.message}`);
    });

    const errorLog = console.error;
    const warnLog = console.warn;
    console.error = function (message) {
        jatos.log("Via console.error - " + message);
        errorLog.apply(this, arguments);
    };
    console.warn = function (message) {
        jatos.log("Via console.warn - " + message);
        warnLog.apply(this, arguments);
    };
}
