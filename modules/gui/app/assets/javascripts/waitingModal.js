export { show, hide }

import * as Helpers from "./helpers.js";
import * as FileSystemAccess from "./fileSystemAccess.js";

// The waiting modal might be shown multiple times, so we have an array with all timeouts and when we hide the modal we
// clear them all.
const timeouts = [];

/*
 * Shows the waiting modal after 2 secs of waiting
 *
 * @param {boolean} cancelable - Show the cancel button. So far the cancel button only aborts file downloads done by
 *                               fileSystemAccess.js
 */
function show(cancelable = false) {
    if (typeof cancelable === 'boolean' && cancelable === true) {
        $('#waitingModalCancel').show();
    } else {
        $('#waitingModalCancel').hide();
    }
    hide(); // First hide an 'old' waiting modals
    timeouts.push(setTimeout(function() { $('#waitingModal').modal('show') }, 2000));
}

/*
 * Hides the waiting modal and clear all timeouts
 */
function hide() {
    for (let i = 0; i < timeouts.length; i++) {
        clearTimeout(timeouts[i]);
    }
    $('#waitingModal').modal('hide');
}

$('#waitingModalCancel').click(() => {
    FileSystemAccess.abortFileDownload();
    hide();
});