import {callMany} from "./utils/callbacks.js";

export function installComponentNavigationApi(jatos, dependencies) {
    const {
        beforeUnloadWarning,
        getURL,
        httpLoop,
        isEndingStudy,
        isInitialized,
        isStartingComponent,
        isStudyRunInvalid,
        setStartingComponent
    } = dependencies;

    /**
     * Starts the component with the given ID. Before it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * Either without message:
     * @param {string|number} componentIdOrUuid - ID or UUID of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {Function} [onError] - Called if starting fails
     *
     * Or with message:
     * @param {string|number} componentIdOrUuid - ID or UUID of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {string} [message] - Message that should be logged (max 255 chars)
     * @param {Function} [onError] - Called if starting fails
     */
    jatos.startComponent = function (componentIdOrUuid, resultData, param3, param4) {
        if (!isInitialized()) {
            console.error("jatos.js not yet initialized");
            return;
        }

        let message, onError, componentUuid;
        if (typeof componentIdOrUuid === 'number') {
            componentUuid = jatos.componentList.find(c => c.id === componentIdOrUuid).uuid;
        } else {
            componentUuid = componentIdOrUuid;
        }
        if (typeof param3 === 'string') {
            message = param3;
            onError = param4;
        } else if (typeof param3 === 'function') {
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
        // If this is a single component run initiated by the JATOS GUI, end the run.
        const isSingleComponentRun = jatos.jatosRun === "RUN_COMPONENT_FINISHED";
        if (isSingleComponentRun) {
            if (resultData) {
                jatos.endStudy(resultData, true, message);
            } else {
                jatos.endStudy(true, message);
            }
            return;
        }

        setStartingComponent(true);

        // Send result data and study session data before starting next component
        if (resultData) jatos.appendResultData(resultData);
        jatos.setStudySessionData(jatos.studySessionData);

        const start = function () {
            window.removeEventListener('beforeunload', beforeUnloadWarning, { capture: true });

            let url = getURL("../" + componentUuid + "/start");
            if (message) url = url + "?" + jatos.jQuery.param({ "message": message });
            window.location.href = url;
        };

        // Wait for http-loop-worker.js to finish
        if (httpLoop.isBusy()) {
            setTimeout(jatos.showOverlay, 1000, jatos.waitSendDataOverlayConfig);
        }
        httpLoop.whenIdle(start);
    };

    /**
     * Starts the component with the given position (position of the first
     * component of a study is 1). Before this it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * Either without message:
     * @param {number} componentPos - Position of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {Function} [onError] - Called if starting fails
     *
     * Or with message:
     * @param {number} componentPos - Position of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {string} [message] - Message that should be logged (max 255 chars)
     * @param {Function} [onError] - Called if starting fails
     */
    jatos.startComponentByPos = function (componentPos, resultData, param3, param4) {
        if (isInvalidComponentPosition(jatos.componentList, componentPos)) {
            let onError;
            if (typeof param3 === 'function') onError = param3;
            else if (typeof param4 === 'function') onError = param4;
            callMany("Component position does not exist", onError, console.error);
            return;
        }
        const componentUuid = jatos.componentList[componentPos - 1].uuid;
        jatos.startComponent(componentUuid, resultData, param3, param4);
    };

    /**
     * Starts the component with the given title. If there are multiple components with an
     * identical title it starts the one with the lowest position. Before this it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * Either without message:
     * @param {string} title - Title of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {Function} [onError] - Called if starting fails
     *
     * Or with message:
     * @param {string} title - Title of the component to start
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {string} [message] - Message that should be logged (max 255 chars)
     * @param {Function} [onError] - Called if starting fails
     */
    jatos.startComponentByTitle = function (title, resultData, param3, param4) {
        const component = jatos.componentList.find(component => component.title === title);
        if (!component) {
            let onError;
            if (typeof param3 === 'function') onError = param3;
            else if (typeof param4 === 'function') onError = param4;
            callMany(`Component with title ${title} does not exist`, onError, console.error);
            return;
        }
        const componentUuid = component.uuid;
        jatos.startComponent(componentUuid, resultData, param3, param4);
    };

    /**
     * Starts the next active component of this study. The component's order is
     * determined by their position. If the current component is already the
     * last one it finishes the study. Before this it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * Either without message:
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {Function} [onError] - Called if starting fails
     *
     * Or with message:
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {string} [message] - Message that should be logged (max 255 chars)
     * @param {Function} [onError] - Called if starting fails
     */
    jatos.startNextComponent = function (resultData, param2, param3) {
        let message;
        if (typeof param2 === 'string') {
            message = param2;
        }

        // If this is the last component end study
        const lastActiveComponent = jatos.componentList.slice().reverse()
            .find(function (component) { return component.active; });
        if (jatos.componentPos >= lastActiveComponent.position) {
            if (resultData) {
                jatos.endStudy(resultData, true, message);
            } else {
                jatos.endStudy(true, message);
            }
            return;
        }
        // Start next active component
        for (let i = jatos.componentPos; i < jatos.componentList.length; i++) {
            if (jatos.componentList[i].active) {
                const nextComponentUuid = jatos.componentList[i].uuid;
                jatos.startComponent(nextComponentUuid, resultData, param2, param3);
                break;
            }
        }
    };

    /**
     * Starts the last component of this study or if it's inactive the component
     * with the highest position that is active. Before this it calls
     * jatos.appendResultData (sends result data to the JATOS server and
     * appends them to the already existing ones for this component) and
     * jatos.setStudySessionData (syncs study session data with the JATOS server).
     *
     * Either without message:
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {Function} [onError] - Called if starting fails
     *
     * Or with message:
     * @param {(Object|string)} [resultData] - Result data to be sent back to JATOS
     * @param {string} [message] - Message that should be logged (max 255 chars)
     * @param {Function} [onError] - Called if starting fails
     */
    jatos.startLastComponent = function (resultData, param2, param3) {
        const lastActiveComponent = jatos.componentList.reverse().find(c => c.active);
        jatos.startComponent(lastActiveComponent.uuid, resultData, param2, param3);
    };
}

export function isInvalidComponentPosition(componentList, pos) {
    return pos <= 0 || pos > componentList.length;
}
