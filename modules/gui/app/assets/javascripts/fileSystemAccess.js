/**
 * Module that uses the File System Access API via the browser-fs-access library (https://github.com/GoogleChromeLabs/browser-fs-access)
 */
export {downloadFileStream, abortFileDownload}

import { fileSave, supported } from '../browser-fs-access-0.35.0/src/index.js';
import * as WaitingModal from './waitingModal.js';
import * as Alerts from './alerts.js';

if (supported) {
    console.log('browser-fs-access: Using the File System Access API.');
} else {
    console.log('browser-fs-access: Using the fallback implementation.');
}

// Allows the abortion of the file operation, e.g. by a waiting modal
let downloadFileAbortController;
function abortFileDownload() {
    if (downloadFileAbortController) downloadFileAbortController.abort();
}

/**
 * Save a response stream to a local file
 *
 * Hint: window.showSaveFilePicker() must run before the actual fetching of the file, otherwise we might get the error
 * "SecurityError Failed to execute 'showSaveFilePicker' on 'Window': Must be handling a user gesture to show a file
 * picker." when downloading larger files (https://developer.chrome.com/docs/capabilities/web-apis/file-system-access).
 *
 * @param {string} url - Request URL to get the stream
 * @param {optional string} postData - JSON data that is send with the request. If set the request will be a POST - if
 *                                     not set it will be a GET.
 * @param {string} rawFileName - The filename that is used to save the data stream in the local file system.
 *                               The filename will be sanitized before using it.
 * @param {string} csrfToken - A CSRF token (necessary if a POST request)
 */
async function downloadFileStream(url, postData, rawFileName, csrfToken) {
    const fileName = sanitizeFilename(rawFileName);
    const fileExtension = fileName.split('.').pop();

    try {
        // Uses window.showSaveFilePicker to pick a file handle. This is necessary for large file downloads, since
        // the browser might "lose context" between start of downloading and browser-fs-access' calling of
        // window.showSaveFilePicker because it might take too long. Therefore we call it before start fetching the file
        // when the "context" is still there.
        const fileHandle = await pickFileHandle(fileName);

        WaitingModal.show(true);

        const response = await fetchFile(url, postData, csrfToken);

        if (response.status !== 200) throw new Error(response.statusText)

        await fileSave(
            response,
            { fileName: fileName },
            fileHandle
        );
        Alerts.success(`Download of <i>${fileName}</i> was successful.`, 0);
    } catch (err) {
        if (err.name !== 'AbortError') {
            Alerts.error("Download failed");
            console.error("Download failed (" + err + ")");
        }
    } finally {
        WaitingModal.hide();
    }
}

/*
 * Sanitizes the given raw filename:
 * 1) trims whitespaces
 * 2) replaces all illegal chars with '_'
 * 3) change all letters to lower case
 * 4) limits filename to maximum of 100 chars
 */
function sanitizeFilename(rawFileName) {
    const illegalCharsRegex = /[\s\n\r\t\f*?\"\\\/,`<>|:~!§$%&^°]/g;
    const max = 100;
    let fileName = rawFileName
        .trim()
        .replaceAll(illegalCharsRegex, "_")
        .toLowerCase()
        .substring(0, max);
    return fileName;
}

/*
 * Uses window.showSaveFilePicker to pick a file handle.
 */
function pickFileHandle(fileName) {
    if (!supported) return;

    return window.showSaveFilePicker({
        suggestedName: fileName,
        types: [
            {
                description: `ZIP archive file`,
                accept: {'application/zip': ['.zip']},
            },
            {
                description: `JATOS study archive file`,
                accept: {'application/zip': ['.' + window.common.studyArchiveSuffix]},
            },
            {
                description: `JATOS results archive file`,
                accept: {'application/zip': ['.' + window.common.resultsArchiveSuffix]},
            },
            {
                description: `JSON file`,
                accept: {'application/json': ['.json']},
            },
            {
                description: `CSV file`,
                accept: {'text/csv': ['.csv']},
            },
            {
                description: `Log file`,
                accept: {'text/plain': ['.log']},
            }
        ]
    });
}

/*
 * Using Fetch API to get file from / post a file to the JATOS server.
 */
function fetchFile(url, postData, csrfToken) {
    downloadFileAbortController = new AbortController();
    let init;
    if (postData) {
        init = {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Csrf-Token': csrfToken
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

    // Must run after window.showSaveFilePicker()!
    return fetch(url, init);
}