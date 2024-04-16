export { show, hide }

import * as Helpers from "./helpers.js";
import * as FileSystemAccess from "./fileSystemAccess.js";

const timeouts = [];

function show(cancelable, msg) {
    if (typeof cancelable === 'boolean' && cancelable === true) {
        $('#waitingModal .modal-footer').show();
    } else {
        $('#waitingModal .modal-footer').hide();
    }
    hide(msg);
    timeouts.push(setTimeout(function() { $('#waitingModal').modal('show') }, 2000));
}

function hide(msg) {
    for (let i = 0; i < timeouts.length; i++) {
        clearTimeout(timeouts[i]);
    }
    $('#waitingModal').modal('hide');
}

$('#waitingModalCancel').click(() => {
    FileSystemAccess.abortFileDownload();
    hide();
});