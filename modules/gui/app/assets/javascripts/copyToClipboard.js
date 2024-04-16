export { onClick }

// Handle copy-to-clipboard button clicks
function onClick(event) {
    const button = this;

    copyToClipboard(button.parentElement);

    // Change icon to checkmark for 2s
    $(button).addClass("btn-clipboard-copied");
    setTimeout(() => $(button).removeClass("btn-clipboard-copied"), 2000);

    // Temporarily remove tooltip
    $(button).removeAttr("title");

    // Show popover
    const popover = new bootstrap.Popover(button, {
        content: "Copied!",
        placement: "top",
        fallbackPlacements: ['top', 'bottom', 'left', 'right']
    });
    popover.toggle();

    //event.stopPropagation();

    // With the next click, anywhere, hide popover and restore to the tooltip
    $(document).click(function() {
        popover.hide();
        $(button).attr("title", "Copy to clipboard");
    });
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