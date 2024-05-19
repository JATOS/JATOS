export { ComponentResultInfo, getResultDataShortHtml }

import * as Alerts from '../alerts.js';
import * as Helpers from '../helpers.js';
import * as WaitingModal from "../waitingModal.js";
import * as CopyToClipboard from "../copyToClipboard.js";

class ComponentResultInfo {
    constructor(dataTable) {
        this.dataTable = dataTable;
    }

    generate = (tr) => {
        const row = this.dataTable.row(tr);
        const studyResultId = row.data().id;
        if (row.child.isShown()) {
            const collapseElement = row.child().find(".collapse");
            // First we collapse with Bootstrap and then we hide the DataTable row
            collapseElement.off('hidden.bs.collapse').on('hidden.bs.collapse', row.child.hide);
            new bootstrap.Collapse(collapseElement).hide();
        } else {
            $.ajax({
                url : window.routes.StudyResults.tableDataComponentResultsByStudyResult(studyResultId),
                contentType: "application/json; charset=utf-8"
            }).done((results) => {
                const childRow = this.generateComponentResultsChildRow(results, studyResultId);
                // We first show the DataTables row and then de-collapse with Bootstrap
                row.child(childRow).show(); // Let DataTables add the child row. But it's still collapsed.
                tr.next().addClass('info').find("td:first").addClass("p-0");
                const collapseElement = row.child().find(".collapse");
                new bootstrap.Collapse(collapseElement).show();
                Helpers.activateTooltips(collapseElement);
            }).fail(function(err) {
                const errMsg = err.responseText ? err.responseText : "Couldn't get component result data";
                Alerts.error(errMsg);
            });
        }
    }

    generateComponentResultsChildRow = (componentResults, studyResultId) => {
        // We cannot collapse a "tr" element directly. Therefore, we add an extra "div".
        const childRow = $(`
            <div class="d-flex flex-row collapse">
                <div class="title d-flex justify-content-end text-nowrap p-3 pb-5">Component Results</div>
                <table class="table table-borderless m-0">
                    <thead>
                        <tr>
                            <th data-bs-tooltip="The ID of the component result">Comp. Result ID</th>
                            <th data-bs-tooltip="The ID of the component">Comp. ID</th>
                            <th data-bs-tooltip="The title of the component">Component Title</th>
                            <th data-bs-tooltip="The start time of the component run (in local time)">Start Time</th>
                            <th data-bs-tooltip="The duration from start to end. Format is [days:]hours:minutes:seconds.">Duration</th>
                            <th data-bs-tooltip="The current state of the component run">State</th>
                            <th data-bs-tooltip="The size of the result data">Data Size</th>
                            <th data-bs-tooltip="The files (file size in brackets) that were uploaded during the run of the component">Files (Size)</th>
                            <th data-bs-tooltip="The message that was send (optionally) in the end of the component run">Message</th>
                        </tr>
                    </thead>
                </table>
            </div>
        `);

        if (componentResults.length == 0) {
            const row = $('<tbody><tr class="info"><td colspan="9">empty</td></tr></tbody>');
            childRow.find("thead").after(row);
            return childRow;
        }

        componentResults.forEach(function(componentResult) {
            const duration = componentResult.duration ? componentResult.duration : '<span class="text-body text-opacity-50">not yet</span>';
            const message = componentResult.message ? componentResult.message : '<span class="text-body text-opacity-50">none</span>';
            const resultFiles = componentResult.files.map(function(fileObj) {
                const url = window.routes.Api.exportSingleResultFile(componentResult.id, fileObj.filename);
                return `<a class="text-nowrap" href="${url}" download>${fileObj.filename} (${fileObj.sizeHumanReadable})</a>`;
            });
            if (resultFiles.length === 0) resultFiles.push('<span class="text-body text-opacity-50">none</span>');

            const resultDataDiv = `
                <div class="card mx-4 mb-2">
                    <div class="card-header">
                        Data
                    </div>
                    <div class="card-body">
                        ${getResultDataShortHtml(componentResult)}
                    </div>
                </div>
            `;

            // One component result actually spreads over two rows inside a <tbody>. This way we can style the <tbody> element.
            const row = $(`
                <tbody id="componentResultInfo-${componentResult.id}">
                    <tr>
                        <td class="ps-4">${componentResult.id}</td>
                        <td>${componentResult.componentId}</td>
                        <td>${componentResult.componentTitle}</td>
                        <td>${Helpers.getLocalTimeDataTables(componentResult.startDate)}</td>
                        <td>${duration}</td>
                        <td>${componentResult.componentState}</td>
                        <td>${componentResult.dataSizeHumanReadable}</td>
                        <td>${resultFiles.join('<br>')}</td>
                        <td>${message}</td>
                    </tr>
                    <tr>
                        <td class="p-0" colspan="9">${resultDataDiv}</td>
                    </tr>
                </tbody>
            `);
            row.on('click', '.btn-clipboard', CopyToClipboard.onClick);
            row.data(componentResult);
            childRow.find("table").append(row);
        });

        return childRow;
    }
}

function getResultDataShortHtml(componentResult) {
    const dataShort = componentResult.dataShort.length != 0 ? componentResult.dataShort : "no data";

    // If the last three chars of the dataShort field are "..." add the show-all button and hide the copy-to-clipboard button
    const isTooLong = componentResult.dataShort.substr(componentResult.dataShort.length - 3) === "...";
    const showAllButton = isTooLong ? '<button type="button" class="btn btn-nav btn-xs show-all ms-2" data-bs-tooltip="Show all result data of this component result.">Show All</button>' : "";
    const copyToClipboardButton = !isTooLong ? '<span class="btn-clipboard btn-clipboard-top-right no-info-icon" data-bs-tooltip="Copy to clipboard"></span>' : "";

    return `<pre class="d-inline m-0"><code class="text-wrap text-break">${dataShort}</code></pre>${showAllButton}${copyToClipboardButton}`;
}