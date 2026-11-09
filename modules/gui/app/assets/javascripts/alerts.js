/*
 * Handles alerts (little popups with info, warnings etc messages). Uses Bootstrap toasts, timeago.js library.
 * Messages allow HTML by default. Pass plainText = true to display them as text instead.
 */

export { Named, success, info, warning, error, showList, hideAll }

import * as Timeago from 'timeago';

/*
 * This class allows alerts with a namespace. E.g., if alerts are associated with a Modal one can get Alerts with a
 * namespace and then hide them if the Modal hides.
 */
class Named {

    constructor(name) {
        this.name = name;
    }

    success = (msg, delay = 5000, plainText = false) => {
        showToast(msg, "success", delay, this.name, plainText)
    }

    info = (msg, delay = 5000, plainText = false) => {
        showToast(msg, "info", delay, this.name, plainText)
    }

    warning = (msg, delay = 10000, plainText = false) => {
        showToast(msg, "warning", delay, this.name, plainText)
    }

    error = (msg, delay = 0, plainText = false) => {
        showToast(msg, "error", delay, this.name, plainText)
    }

    showList = (msgs, delay, plainText = false) => {
        if (msgs) {
            if (msgs.successList) msgs.successList.forEach((msg) => this.success(msg, delay, plainText));
            if (msgs.infoList) msgs.infoList.forEach((msg) => this.info(msg, delay, plainText));
            if (msgs.warningList) msgs.warningList.forEach((msg) => this.warning(msg, delay, plainText));
            if (msgs.errorList) msgs.errorList.forEach((msg) => this.error(msg, delay, plainText));
        }
    }

    hideAll = () => {
        if (this.name) $(`.toast-container .toast.show.${this.name}`).toast("hide")
    }
}

function success(msg, delay = 5000, plainText = false) {
    showToast(msg, "success", delay, undefined, plainText)
}

function info(msg, delay = 5000, plainText = false) {
    showToast(msg, "info", delay, undefined, plainText)
}

function warning(msg, delay = 10000, plainText = false) {
    showToast(msg, "warning", delay, undefined, plainText)
}

function error(msg, delay = 0, plainText = false) {
    showToast(msg, "error", delay, undefined, plainText)
}

/*
 * Shows multiple alerts defined in a list
 */
function showList(msgs, delay, plainText = false) {
    if (msgs) {
        if (msgs.successList) msgs.successList.forEach(function(msg) { success(msg, delay, plainText) });
        if (msgs.infoList) msgs.infoList.forEach(function(msg) { info(msg, delay, plainText) });
        if (msgs.warningList) msgs.warningList.forEach(function(msg) { warning(msg, delay, plainText) });
        if (msgs.errorList) msgs.errorList.forEach(function(msg) { error(msg, delay, plainText) });
    }
}

function hideAll() {
    $(".toast-container .toast.show").toast("hide")
}

function showToast(msg, type, delay, name, plainText = false) {
    let icon, headerText, textColor, iconColor;
    switch (type) {
        case "success":
            icon = 'bi-check-circle-fill';
            headerText = "Success";
            iconColor = "success";
            textColor = "body";
            break;
        case "info":
            icon = 'bi-info-circle-fill';
            headerText = "Info";
            iconColor = "info";
            textColor = "body";
            break;
        case "warning":
            icon = 'bi-exclamation-triangle-fill';
            headerText = "Warning";
            iconColor = "warning";
            textColor = "warning";
            break;
        case "danger":
        case "error":
            icon = 'bi-exclamation-octagon-fill';
            headerText = "Error";
            iconColor = "danger";
            textColor = "danger";
            break;
    }
    const html = `
            <div class="toast text-${textColor} bg-body-tertiary" role="alert" data-bs-autohide="${delay?"true":"false"}" data-bs-delay="${delay?delay:0}">
                <div class="toast-header bg-body-tertiary py-1">
                    <i class="${icon} text-${iconColor} fs-4 pe-2"></i>
                    <strong class="me-auto">${headerText}</strong>
                    <small class="timeago" datetime="${new Date().toISOString()}"></small>
                    <button type="button" class="btn btn-close2 btn-xs btn-nav ms-2" data-bs-dismiss="toast"></button>
                </div>
                <div class="toast-body"></div>
            </div>`;
    $(".toast-container").prepend(html);
    const toastElement = $(".toast").first();
    if (plainText) toastElement.find(".toast-body").text(msg);
    else toastElement.find(".toast-body").html(msg);
    if (name) toastElement.addClass(name);
    timeago.render(toastElement.find(".timeago").get());
    const toast = new bootstrap.Toast(toastElement);
    // Prevent random focus change after close button clicked
    toastElement.find(".btn-close").mousedown(function(e) {
        e.preventDefault();
        toast.dispose();
    });
    toast.show();
}