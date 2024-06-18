export { show, hide }

import * as FileSystemAccess from "./fileSystemAccess.js";

// The waiting modal might be shown multiple times, so we have an array with all timeouts and when we hide the modal we
// clear them all. We are using timeouts because Bootstrap's 'data-bs-delay' attribute didn't seem to work.
let timeouts = [];

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
    hide(); // First hide an 'old' waiting modal

    // We have to remove any event handler originating in the hide function
    $('#waitingModal').off('shown.bs.modal');

    // I couldn't get 'data-bs-delay' working and therefore I'm using 'setTimeout'
    timeouts.push(setTimeout(function() { $('#waitingModal').modal('show') }, 2000));
}

/*
 * Hides the waiting modal and clear all timeouts
 */
function hide() {
    for (let i = 0; i < timeouts.length; i++) {
        clearTimeout(timeouts[i]);
    }
    timeouts = [];

    $('#waitingModal').modal('hide');
    // We also have to deal with the Modals that are in the process of being shown
    $('#waitingModal').one('shown.bs.modal', () => $('#waitingModal').modal('hide'));
}

$('#waitingModalCancel').click(() => {
    FileSystemAccess.abortFileDownload();
    hide();
});