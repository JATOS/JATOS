export function installStudySessionApi(jatos, dependencies) {
    const {getURL, sendToHttpLoop} = dependencies;

    /**
     * If you want to just write into the study session, this function is
     * not what you need. If you want to write something into the study
     * session, just write into the 'jatos.studySessionData' object.
     *
     * This function sets the study session data and sends it to the
     * JATOS server for safe storage. This is done automatically whenever
     * a component finishes. But sometimes it is necessary to trigger this
     * manually, e.g. in a very long-running component one might want to
     * store the session intermediately.
     *
     * @param {object} studySessionData - Object to be submitted
     * @param {Function} [onSuccess] - Called after this function is finished
     * @param {Function} [onFail] - Called if the request fails
     * @return {JatosPromise}
     */
    jatos.setStudySessionData = function (studySessionData, onSuccess, onFail) {
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
        return sendToHttpLoop(request, onSuccess, onFail).promise();
    };
}
