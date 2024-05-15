export { Named, success, info, warning, error, showList, hideAll }

import * as Timeago from './timeago.min.js';

class Named {

    constructor(name) {
        this.name = name;
    }

    success = (msg, delay = 5000) => {
        showToast(msg, "success", delay, this.name)
    }

    info = (msg, delay = 5000) => {
        showToast(msg, "info", delay, this.name)
    }

    warning = (msg, delay = 10000) => {
        showToast(msg, "warning", delay, this.name)
    }

    error = (msg, delay = 0) => {
        showToast(msg, "error", delay, this.name)
    }

    showList = (msgs, delay) => {
        const name = this.name;
        if (msgs) {
            if (msgs.successList) msgs.successList.forEach((msg) => success(msg, delay, name));
            if (msgs.infoList) msgs.infoList.forEach((msg) => info(msg, delay, name));
            if (msgs.warningList) msgs.warningList.forEach((msg) => warning(msg, delay, name));
            if (msgs.errorList) msgs.errorList.forEach((msg) => error(msg, delay, name));
        }
    }

    hideAll = () => {
        if (this.name) $(`.toast-container .toast.show.${this.name}`).toast("hide")
    }
}

function success(msg, delay = 5000) {
    showToast(msg, "success", delay)
}

function info(msg, delay = 5000) {
    showToast(msg, "info", delay)
}

function warning(msg, delay = 0) {
    showToast(msg, "warning", delay)
}

function error(msg, delay = 0) {
    showToast(msg, "error", delay)
}

function showList(msgs, delay) {
    if (msgs) {
        if (msgs.successList) msgs.successList.forEach(function(msg) { success(msg, delay) });
        if (msgs.infoList) msgs.infoList.forEach(function(msg) { info(msg, delay) });
        if (msgs.warningList) msgs.warningList.forEach(function(msg) { warning(msg, delay) });
        if (msgs.errorList) msgs.errorList.forEach(function(msg) { error(msg, delay) });
    }
}

function hideAll() {
    $(".toast-container .toast.show").toast("hide")
}

function showToast(msg, type, delay, name) {
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
            <div class="toast text-${textColor}" role="alert" data-bs-autohide="${delay?"true":"false"}" data-bs-delay="${delay?delay:0}">
                <div class="toast-header py-1">
                    <i class="${icon} text-${iconColor} fs-4 pe-2"></i>
                    <strong class="me-auto">${headerText}</strong>
                    <small class="timeago" datetime="${new Date().toISOString()}"></small>
                    <button type="button" class="btn btn-close2 btn-xs btn-nav ms-2" data-bs-dismiss="toast"></button>
                </div>
                <div class="toast-body">${msg}</div>
            </div>`;
    $(".toast-container").prepend(html);
    const toastElement = $(".toast").first();
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