/** Installs server logging and optional browser error forwarding. */
export function installLoggingApi(jatos, {getURL, isInitialized, sendToHttpLoop}) {
    /**
     * DEPRECATED - Instead use the specific function's error callbacks or Promise functions
     *
     * Defines callback function to be called if jatos.js produces an error.
     */
    jatos.onError = function (onError) {
        console.warn("jatos.onError is abolished - use the specific function's error callback or Promise function");
    };

    /**
     * DEPRECATED - Use jatos.log instead
     *
     * Logs a message within the JATOS log on the server side.
     */
    jatos.logError = function (logErrorMsg) {
        console.warn("jatos.logError is abolished - use jatos.log instead");
    };

    /**
     * Logs a message within the JATOS log on the server side.
     */
    jatos.log = function (logMsg) {
        if (!isInitialized()) return;

        var request = {
            url: getURL("log"),
            method: "POST",
            data: logMsg,
            contentType: "text/plain; charset=UTF-8",
            timeout: jatos.httpTimeout,
            retry: jatos.httpRetry,
            retryWait: jatos.httpRetryWait
        };
        sendToHttpLoop(request);
    };

    /**
     * Convenience function that sends all 'error' and 'unhandledrejection'
     * events and console.error and console.warn calls to JATOS server log
     */
    jatos.catchAndLogErrors = function () {
        window.addEventListener('error', function (e) {
            jatos.log(`Via 'error' event in ${e.filename}:${e.lineno} - ${e.message}`);
        });
        window.addEventListener('unhandledrejection', function (e) {
            jatos.log(`Via 'unhandledrejection' event in ${e.filename}:${e.lineno} - ${e.message}`);
        });

        var errorLog = console.error;
        var warnLog = console.warn;
        console.error = function (message) {
            jatos.log("Via console.error - " + message);
            errorLog.apply(this, arguments);
        };
        console.warn = function (message) {
            jatos.log("Via console.warn - " + message);
            warnLog.apply(this, arguments);
        };
    };
}
