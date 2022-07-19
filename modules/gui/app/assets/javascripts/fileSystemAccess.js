/**
 * Module that uses the File System Access API via browser-fs-access (https://github.com/GoogleChromeLabs/browser-fs-access)
 */

import { fileSave, supported } from '../browser-fs-access/index.js';

let downloadFileAbortController;

if (supported) {
    console.log('browser-fs-access: Using the File System Access API.');
} else {
    console.log('browser-fs-access: Using the fallback implementation.');
}

$('#waitingModal').on('click', '.cancel', function(e) {
    if (downloadFileAbortController) downloadFileAbortController.abort();
    hideWaitingModal();
});

/**
 * Save a response stream to a file
 *
 * @param {string} url - Request URL to get the stream
 * @param {optional string} postData - JSON data that is send with the request. If set the request will be a POST - if
 *                                     not set it will be a GET.
 * @param {optional string} filename - filename the stream will be saved under in the local file system. If not set the
 *                                     filename will be taken from the 'Content-Disposition' header.
 */
window.downloadFileStream = async function(url, postData, filename) {
    downloadFileAbortController = new AbortController();
    var init;
    if (postData) {
        init = {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            cache: 'no-cache',
            body: postData,
            signal: downloadFileAbortController.signal
        };
    } else {
        init = {
            method: 'GET',
            signal: downloadFileAbortController.signal
        };
    }

    const response = await fetch(url, init);
    try {
        const filenameFromContentDispositionHeader = getFilenameFromContentDispositionHeader(response);
        if (filenameFromContentDispositionHeader) filename = filenameFromContentDispositionHeader;
        if (!filename) throw `Downloading ${url} - no filename specified`;
        await fileSave(response, {
            fileName: filename
        });
    } catch (err) {
        showError("Download failed");
        console.error("Download failed (" + err + ")");
    } finally {
        hideWaitingModal();
    }
}

function getFilenameFromContentDispositionHeader(response) {
    // From https://stackoverflow.com/a/40940790/1278769
    var filename = null;
    var disposition = response.headers.get("Content-Disposition");
    if (disposition && disposition.indexOf('attachment') !== -1) {
        var filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
        var matches = filenameRegex.exec(disposition);
        if (matches != null && matches[1]) {
            filename = matches[1].replace(/['"]/g, '');
        }
    }
    return filename;
}
