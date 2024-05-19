export { LinksWithToggle }

import * as Helpers from "./helpers.js";
import * as CopyToClipboard from "./copyToClipboard.js";
import * as QrCodeModal from "./qrCodeModal.js";

/**
 * Renders the toggle button "Open Directly" / "Confirm First" and the study links that can be toggled by it.
 * The toggle button and the associated study links both carry an data attribute "data-study-link-toggle" with an
 * unique ID that can be used to identify the associated study links when the toggle is clicked. This prevents the
 * toggle to toggle arbitrary study links on the page.
 **/
class LinksWithToggle {

    constructor() {
        this.toggleId = Math.random().toString(16).slice(2); // ID used to associate toggle button to study links
    }

    /**
     * Appends a toggle button to the given toggleSelector and adds className to its class attribute.
     * Additionally it adds a click event listener to the toggle that toggles the button and toggles the "hide" class
     * in studyLinksContainerSelector by using the data-study-link-toggle data attribute.
     *
     * @param {String} toggleSelector               Selector that specifies the element where the toggle will be attached
     * @param {String} studyLinksContainerSelector  Selector to an element that will contains all study links, e.g. a table or Modal
     * @param {String} className                    Additional class or list of classes to be added to the toggle button
     */
    renderToggle = (toggleSelector, studyLinksContainerSelector, className) => {
        const $toggleButtons = $(`
            <div class="btn-group ${className}" data-study-link-toggle="${this.toggleId}">
                <button class="btn btn-xs btn-batch direct-link" data-bs-tooltip="Show links that start the study directly after clicking on them">Open Directly</button>
                <button class="btn btn-xs btn-outline-secondary confirm-link" data-bs-tooltip="Show links that do not start the study directly but display a Study Entry page before. Here a message can be displayed (specified in Study Properties) and the worker has to click a button to start the study.">Confirm First</button>
            </div>
        `);
        const toggleId = this.toggleId;
        $toggleButtons.click(function() {
            $(this).find('.btn')
                .toggleClass('btn-batch')
                .toggleClass('btn-outline-secondary');
            $(studyLinksContainerSelector).find(`[data-study-link-toggle=${toggleId}]`).each(function() {
                $(this).toggleClass("hide");
            });
        });
        $(toggleSelector).empty();
        $(toggleSelector).append($toggleButtons[0]);
        Helpers.activateTooltips($(toggleSelector));
    }

    /**
     * Renders a list of study links for the given list of study codes using the renderLink method
     *
     * @param {Array.String>} studyCodes            The study codes to be rendered
     * @param {String} studyLinksContainerSelector  Selector to an element that will contains all study links,
     *                                              e.g. a table or Modal. If not set, "<div></div>" will be used.
     */
    renderLinks = (studyCodes, studyLinksContainerSelector) => {
        const $container = studyLinksContainerSelector ? $(studyLinksContainerSelector) : $("<div></div>")
        studyCodes.map(code => $container.append(this.renderLink(code)));
        return $container[0];
    }

    /**
     * Renders one study link for the given study code in two versions, as a direct link and as a confirm link.
     * Additionally it adds buttons for copy-to-clipboard and showing of an QR code.
     *
     * @param {String>} studyCode   The study code to be rendered
     */
    renderLink = (studyCode) => {
        const directLink = window.routes.Publix.run(studyCode);
        const confirmLink = window.routes.Publix.studyEntry(studyCode);
        const $link = $(`
            <div>
                <div class="direct-link text-truncate" data-study-link-toggle="${this.toggleId}">
                    <span>${directLink}</span>
                    <span class="btn-clipboard no-info-icon" data-bs-tooltip="Copy this link to the clipboard."></span>
                    <span class="btn-qr-code no-info-icon" data-bs-tooltip="Show QR code for this link."
                        data-qr-code-text="${directLink}"></span>
                </div>
                <div class="confirm-link text-truncate hide" data-study-link-toggle="${this.toggleId}">
                    <span>${confirmLink}</span>
                    <span class="btn-clipboard no-info-icon" data-bs-tooltip="Copy this link to the clipboard."></span>
                    <span class="btn-qr-code no-info-icon" data-bs-tooltip="Show QR code for this link.
                        data-qr-code-text="${confirmLink}"></span>
                </div>
            </div>`);
        Helpers.activateTooltips($link);
        $link.find(".btn-clipboard").click(CopyToClipboard.onClick);
        $link.find(".btn-qr-code").click(QrCodeModal.show);
        return $link[0];
    }
}

