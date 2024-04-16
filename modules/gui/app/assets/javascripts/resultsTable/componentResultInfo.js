export { ComponentResultInfo }

import * as Alerts from '../alerts.js';
import * as Helpers from '../helpers.js';
import * as WaitingModal from "../waitingModal.js";

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
                const childRow = this.#generateComponentResultsChildRow(results, studyResultId);
                // We first show the DataTables row and then de-collapse with Bootstrap
                row.child(childRow).show(); // Let DataTables add the child row. But it's still collapsed.
                tr.next().addClass('info').find("td:first").addClass("p-0");
                const collapseElement = row.child().find(".collapse");
                new bootstrap.Collapse(collapseElement).show();
            }).fail(function(err) {
                Alerts.error(err.responseText);
            });
        }
    }

    #generateComponentResultsChildRow = (componentResults, studyResultId) => {
        // We cannot collapse a "tr" element directly. Therefore, we add an extra "div".
        const childRow = $(`
            <div class="d-flex flex-row collapse">
                <div class="title d-flex justify-content-end text-nowrap p-3 pb-5">Component Results</div>
                <table class="table table-borderless m-0">
                    <thead>
                        <tr>
                            <th data-toggle="tooltip" title="The ID of the component result">Comp. Result ID</th>
                            <th data-toggle="tooltip" title="The ID of the component">Comp. ID</th>
                            <th data-toggle="tooltip" title="The title of the component">Component Title</th>
                            <th data-toggle="tooltip" title="The start time of the component run (in local time)">Start Time</th>
                            <th data-toggle="tooltip" title="The duration from start to end. Format is [days:]hours:minutes:seconds.">Duration</th>
                            <th data-toggle="tooltip" title="The current state of the component run, like @common.ComponentResult.ComponentState.allStatesAsString()">State</th>
                            <th data-toggle="tooltip" title="The size of the result data">Data Size</th>
                            <th data-toggle="tooltip" title="The files (file size in brackets) that were uploaded during the run of the component">Files (Size)</th>
                            <th data-toggle="tooltip" title="The message that was send (optionally) in the end of the component run">Message</th>
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

            let showAllButton = "";
            // If the last three chars of the dataShort field are "..." add the show-all button
            if (componentResult.dataShort.substr(componentResult.dataShort.length - 3) == "...") {
                showAllButton = '<button type="button" class="btn btn-nav btn-sm show-all" data-toggle="tooltip" title="Show all result data of this component result.">Show All</button>';
            }
            const resultDataDiv = `
                <div class="card mx-4 mb-2">
                    <div class="card-header">
                        Data
                    </div>
                    <div class="card-body">
                        <pre class="text-wrap text-break m-0">${componentResult.dataShort}${showAllButton}</pre>
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
                        <td>${Helpers.getLocalTime(componentResult.startDate)}</td>
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
            row.data(componentResult);
            childRow.find("table").append(row);
        });

        return childRow;
    }
}