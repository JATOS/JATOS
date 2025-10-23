export {
    getLocalTimeSimple,
    getLocalTimeDataTables,
    getDateTimeYYYYMMDDHHmmss,
    clearForm,
    disableForm,
    setButtonWidthToMax,
    isJson,
    getWorkerTypeNames,
    getWorkerTypeUIName,
    isLocalhost,
    getAuthMethodText,
    generateOrcidLink,
    generateFullUserString,
    generateMembersHtml,
    getOidcLogoUrl,
    getOrcidLogoUrl,
    getSramLogoUrl,
    getConextLogoUrl,
    escapeHtml,
    trimTextWithThreeDots,
    getTheme,
    activateTooltips,
    activateTooltipsOnDataTablesDropdowns,
    activatePopovers,
    generateModalSubtitles,
    triggerButtonByEnter,
    getDataFromDataTableRow,
    decodeHtmlEntities,
    encodeHtmlEntities
};

const isTouchDevice = ('ontouchstart' in window) || (navigator.maxTouchPoints > 0) || (navigator.msMaxTouchPoints > 0);

const browsersLocale = (navigator.languages && navigator.languages.length) ? navigator.languages[0] : navigator.language;

const locale = window.common.locale ? window.common.locale : browsersLocale;

// Returns IANA timezone identifier that the browser uses
const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;

// Returns the timezone abbreviation
const timezoneAbbr = /.*\s(.+)/.exec((new Date()).toLocaleDateString(navigator.language, { timeZoneName:'short' }))[1];

/**
 * Returns the data-time for the given timestamp using the set locale
 *
 * @param {number} timestamp - Timestamp to be rendered as date-time
 */
function getLocalTimeSimple(timestamp) {
    return new Date(timestamp).toLocaleString(locale);
}

/**
 * Used in DataTables tables. If type is 'sort' it just returns the given timestamp. In any other case it returns
 * HTML with the date-time of the timestamp in the set locale and a tooltip.
 *
 * @param {number} timestamp - Timestamp to be rendered as date-time
 * @param {string} type - DataTables type
 */
function getLocalTimeDataTables(timestamp, type) {
    if (type === 'sort') return timestamp;
    if (type === 'filter') return new Date(timestamp).toISOString();
    return timestamp
        ? `<span class="no-info-icon" data-bs-tooltip="in local time ${timezoneAbbr} (${timezone}), with locale '${locale}'">${getLocalTimeSimple(timestamp)}</span>`
        : '<span class="text-body text-opacity-50">never</span>';
}

/**
 * Returns the current date-time in the format 'YYYYMMDDHHmmss', good for use in filenames
 */
function getDateTimeYYYYMMDDHHmmss() {
    return new Date().toISOString().replaceAll(/\.\d{3}Z/g, "").replaceAll(/[:\-T\.]/g, "");
}

/**
 * Clears the given HTML form element (e.g. input, textarea), removes 'disabled' and 'checked' properties.
 *
 * @param {string} selector - Selector of the form element
 */
function clearForm(selector) {
    $(selector + " :is(input[type=text], input[type=password], textarea)").val("");
    $(selector + " input[type=checkbox]").removeAttr('checked').prop('checked', false);
    $(selector + " input[type=radio]").removeAttr('selected').prop('checked', false);
    $(selector + " :is(input, textarea, button)").prop('disabled', false);
}

/**
 * Disables elements of a HTML form element, e.g. input, textarea.
 *
 * @param {string} selector - Selector of the form element
 */
function disableForm(selector, value = true) {
    $(selector).find("input, textarea, .slider, .ace_editor").not(":input[type=reset]").attr("disabled", value);
}

/**
 * Adjust the width of every button to the widest one
 *
 * @param {string} selector - Selector of a parent element containing all buttons
 */
function setButtonWidthToMax(selector) {
    let maxWidth = 0;
    $(selector).each(function(index) {
        const width = $(this).width();
        if (width > maxWidth) {
            maxWidth = width;
        }
    });
    $(selector).each(function(index) {
        $(this).width(maxWidth);
    });
}

/**
 * Checks if the given string is of JSON format.
 */
function isJson(string) {
    try {
        $.parseJSON(string);
        return true;
    } catch(error) {
        return false;
    }
}

/**
 * Returns a map object with all JATOS worker types mapped to their GUI names
 */
function getWorkerTypeNames() {
    return {
        "Jatos": "Jatos",
        "PersonalSingle": "Personal Single",
        "PersonalMultiple": "Personal Multiple",
        "GeneralSingle": "General Single",
        "GeneralMultiple": "General Multiple",
        "MTSandbox": "MTurk Sandbox",
        "MT": "MTurk"
    }
}

/**
 * Returns the UI worker type name for a given JATOS worker type
 */
function getWorkerTypeUIName(workerType) {
    return getWorkerTypeNames()[workerType];
}

/**
 * Checks if this JATOS is running on 'localhost'. It's not accurate. It just checks the location.hostname.
 */
function isLocalhost() {
    return location.hostname === 'localhost' ||
    location.hostname === '' ||
    location.hostname === '[::1]' ||
    location.hostname.match(/^127(?:\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)){3}$/);
}

/**
 * Returns the GUI name of the authentication method (of a user)
 *
 * @param {string} authMethod - Authentication method used for the user
 */
function getAuthMethodText(authMethod) {
    switch(authMethod) {
        case "DB": return "local";
        case "LDAP": return "LDAP";
        case "OAUTH_GOOGLE": return "Google";
        case "OIDC": return "OIDC";
        case "ORCID": return "ORCID";
        case "SRAM": return "SRAM";
        case "CONEXT": return "CONEXT";
        default: return authMethod;
    }
}

/**
 * Generates a HTML link for an ORCID user including ORCID logo
 *
 * @param {string} orcid - User's ORCID ID
 */
function generateOrcidLink(orcid) {
    return `<a href="https://orcid.org/${orcid}" target="_blank" class="text-nowrap"><img class="me-1" src="${window.routes.assets("lib/jatos-gui/images/ORCIDiD_iconvector.svg")}" alt="ORCID logo" width="16" height="16"/>${orcid}</a>`;
}

/**
 * Generates HTML containing the name and username and if it is an ORCID user, the ORCID link.
 *
 * @param {string} name - Name of the user
 * @param {string} username - Username of the user
 * @param {string} authMethod - Authentication method of the user
 */
function generateFullUserString(name, username, authMethod) {
    if (authMethod == "ORCID") {
        return `${name} (${generateOrcidLink(username)})`;
    } else {
        return `${name} (${username})`;
    }
}

/**
 * Generates HTML that shows the members of a study, either as single line, or as a list, or as 'Show All' button with a Popover
 *
 * @param {string[]} members - Array of usernames
 */
function generateMembersHtml(members) {
    if (members.length == 1) {
        const m = members[0];
        return `${generateFullUserString(m.name, m.username, m.authMethod)}`;
    } else if (members.length < 4 ) {
        // Show the users in a list
        const memberList = members.map((m) => `<li>${generateFullUserString(m.name, m.username, m.authMethod)}</li>`).join("");
        return `<ol class="mb-0 ps-3">${memberList}</ol>`;
    } else {
        // Show a popover with the users. You still have to activate the popovers with activatePopovers()
        const memberList = members.map((m) => `<li>${generateFullUserString(m.name, m.username, m.authMethod)}</li>`).join("");
        const membersHtml = `<b>This study has ${members.length} member users</b>:<ol class="mb-0">${memberList}</ol>`;
        return`<button class="btn btn-nav btn-sm" type="button" data-bs-toggle="popover" data-bs-trigger="focus" data-bs-container="body" data-bs-html="true" data-bs-content="${escapeHtml(membersHtml)}">Show All</button>`;
    }
}

/**
 * Little helper function. The URL to the OIDC logo (e.g. displayed on the signin page) can be determined in the
 * jatos.conf. If the set URL is an external one and contains an 'http', just return the URL. If the set URL is an
 * internal one, we add the JATOS URL base path.
 */
function getOidcLogoUrl() {
    if (window.common.oidcSigninButtonLogoUrl.includes("http")) {
        return window.common.oidcSigninButtonLogoUrl;
    } else {
        return window.common.jatosUrlBasePath + window.common.oidcSigninButtonLogoUrl;
    }
}

/**
 * Little helper function. The URL to the ORCID logo (e.g. displayed on the signin page) can be determined in the
 * jatos.conf. If the set URL is an external one and contains an 'http', just return the URL. If the set URL is an
 * internal one, we add the JATOS URL base path.
 */
function getOrcidLogoUrl() {
    if (window.common.orcidSigninButtonLogoUrl.includes("http")) {
        return window.common.orcidSigninButtonLogoUrl;
    } else {
        return window.common.jatosUrlBasePath + window.common.orcidSigninButtonLogoUrl;
    }
}

/**
 * Little helper function. The URL to the Surf SRAM logo (e.g. displayed on the signin page) can be determined in the
 * jatos.conf. If the set URL is an external one and contains an 'http', just return the URL. If the set URL is an
 * internal one, we add the JATOS URL base path.
 */
function getSramLogoUrl() {
    if (window.common.sramSigninButtonLogoUrl.includes("http")) {
        return window.common.sramSigninButtonLogoUrl;
    } else {
        return window.common.jatosUrlBasePath + window.common.sramSigninButtonLogoUrl;
    }
}

/**
 * Little helper function. The URL to the Surf CONEXT logo (e.g. displayed on the signin page) can be determined in the
 * jatos.conf. If the set URL is an external one and contains an 'http', just return the URL. If the set URL is an
 * internal one, we add the JATOS URL base path.
 */
function getConextLogoUrl() {
    if (window.common.conextSigninButtonLogoUrl.includes("http")) {
        return window.common.conextSigninButtonLogoUrl;
    } else {
        return window.common.jatosUrlBasePath + window.common.conextSigninButtonLogoUrl;
    }
}

/**
 * Escape some HTML characters from the given string for use in tooltips
 */
function escapeHtml(str) {
    return str.replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
}

/**
 * Trims the given text if it exceeds a certain length and adds '...' to the end
 *
 * @param {string} text - Text to be trimmed
 * @param {number} length - Max. length of text (including the '...')
 */
function trimTextWithThreeDots(text, length) {
     return text.length > length ? text.substring(0, length - 3) + "..." : text;
}

/**
 * Returns the color theme, either 'dark' or 'light'. The original theme as stored by colorThemeToggler.js in
 * the 'data-bs-theme' attribute of the document element can be 'dark', 'light', or 'system'. If the original theme is
 * set to 'system' it queries the 'prefers-color-scheme' of the browser. If the browser does not have one it returns
 * 'light'.
 */
function getTheme() {
    const theme = document.documentElement.getAttribute('data-bs-theme');
    if (theme === 'system') {
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? "dark" : "light";
    } else {
        return theme;
    }
}

/**
 * Activates Bootstrap 5 tooltips. Elements with tooltips need to have at least the attribute 'data-bs-tooltip' defining
 * the tooltip content. We don't use the default Bootstrap attributes, data-bs-toggle="tooltip" and data-bs-title,
 * because 1) we want to avoid clashes with other features, e.g. popovers using the same attribute, 2) we want to have
 * the possibility to not activate them at all on devices with touch screens, and 3) DataTables sometimes (e.g. with
 * Dropdowns) activates them automatically - something we don't want all the time. Most importantly this function adds
 * the 'data-bs-title' with the content of 'data-bs-tooltip'. Then it adds several tooltips config attributes (if not
 * set already): 'data-bs-delay', 'data-bs-trigger' ('hover' only - no 'focus'), and 'data-bs-container'.
 *
 * @param {optional string|jQuery element} parent - Either a selector string or an jQuery element specifying a parent
 *          element containing the elements with tooltips to be activated. If not set all tooltips in document will be
 *          activated.
 */
function activateTooltips(parent) {
    if (isTouchDevice) return;

    let $elements;
    if (!parent) {
        $elements = $('[data-bs-tooltip]');
    } else if (parent instanceof jQuery) {
        $elements = parent.find('[data-bs-tooltip]');
    } else if (typeof parent === 'string' || parent instanceof String) {
        $elements = $(`${parent} [data-bs-tooltip]`);
    }
    $elements.each(function() {
        const title = $(this).attr('data-bs-tooltip');
        if (!title) {
            console.warn("Element has attribute 'data-bs-tooltip' without content");
            return;
        }
        $(this).attr('data-bs-title', title);

        // Add our default attributes if they aren't already set
        if (!$(this).is('[data-bs-delay]')) $(this).attr('data-bs-delay', '{"show":1500, "hide":150}');
        if (!$(this).is('[data-bs-trigger]')) $(this).attr('data-bs-trigger', 'hover');
        if (!$(this).is('[data-bs-container]')) $(this).attr('data-bs-container', 'body');

        new bootstrap.Tooltip(this);
    });
}

/**
 * Hack to activate tooltips on dropdowns that are dynamically generated by DataTables
 *
 * @param {object} dataTable - DataTables object
 */
function activateTooltipsOnDataTablesDropdowns(dataTable) {
    if (isTouchDevice) return;

    dataTable.on('buttons-action', function (e, buttonApi, dataTable, node, config) {
        if ($(node).hasClass("dropdown-toggle")) {
            activateTooltips($(node).siblings('.dt-button-collection'));
        }
    });
}

/**
 * Activates Bootstrap 5 popovers.
 *
 * @param {optional string} parent - A selector string specifying a parent element containing the elements with the
 *          popovers to be activated. If not set all tooltips in document will be activated.
 */
function activatePopovers(parent) {
    const selector = parent ? `${parent} [data-bs-toggle="popover"]` : '[data-bs-toggle="popover"]';
    const popoverTriggerList = document.querySelectorAll(selector);
    const popoverList = [...popoverTriggerList].map(popoverTriggerEl => new bootstrap.Popover(popoverTriggerEl));
}

/**
 * Attaches subtitles to the given Modal element
 *
 * @param {string} modalSelector - A selector specifying the Modal element
 * @param {object[]} subtitleList - Array of objects. Each object is of {subtitleName: subtitleValue}.
 */
function generateModalSubtitles(modalSelector, subtitleList, newLine = false) {
    const joinStr = newLine ? "<br>" : ", ";
    const subtitlesHtml = Object.entries(subtitleList).map(st =>
        `<span class="fw-normal">${st[0]}</span>: <span class="user-select-all">${st[1]}</span>`
    ).join(joinStr);
    const subtitleSpan = `<div class="modal-subtitle fw-light fs-6 text-wrap text-break">${subtitlesHtml}</div>`;
    $(`${modalSelector} .modal-subtitle`).remove();
    $(`${modalSelector} .modal-title`).append(subtitleSpan);
}

/**
 * Trigger a button 'click' event if the 'enter' key is pressed inside an DOM element. Useful to trigger the 'confirmed'
 * button inside a simple form.
 *
 * @param {string} selector - The selector to the element to be listened for an 'enter' key press
 * @param {string} buttonSelector - The selector specifying the button to be clicked
 */
function triggerButtonByEnter(selector, buttonSelector) {
    $(selector).on("keydown", function(e) {
        if (e.key == "Enter") {
            e.preventDefault();
            $(buttonSelector).trigger("click");
        }
    });
}

/**
 * Get data from a DataTable row.
 *
 * @param {object} dataTable - DataTables object to get the data from
 * @param {object} element - DOM Element that is a child of the row we want to get the data from
 */
function getDataFromDataTableRow(dataTable, element) {
    const row = $(element).closest('tr');
    return dataTable.row(row).data();
}

/**
 * Replace HTML entities with their characters, e.g., '&lt' with '<';
 *
 * @param {string} str - A string
 * @returns The decoded string
 */
function decodeHtmlEntities(str) {
    const textArea = document.createElement('textarea');
    textArea.innerHTML = str;
    return textArea.value;
}

/**
 * Replace in HTML reserved characters with their HTML entities, e.g., '<' with '&lt';
 *
 * @param {string} str - A string
 * @returns The encoded string
 */
function encodeHtmlEntities(str) {
    const textArea = document.createElement('textarea');
    textArea.textContent = str;
    return textArea.innerHTML;
}