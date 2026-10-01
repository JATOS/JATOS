/* global jQuery */

/** Owns startup, readiness callbacks, and the study-run heartbeat worker. */
export function createInitialization(jatos, dependencies) {
    const {requestHttp, getURL, getAjaxErrorMsg, showIdOverlay, httpLoop, channels} = dependencies;

    let initialized = false;
    let jatosOnLoadEventFired = false;
    const jatosOnLoadEvent = new Event("jatosOnLoad");
    // The study-run heartbeat is separate from batch/group channel heartbeats.
    let heartbeatWorker;
    /** @deprecated Compatibility access for existing studies; load your own jQuery for new code. */
    jatos.jQuery = {};

    function start() {
        // Load jatos.js's jQuery and put it in jatos.jQuery to avoid conflicts with
        // a component's jQuery version. Afterward call initJatos.
        getScript('jatos-publix/javascripts/jquery-3.7.1.min.js', function () {
            jatos.jQuery = jQuery.noConflict(true);
            jatos.jQuery.ajaxSetup({
                cache: true
            });
            initJatos();
        });
    }

    /**
     * Adds a <script> element into HTML's head and call success function when loaded
     */
    function getScript(url, onSuccess) {
        const script = document.createElement('script');
        script.src = url;
        const head = document.getElementsByTagName('head')[0];
        let done = false;
        script.onload = script.onreadystatechange = function () {
            if (!done && (!this.readyState || this.readyState === 'loaded' ||
                this.readyState === 'complete')) {
                done = true;
                onSuccess();
                script.onload = script.onreadystatechange = null;
                head.removeChild(script);
            }
        };
        head.appendChild(script);
    }

    /**
     * Initialising jatos.js
     */
    function initJatos() {

        // "There is a natural order to this world, and those who try to upend it do not fare well."
        // 1) Load additional scripts
        // 2) Do more init stuff that doesn't involve HTTP requests
        // 3) Get init data from JATOS server
        // 4) Try to open the batch channel
        // 5) Call readyForOnLoad
        jatos.jQuery.when(
            // Load JSON Patch library https://github.com/Starcounter-Jack/JSON-Patch
            jatos.jQuery.getScript("jatos-publix/javascripts/fast-json-patch.min.js")
        )
            .then(function () {
                // Get studyResultUuid from URL path
                jatos.studyResultUuid = window.location.pathname.split("/").reverse()[2];
                readIdCookie();
                // Start heartbeat.js (the general one - not the channel one)
                heartbeatWorker = new Worker("jatos-publix/javascripts/heartbeat.js");
                heartbeatWorker.postMessage([jatos.studyResultUuid]);
                // Start httpLoop.js
                httpLoop.start();
            })
            .then(getInitData)
            .then(showIdOverlay)
            .then(channels.openBatchChannelWithRetry)
            .always(function () {
                initialized = true;
                readyForOnLoad();
            });
    }

    /**
     * Reads JATOS ID cookies, finds the right one with the correct studyResultUuid
     * and stores all key-value pairs into the global jatos object.
     */
    function readIdCookie() {
        const idCookieName = "JATOS_ID";
        const cookieRow = document.cookie
            .split('; ')
            .filter(row => row.includes(idCookieName))
            .find(row => row.includes(jatos.studyResultUuid));
        if (!cookieRow) {
            console.error('readIdCookie: JATOS ID cookie for current studyResultUuid not found.');
            return;
        }

        const equalsIndex = cookieRow.indexOf('=');
        const idCookieValue = cookieRow.substring(equalsIndex + 1);
        if (!idCookieValue) {
            console.error(`readIdCookie: JATOS ID cookie value is empty for cookie "${cookieRow}".`);
            return;
        }

        const cookieParams = new URLSearchParams(idCookieValue);
        cookieParams.forEach((value, key) => jatos[key] = value);
        jatos.componentPos = parseInt(jatos.componentPos, 10);
    }

    /**
     * Gets the study's session data, the study's properties, and the
     * component's properties from the JATOS server and stores them in
     * jatos.studySessionData, jatos.studyProperties, and
     * jatos.componentProperties. Additionally it sets jatos.batchInput,
     * jatos.studyInput, and jatos.componentInput.
     */
    function getInitData() {
        return requestHttp({
            url: getURL("initData"),
            retry: {times: jatos.httpRetry, timeout: jatos.httpRetryWait},
            method: "GET",
            dataType: 'json',
            timeout: jatos.httpTimeout,
            success: setInitData,
            error: (err) => console.error(getAjaxErrorMsg(err))
        });
    }

    /**
     * Puts the init data into jatos variables
     */
    function setInitData(initData) {
        // Batch properties
        jatos.batchProperties = initData.batchProperties;
        if (typeof jatos.batchProperties.batchInput != 'undefined' &&
            jatos.studyProperties.studyInput !== null) {
            jatos.batchJsonInput = jatos.jQuery
                .parseJSON(jatos.batchProperties.batchInput);
        } else {
            jatos.batchJsonInput = {};
        }
        jatos.batchInput = jatos.batchJsonInput;
        delete jatos.batchProperties.batchInput;

        // Study session data
        try {
            jatos.studySessionData = JSON.parse(initData.studySessionData);
        } catch (e) {
            console.error(e.stack || e);
        }

        // Study properties
        jatos.studyProperties = initData.studyProperties;
        if (typeof jatos.studyProperties.studyInput != 'undefined' &&
            jatos.studyProperties.studyInput !== null) {
            jatos.studyJsonInput = jatos.jQuery
                .parseJSON(jatos.studyProperties.studyInput);
        } else {
            jatos.studyJsonInput = {};
        }
        jatos.studyInput = jatos.studyJsonInput;
        delete jatos.studyProperties.studyInput;

        // Study's component list and study length
        jatos.componentList = initData.componentList;
        jatos.studyLength = initData.componentList.length;

        // Component properties
        jatos.componentProperties = initData.componentProperties;
        if (typeof jatos.componentProperties.componentInput != 'undefined' &&
            jatos.componentProperties.componentInput !== null) {
            jatos.componentJsonInput = jatos.jQuery
                .parseJSON(jatos.componentProperties.componentInput);
        } else {
            jatos.componentJsonInput = {};
        }
        jatos.componentInput = jatos.componentJsonInput;
        delete jatos.componentProperties.componentInput;

        // Query string parameters of the URL that started the study
        jatos.urlQueryParameters = initData.urlQueryParameters;
        jatos.frameId = jatos.urlQueryParameters.frameId || undefined;
        jatos.studyCode = initData.studyCode;
    }

    /**
     * Defines a listener (a callback function) that will be called
     * when jatos.js finished its initialisation (e.g. channels are
     * open and init data loaded). It's possible to define several
     * listeners (called in the order as defined). If the
     * jatosOnLoad event was already fired, the callback is called
     * right away.
     * @param {function} callback - callback function
     */
    jatos.onLoad = function (callback) {
        if (!jatosOnLoadEventFired) {
            window.addEventListener("jatosOnLoad", callback);
            readyForOnLoad();
        } else {
            callback();
        }
    };

    /**
     * Just for convenience. People are used to 'onload' all lower case
     */
    jatos.onload = jatos.onLoad;

    /**
     * Calls onLoadCallback if it already exists and jatos.js is initialised.
     * We can't use Deferred since jQuery might not be defined yet.
     */
    function readyForOnLoad() {
        if (!jatosOnLoadEventFired && initialized) {
            jatosOnLoadEventFired = true;
            window.dispatchEvent(jatosOnLoadEvent);
        }
    }

    /**
     * A web worker used in jatos.js to send periodic requests back to the
     * JATOS server. With this function one can set the period with which the
     * heartbeat is send.
     *
     * @param {number} heartbeatPeriod - in milliseconds (Integer)
     */
    jatos.setHeartbeatPeriod = function (heartbeatPeriod) {
        if (typeof heartbeatPeriod == 'number' && heartbeatWorker) {
            heartbeatWorker.postMessage([jatos.studyResultUuid, heartbeatPeriod]);
        }
    };

    return {
        start,
        isInitialized: () => initialized,
        terminateHeartbeat: () => heartbeatWorker.terminate()
    };
}
