/**
 * Module that uses the File System Access API via browser-fs-access (https://github.com/GoogleChromeLabs/browser-fs-access)
 */

import { fileSave, supported } from '../browser-fs-access-0.31.0/src/index.js';

let downloadFileAbortController;

if (supported) {
    console.log('browser-fs-access: Using the File System Access API.');
} else {
    console.log('browser-fs-access: Using the fallback implementation.');
}

window.abortFileDownload = function () {
    if (downloadFileAbortController) downloadFileAbortController.abort();
}

/**
 * Save a response stream to a local file
 *
 * @param {string} url - Request URL to get the stream
 * @param {optional string} postData - JSON data that is send with the request. If set the request will be a POST - if
 *                                     not set it will be a GET.
 * @param {optional string} rawFileName - file name the stream will be saved under in the local file system.
 */
window.downloadFileStream = async function (url, postData, rawFileName) {
    try {
        showWaitingModal(true);

        const fileName = generateFilename(rawFileName);
        if (!fileName) throw `Downloading ${url} - no file name specified`;

        // Trying to get the file handle before requesting the file. If the time between 'click' and getting the file
        // handle is too long one gets a "Failed to execute 'showSaveFilePicker' on 'Window': Must be handling a user
        // gesture to show a file picker.".
        let handle;
        if (supported) {
            handle = (await window.showSaveFilePicker({
                suggestedName: fileName
            }));
        }

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

        await fileSave(
            response,
            { fileName: fileName },
            handle
        );
    } catch (err) {
        if (err.name !== 'AbortError') {
            showError("Download failed");
            console.error("Download failed (" + err + ")");
        }
    } finally {
        hideWaitingModal();
    }
}

function generateFilename(rawFileName) {
    const illegalCharsRegex = /[\s\n\r\t\f*?\"\\\/,`<>|:~!§$%&^°]/g;
    const max = 100;
    let fileName = rawFileName
        .trim()
        .replaceAll(illegalCharsRegex, "_")
        .toLowerCase()
        .substring(0, max);
    return fileName;
}