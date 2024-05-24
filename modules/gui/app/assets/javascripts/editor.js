/*
 * Using ACE (https://ace.c9.io/) as a code editor for JATOS
 */

export { setup, getValue, focus };

import * as Alerts from "./alerts.js";
import * as Helpers from "./helpers.js";

// Store multiple editors and use the DOM selector as the key
const editors = {};

// Listen to the "colorThemeChange" emitted from colorThemeToggler.js
document.addEventListener("colorThemeChange", () => setTheme());

/**
 * Sets up and shows an editor
 *
 * @param {string} mode - ACE's editor mode, e.g. 'json'
 * @param {string} value - The initial content of the editor
 * @param {string} selector - The DOM selector to identify the editor (needed to allow multiple editors on the same page)
 * @param {number} maxLines - ACE's 'maxLine' property
 */
function setup(mode, value, selector, maxLines = 30) {
    if (!editors[selector]) {
        const theme = Helpers.getTheme() === 'light' ? 'ace/theme/tomorrow' : 'ace/theme/tomorrow_night';
        const editor = ace.edit($(selector).get(0), {
            mode: 'ace/mode/' + mode,
            theme: theme,
            autoScrollEditorIntoView: true,
            showPrintMargin: false,
            minLines: 8,
            maxLines: maxLines
        });
        editors[selector] = editor;
    }

    // If in "json" mode, format the JSON prettily
    if (mode === "json") {
        try {
            const jsonPretty = value ? JSON.stringify(JSON.parse(value), null, 2) : "";
            editors[selector].getSession().setValue(jsonPretty);
        } catch (e) {
            editors[selector].getSession().setValue("");
        }
    } else {
        editors[selector].getSession().setValue(value);
    }
}

/*
 * Function used to get the current content of the editor
 */
const getValue = (selector) => editors[selector].getSession().getValue();

/*
 * Focus on the editor that is specified by a selector
 */
const focus = (selector) => editors[selector].focus();

function setTheme() {
    const aceTheme = Helpers.getTheme() == 'dark' ? 'ace/theme/tomorrow_night' : 'ace/theme/tomorrow';
    for (const [selector, editor] of Object.entries(editors)) {
        editor.setTheme(aceTheme);
    }
}

$(".pretty-json-button").click(function(event) {
    const selector = $(this).data("target");
    try {
        const json = editors[selector].getSession().getValue();
        const jsonPretty = JSON.stringify(JSON.parse(json), null, 2);
        editors[selector].getSession().setValue(jsonPretty);
    } catch (e) {
        showError("Invalid JSON format");
    }
});