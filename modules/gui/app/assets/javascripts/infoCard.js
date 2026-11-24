export { render }

import * as Helpers from "helpers";
import * as CopyToClipboard from "copyToClipboard";

$(document).on('click', '.info-card .info-card-copy', CopyToClipboard.onClick);

/**
 * Render an info card with labeled values. Text and attribute values are HTML-escaped.
 *
 * @param {Object[]} items - Items to display, in order.
 * @param {string} items[].name - Label displayed before the value.
 * @param {string|number|boolean} items[].value - Full value; also used for copying and truncation tooltips.
 * @param {string} [items[].tooltip] - Explanatory tooltip on the label.
 * @param {string} [items[].displayValue] - Alternate display text; does not change the copied value.
 * @param {number} [items[].truncate] - Positive integer limiting displayed characters. When shortened,
 *     the value gets a tooltip containing the full original value.
 * @param {boolean} [items[].addCopyBtn=false] - Add a CopyToClipboard button that copies the full original value.
 * @param {boolean} [items[].useMonospace=false] - Apply font-monospace to the displayed value.
 * @param {string} [items[].icon] - Bootstrap icon class displayed before the value, e.g. "bi-git".
 * @param {string} [items[].href] - Link destination for the value; opens in a new tab.
 * @returns {string} HTML for the info card, ready to insert into the page.
 */
function render(items) {
    return `
        <div class="info-card card card-body bg-body-tertiary mb-4">
            <div class="row row-cols-auto">
                ${items.map(renderItem).join("")}
            </div>
        </div>
    `;
}

function renderItem(item) {
    const escape = value => Helpers.escapeHtml(String(value));
    const value = String(item.value ?? "");
    const display = String(item.displayValue ?? value);
    const shortened = Number.isInteger(item.truncate) && item.truncate > 0 && display.length > item.truncate;
    const text = shortened ? display.slice(0, item.truncate) : display;
    const tooltip = item.tooltip ? `data-bs-tooltip="${escape(item.tooltip)}"` : "";
    const valueTooltip = shortened ? `data-bs-tooltip="${escape(value)}"` : "";
    const classes = `info-card-value-text no-info-icon${item.useMonospace ? ' font-monospace' : ''}`;
    const content = item.href
        ? `<a class="${classes}" href="${escape(item.href)}" target="_blank" rel="noopener noreferrer" ${valueTooltip}>${escape(text)}</a>`
        : `<span class="${classes}" ${valueTooltip}>${escape(text)}</span>`;
    const icon = item.icon ? `<i class="${escape(item.icon)}"></i>` : "";
    const copy = item.addCopyBtn
        ? `<button type="button" class="btn btn-sm p-0 btn-clipboard info-card-copy no-info-icon" data-clipboard-text="${escape(value)}" data-bs-tooltip="Copy to clipboard"></button>`
        : "";
    return `
        <div class="col fs-6 d-flex align-items-baseline">
            <span class="text-body-secondary text-nowrap" ${tooltip}>${escape(item.name)}:</span>
            <span class="info-card-value ms-1 d-inline-flex align-items-center gap-1 user-select-all${shortened ? ' text-nowrap' : ' text-wrap'}">${icon}${content}${copy}</span>
        </div>`;
}
