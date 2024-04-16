export { getLocalTime, getLocalTimeSimple, disableForm, setButtonWidthToMax, isJson, getWorkerTypeName,
        isLocalhost, getAuthMethodText, generateOrcidLink,
        generateFullUserString, trimTextWithThreeDots, getDateTimeYYYYMMDDHHmmss, loadUser,
        getTheme, getOidcLogoUrl, getOrcidLogoUrl, preventFormSubmitViaEnter, activatePopovers, generateMembersHtml };

const locale = "en-GB"
const timezone = new Date().toString().match(/\(([^\)]+)\)$/)[1];
const timezoneAbbr = /.*\s(.+)/.exec((new Date()).toLocaleDateString(navigator.language, { timeZoneName:'short' }))[1];
const getLocalTimeSimple = (timestamp) => new Date(timestamp).toLocaleString(locale);
const getLocalTime = (timestamp, type) => {
    if (type === 'sort') return timestamp;
    return timestamp
        ? `<span class="no-info-icon" data-toggle="tooltip" title="in local time ${timezoneAbbr} (${timezone}), with locale '${locale}'">${getLocalTimeSimple(timestamp)}</span>`
        : '<span class="text-body text-opacity-50">never</span>';
}

function clearForm(formElem) {
    $(formElem + " :is(input[type=text], input[type=password], textarea)").val("");
    $(formElem + " input[type=checkbox]").removeAttr('checked').prop('checked', false);
    $(formElem + " input[type=radio]").removeAttr('selected').prop('checked', false);
    $(formElem + " :is(input, textarea, button)").prop('disabled', false);
}

function disableForm(selector) {
    $(`${selector} input, textarea`).not(":input[type=reset]").attr("disabled", true);
}

// Adjust the width of every button to the widest one
function setButtonWidthToMax(element) {
    let maxWidth = 0;
    $(element).each(function(index) {
        const width = $(this).width();
        if (width > maxWidth) {
            maxWidth = width;
        }
    });
    $(element).each(function(index) {
        $(this).width(maxWidth);
    });
}

function isJson(string) {
    try {
        $.parseJSON(string);
        return true;
    } catch(error) {
        return false;
    }
}

function getWorkerTypeName(workerType) {
    switch (workerType) {
    case "Jatos":
        return "Jatos";
    case "PersonalSingle":
        return "Personal Single";
    case "PersonalMultiple":
        return "Personal Multiple";
    case "GeneralSingle":
        return "General Single";
    case "GeneralMultiple":
        return "General Multiple";
    case "MTSandbox":
        return "MTurk Sandbox";
    case "MT":
        return "MTurk";
    default:
        return "unknown";
    }
}

function isLocalhost() {
    return location.hostname === 'localhost' ||
    location.hostname === '' ||
    location.hostname === '[::1]' ||
    location.hostname.match(/^127(?:\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)){3}$/);
}

function getAuthMethodText(authMethod) {
    switch(authMethod) {
        case "DB": return "local";
        case "LDAP": return "LDAP";
        case "OAUTH_GOOGLE": return "Google";
        case "OIDC": return "OIDC";
        case "ORCID": return "ORCID";
        default: return authMethod;
    }
}

function generateOrcidLink(orcid) {
    return `<a href="https://orcid.org/${orcid}" target="_blank" class="text-nowrap"><img alt="ORCID logo" src="@routes.Assets.versioned("lib/jatos-gui/images/ORCIDiD_iconvector.svg")" width="16" height="16" />&nbsp;${orcid}</a>`;
}

function generateFullUserString(name, username, authMethod) {
    if (authMethod == "ORCID") {
        return `${name} (${generateOrcidLink(username)})`;
    } else {
        return `${name} (${username})`;
    }
}

function trimTextWithThreeDots(text, length) {
     return text.length > length ? text.substring(0, length - 3) + "..." : text;
}

function copyToClipboard(element) {
    const textToCopy = element.textContent.trim();
    // navigator clipboard api needs a secure context (https)
    if (navigator.clipboard && window.isSecureContext) {
        return navigator.clipboard.writeText(textToCopy);
    } else {
        window.prompt("Copy to clipboard: Ctrl+C, Enter", textToCopy);
    }
}

function getDateTimeYYYYMMDDHHmmss() {
    return new Date().toISOString().replaceAll(/\.\d{3}Z/g, "").replaceAll(/[:\-T\.]/g, "");
}

function getFormattedDate(timestamp) {
    // From https://stackoverflow.com/a/67705873/1278769
    // Uses format YYYY/MM/DD HH:mm:ss
    const pad = (n,s=2) => (`${new Array(s).fill(0)}${n}`).slice(-s);
    const d = new Date(timestamp);
    return `${pad(d.getFullYear(),4)}/${pad(d.getMonth()+1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/**
 * Returns the request's host URL without path (and without base path from Common.getJatosUrlBasePath()) or query string
 * (e.g. just "https://www.example.com"). It returns the URL with the proper protocol http or https.
 */
function getRealHostUrl() {
    return location.protocol + '//' + location.host;
}

/**
 * Returns the request's host URL with base path from Common.getJatosUrlBasePath() but without the rest of the path
 * or query string (e.g. "https://www.example.com/basepath/"). It returns the URL with the proper protocol http
 * or https.
 * DEPRECATED: use commons instead
 */
function getRealBaseUrl() {
    return `${location.protocol}//${location.host}@Common.getJatosUrlBasePath()`;
}

function loadUser(username) {
    return $.ajax({
        type: 'GET',
        url: window.routes.Users.singleUserData(username),
        success: function(user) {
            // As a side-effect update the navbar's user data
            $("#userSidebarName").text(user.name);
            $("#userSidebarUsername").text(`(${user.name})`);
        },
        error: function(response) {
           Alerts.error(response.responseText);
        }
    });
}

function getTheme() {
    const theme = document.documentElement.getAttribute('data-bs-theme');
    if (theme === 'system') {
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? "dark" : "light";
    } else {
        return theme;
    }
}

function getOidcLogoUrl() {
    if (window.common.oidcSigninButtonLogoUrl.includes("http")) {
        return window.common.oidcSigninButtonLogoUrl;
    } else {
        return window.common.jatosUrlBasePath + window.common.oidcSigninButtonLogoUrl;
    }
}

function getOrcidLogoUrl() {
    if (window.common.orcidSigninButtonLogoUrl.includes("http")) {
        return window.common.orcidSigninButtonLogoUrl;
    } else {
        return window.common.jatosUrlBasePath + window.common.orcidSigninButtonLogoUrl;
    }
}

function preventFormSubmitViaEnter(form) {
    // Prevent form submitting via pressing the enter key
    $(form).find("input").keypress(function(e) {
        if (e.keyCode == 13) {
            e.preventDefault();
            return false;
        }
    });
}

function activatePopovers() {
    const popoverTriggerList = document.querySelectorAll('[data-bs-toggle="popover"]');
    const popoverList = [...popoverTriggerList].map(popoverTriggerEl => new bootstrap.Popover(popoverTriggerEl));
}

// You still have to activate the popovers with activatePopovers()
function generateMembersHtml(members) {
    if (members.length < 4 ) {
        return members.map((m) => generateFullUserString(m.name, m.username, m.authMethod)).join("<br>");
    } else {
        const m = members.map((m) => `${m.name} (${m.username})`).join("<br>");
        return`<button class="btn btn-nav btn-sm" type="button" data-bs-toggle="popover" data-bs-trigger="focus" data-bs-container="body" data-bs-html="true" data-bs-content="${m}">Show All</button>`;
    }
}