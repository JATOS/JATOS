/** Installs browser UI helpers and owns the unload-warning listener. */
export function createBrowserUi(jatos) {
    /**
     * Config of the overlay that is shown when the component ended but
     * the httpLoop still has requests to send. See function jatos.showOverlay
     * for config options.
     */
    jatos.waitSendDataOverlayConfig = {
        text: "Sending data. Please wait."
    };
    /**
     * Flag that determines if the 'beforeunload' warning should be shown
     * by the browser to the worker if they attempt to reload or close the
     * browser (tab)
     */
    let showBeforeUnloadWarning = true;

    /**
     * Warn worker with a popup that the component is not reloadable and leaving the page would end the study
     * Remember: This works only if at least one user action happend in the window (e.g. mouse click)
     * Check: https://developer.mozilla.org/en-US/docs/Web/API/Window/beforeunload_event
     */
    function onLoad() {
        if (showBeforeUnloadWarning && !jatos.componentProperties.reloadable) {
            window.addEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        }
    }

    function beforeUnloadWarning(event) {
        event.preventDefault();
        // Most browsers do not show this message but a standardized one
        event.returnValue = "Are you sure you want to leave?";
    }

    function removeBeforeUnloadWarning() {
        window.removeEventListener("beforeunload", beforeUnloadWarning, { capture: true });
    }

    /**
     * Adds or cancels warning popup that will be shown by the browser to the worker who
     * attempts to reload the page or close the browser (tab).
     *
     * @param {boolean} show - If true the warning will be shown - if false a
     * 		previously added warning will be canceled
     */
    jatos.showBeforeUnloadWarning = function (show) {
        showBeforeUnloadWarning = show;
        if (show) {
            window.addEventListener("beforeunload", beforeUnloadWarning, { capture: true });
        } else {
            removeBeforeUnloadWarning();
        }
    };

    /**
     * Adds an overlay to the document that shows a text and an image underneath
     * in the center of the screen. By default the text is 'Please wait.' and the
     * image is a spinning wheel. If an element with the provided ID already exists
     * just the text content will be updated.
     *
     * @param {Object} [config] - Config object
     * @param {boolean} [config.show=true] - If true the overlay is shown
     * @param {boolean} [config.keep=false] - Keep the overlay when `jatos.removeOverlays` is called
     * @param {string} [config.text="Please wait"] - Text to be shown
     * @param {string} [config.imgUrl] - URL of the image (default is a spinning wheel)
     * @param {boolean} [config.showImg=true] - If true the image is shown
     * @param {string} [config.style] - Additional CSS styles
     * @param {string} [config.id] - Element ID
     * @param {string} [config.className] - Additional class name
     * @param {number} [config.timeout] - If set the overlay will be removed after the given milliseconds
     * @return {HTMLElement} The created element (or updated existing one)
     */
    jatos.showOverlay = function (config) {
        if (config && typeof config.show == "boolean" && !config.show) return;

        // If an element with the given ID already exists just update the text and return
        if (config && typeof config.id == "string") {
            const el = document.getElementById(config.id);
            if (el) {
                if (config && config.text) el.textContent = config.text;
                return el;
            }
        }

        // Create div
        const div = document.createElement('div');

        // Add style
        let divStyle = 'color: black;' +
            'font-family: Sans-Serif;' +
            'font-size: 30px;' +
            'letter-spacing: 2px;' +
            'opacity: 0.6;' +
            'text-shadow: -1px 0 white, 0 1px white, 1px 0 white, 0 -1px white;' +
            'z-index: 9999;' +
            'position: absolute;' +
            'left: 50%;' +
            'top: 50%;' +
            'transform: translate(-50%, -50%);' +
            'display: flex;' +
            'align-items: center;' +
            'justify-content: center;' +
            'flex-direction: column;';
        if (config && typeof config.style == "string") divStyle += ";" + config.style;
        div.style.cssText = divStyle;

        // Add ID and classes
        if (config && typeof config.id == "string") div.id = config.id;
        div.classList.add("jatosOverlay");
        if (config && typeof config.className == "string") div.classList.add(config.className);

        // Add Text
        div.textContent = config ? config.text : "Please wait";

        // Add image
        const showImg = (config && typeof config.showImg == "boolean") ? config.showImg : true;
        if (showImg) {
            var imgUrl = (config && typeof config.imgUrl == "string") ? config.imgUrl
                : "jatos-publix/images/waiting.gif";
            var waitingImg = document.createElement('img');
            waitingImg.src = imgUrl;
            waitingImg.style.marginTop = "10px";
            div.appendChild(waitingImg);
        }

        // Add data attribute 'keep'
        const keep = (config && typeof config.keep == "boolean") ? config.keep : false;
        div.setAttribute('data-keep', keep);

        // Set timeout
        if (config && typeof config.timeout == "number") {
            setTimeout(() => div.remove(), config.timeout);
        }

        document.body.appendChild(div);
        return div;
    };

    // Keep this for backward compatibility
    jatos.removeOverlay = () => jatos.removeOverlays();

    /**
     * Removes all overlays that have the class 'jatosOverlay' and the data attribute
     * 'keep' set to "false". If force is true it also removes the ones with the data
     * attribute 'keep' set to "true".
     * @param {boolean} [force=false] - If true, remove overlays even if they are marked to keep
     */
    jatos.removeOverlays = function (force) {
        document.querySelectorAll('.jatosOverlay').forEach(el => {
            if (el.dataset.keep === "false" || force) el.remove();
        });
    };

    /**
     * Uses an overlay to show some IDs if worker type is 'Jatos'
     */
    function showIdOverlay() {
        if (jatos.workerType !== "Jatos") return;

        const idObj = {};
        if (jatos.frameId) idObj["frame"] = jatos.frameId;
        if (jatos.workerId) idObj["worker"] = jatos.workerId;
        if (jatos.studyResultId) idObj["study result"] = jatos.studyResultId;
        if (jatos.groupResultId) idObj["group"] = jatos.groupResultId;

        const text = Object.entries(idObj).map(([key, value]) => `${key}: ${value}`).join("\n");
        jatos.showOverlay({
            id: "idOverlay",
            text: text,
            style: "position:fixed;top:unset;left:4px;bottom:4px;transform:unset;font-size:10px;letter-spacing:0px;white-space:pre;line-height:normal;letter-spacing:normal;word-spacing:normal;text-align:left;",
            keep: true,
            showImg: false
        });
    }

    /**
     * Adds a button to the document that if pressed calls jatos.abortStudy.
     * By default this button is in the bottom-right corner but this and
     * other properties can be configured.
     *
     * @param {Object} [config] - Config object
     * @param {string} [config.text] - Button text
     * @param {boolean} [config.confirm=true] - Ask the worker for confirmation before aborting
     * @param {string} [config.confirmText] - Confirmation text
     * @param {string} [config.tooltip] - Tooltip text
     * @param {string} [config.msg] - Message to be sent back to JATOS and logged
     * @param {string} [config.style] - Additional CSS styles for the button element
     * @param {Function} [config.action] - Function to call instead of `jatos.abortStudy`
     */
    jatos.addAbortButton = function (config) {
        var buttonText = (config && typeof config.text == "string") ?
            config.text : "Cancel";
        var confirm = (config && typeof config.confirm == "boolean") ?
            config.confirm : true;
        var confirmText = (config && typeof config.confirmText == "string") ?
            config.confirmText : "Do you really want to cancel this study?";
        var tooltip = (config && typeof config.tooltip == "string") ?
            config.tooltip : "Cancels this study and deletes all already submitted data";
        var msg = (config && typeof config.msg == "string") ?
            config.msg : "Worker decided to abort";
        var style = 'color:black;' +
            'font-family:Sans-Serif;' +
            'font-size:20px;' +
            'letter-spacing:2px;' +
            'position:fixed;' +
            'margin:2em 0 0 2em;' +
            'bottom:1em;' +
            'right:1em;' +
            'opacity:0.6;' +
            'z-index:9999;' +
            'cursor:pointer;' +
            'text-shadow:-1px 0 white, 0 1px white, 1px 0 white, 0 -1px white;';
        if (config && typeof config.style == "string") style += ";" + config.style;

        var text = document.createTextNode(buttonText);
        var buttonDiv = document.createElement('div');
        buttonDiv.appendChild(text);
        buttonDiv.style.cssText = style;
        buttonDiv.setAttribute("title", tooltip);
        buttonDiv.addEventListener("click", function () {
            if (!confirm || window.confirm(confirmText)) {
                if (config && typeof config.action == "function") {
                    config.action(msg);
                } else {
                    jatos.abortStudy(msg);
                }
            }
        });

        document.body.appendChild(buttonDiv);
    };

    return {onLoad, showIdOverlay, removeBeforeUnloadWarning};
}
