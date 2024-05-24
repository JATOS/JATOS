/*
 * Handles the copy-to-clipboard button (class .btn-clipboard and defined in CSS)
 */

export { onClick }

import * as Helpers from "./helpers.js";

// Add the copy-to-clipboard event listener to every ".btn-clipboard". But we need the ".off" in case this library is
// imported multiple times on the same page
$(".btn-clipboard").off("click").on("click", onClick);

/*
 * Use this function to add an copy-to-clipboard event handler manually, e.g. for dynamically generated elements
 */
function onClick(event) {
    const button = this;

    copyToClipboard(button.parentElement);

    // Change icon to checkmark for 2s
    $(button).addClass("btn-clipboard-copied");
    setTimeout(() => $(button).removeClass("btn-clipboard-copied"), 2000);

    // Dispose tooltip
    const tooltip = bootstrap.Tooltip.getInstance(button);
    if (tooltip) {
        tooltip.dispose();
    }
    $(button).removeAttr("data-bs-tooltip data-bs-title data-bs-delay data-bs-trigger");

    // Create and show popover
    let popover = bootstrap.Popover.getInstance(button);
    if (!popover) {
        popover = new bootstrap.Popover(button, {
            content: "Copied!",
            placement: "top",
            fallbackPlacements: ['top', 'bottom', 'left', 'right']
        });

        // With the next click, anywhere, hide popover and enable the tooltip again
        $(button).on('shown.bs.popover', function() {
            $(document).one("click", function() {
                popover.hide();
            });
        });
    }
    popover.show();

}

/*
 * The actual copying to the clipboard
 */
function copyToClipboard(element) {
    const textToCopy = getText(element);

    // navigator clipboard api needs a secure context (https)
    if (navigator.clipboard && window.isSecureContext) {
        // It gets copied even when reject is fired - weird
        navigator.clipboard.writeText(textToCopy).catch(() => {});
    } else {
        window.prompt("Copy to clipboard: Ctrl+C, Enter", textToCopy);
    }
}

/*
 * Recursive function that extracts the text content of a hierarchy of DOM elements. It thereby ignores elements that
 * have a class "hide". It assumes that all text content is in its own element without other child elements.
 */
function getText(element) {
    if (element.children.length > 0 ) {
        return Array.from(element.children)
            .filter(e => !e.classList.contains("hide")) // filter out elements with class "hide"
            .map(e => getText(e))
            .filter(text => text != "") // filter out empty text elements
            .join("\n");
    } else {
        return element.textContent;
    }
}