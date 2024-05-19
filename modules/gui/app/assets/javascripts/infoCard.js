export { render }

function render(items) {
    const itemHtml = items.map(renderItem).join("");
    return `
        <div class="card card-body bg-body-tertiary mb-4">
            <div class="row row-cols-auto">
                ${itemHtml}
            </div>
        </div>
    `;
}

function renderItem(item) {
    const tooltip = item.tooltip ? `data-bs-tooltip="${item.tooltip}"` : "";
    return `
        <div class="col fs-6 text-truncate">
            <span ${tooltip}>${item.name}</span>:
            <span class="ms-1 fw-light user-select-all">${item.value}</span>
        </div>`;
}