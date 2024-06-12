export { Toolbars }

import * as Alerts from '../alerts.js';
import * as Helpers from '../helpers.js';
import * as WaitingModal from "../waitingModal.js";

/*
 * Handles toolbars in the result pages (study, component, worker). Draws two rows (upper and lower) with buttons for
 * refresh, export, delete, select, filter, filter builder, customization of the table and page size
 */
class Toolbars {

    constructor({ dataTable, type, exportResultsCallback, deleteSelectedResultsCallback }) {
        this.dataTable = dataTable;
        this.btnClass = `btn-${type}`;
        this.dropdownClass = `dropdown-item-${type}`;
        this.exportResultsCallback = exportResultsCallback;
        this.deleteSelectedResultsCallback = deleteSelectedResultsCallback;
    }

    generate = () => {
        const dataTable = this.dataTable;

        this.generateUpper();
        this.generateLower();
        this.listenToSelects();
        this.listenToSearch();

        this.dataTable.on('draw', () => {
            this.drawAllSelectCheckboxes();
            // Necessary - otherwise the button doesn't work with manually selected rows
            this.toggleDeselectAllButton();
        });

        Helpers.activateTooltipsOnDataTablesDropdowns(this.dataTable);
    }

    // Upper toolbar contains refresh, export, and delete button
    generateUpper = () => {
        const dataTable = this.dataTable;
        new $.fn.dataTable.Buttons(dataTable, {
            "name": "refreshButton",
            "buttons": [
                {
                    "text": '<i class="bi-arrow-repeat"></i>',
                    "className": `btn ${this.btnClass} me-2`,
                    "attr": {
                        "data-bs-tooltip": "Reload results and refresh the table"
                    },
                    "action": function ( e, dt, node, config ) {
                        $(node).tooltip("dispose");
                        this.disable();
                        dataTable.ajax.reload();
                        setTimeout(this.enable, 3000);
                    }
                }
            ]
        });
        new $.fn.dataTable.Buttons(dataTable, {
            "name": "exportButtonGroup",
            "buttons": [
                {
                    "extend": "collection",
                    "text": '<i class="bi-box-arrow-up-right pe-1"></i>Export Results',
                    "attr": {
                        "data-bs-tooltip": "Export results to your local file system"
                    },
                    "className": `btn ${this.btnClass} me-2`,
                    "buttons": [
                        {
                            "text": '<span data-bs-tooltip="A JATOS Results Archive (JRZIP) contains everything (metadata, result data and result files), all packed in a ZIP archive. Hence every ZIP unpacker can be used to get to the files.">JATOS Results Archive</span>',
                            "className": this.dropdownClass,
                            "action": () => this.exportResultsCallback(window.routes.Api.exportResults(false), "jatos_results_" + Helpers.getDateTimeYYYYMMDDHHmmss() + ".jrzip")
                        },
                        {
                            "text": '<span data-bs-tooltip="Exports data only from results, as zip package or plain text file">Data only</span>',
                            "className": this.dropdownClass,
                            "extend": "collection",
                            "buttons": [
                                {
                                    "text": '<span data-bs-tooltip="Exports data in a zip package. Each result\'s data has its own file within the zip.">ZIP</span>',
                                    "className": this.dropdownClass,
                                    "action": () => this.exportResultsCallback(window.routes.Api.exportResultData(false,false), "jatos_results_data_" + Helpers.getDateTimeYYYYMMDDHHmmss() + ".zip")
                                },
                                {
                                    "text": '<span data-bs-tooltip="Exports data as one plain text file. The result\'s data are stored one after another with a line-break between them.">Plain Text</span>',
                                    "className": this.dropdownClass,
                                    "action": () => this.exportResultsCallback(window.routes.Api.exportResultData(true,false), "jatos_results_data_" + Helpers.getDateTimeYYYYMMDDHHmmss() + ".txt")
                                }
                            ]
                        },
                        {
                            "text": '<span data-bs-tooltip="Exports files only from results, packed in a zip file">Files only</span>',
                            "className": "text-nowrap dropdown-item-study",
                            "action": () => this.exportResultsCallback(window.routes.Api.exportResultFiles, "jatos_results_files_" + Helpers.getDateTimeYYYYMMDDHHmmss() + ".zip")
                        },
                        {
                            "text": '<span data-bs-tooltip="Exports only the metadata of the results. Choose between JSON and CSV format.">Metadata only</span>',
                            "extend": "collection",
                            "collectionLayout": "dropdown",
                            "className": "w-100 dropdown-item-study",
                            "buttons": [
                                {
                                    "text": '<span data-bs-tooltip="Exports metadata in JSON format. It exports metadata of study results and their component results.">JSON</span>',
                                    "className": this.dropdownClass,
                                    "action": () => this.exportResultsCallback(window.routes.Api.exportResultMetadata(false), "jatos_results_metadata_" + Helpers.getDateTimeYYYYMMDDHHmmss() + ".json")
                                },
                                {
                                    "text": '<span data-bs-tooltip="Exports metadata in CSV format. It exports only what is currently visible in this table. Metadata of component results are not included.">CSV</span>',
                                    "extend": "csv",
                                    "filename": () => "jatos_results_metadata_" + Helpers.getDateTimeYYYYMMDDHHmmss(),
                                    "footer": false,
                                    "className": this.dropdownClass,
                                    "exportOptions": {
                                        "columns": ":nth-child(n+3):visible",
                                        "format": {
                                            "header": function ( data, row, column, node ) {
                                                // This is a hack to remove HTML tags
                                                return $('<div></div>').append(data).text();
                                            }
                                        },
                                        "modifier": {
                                            "selected": true
                                        }
                                    },
                                    "action": (e, dt, button, config, cb) => {
                                        // https://datatables.net/reference/option/buttons.buttons.action
                                        // https://datatables.net/forums/discussion/33209/run-script-before-exporting-begins-buttons-plugin
                                        $(".dt-button-collection").hide();
                                        if (dataTable.rows('.selected').nodes().length == 0) {
                                            Alerts.error("No results selected", 5000);
                                            return;
                                        }
                                        WaitingModal.show();
                                        setTimeout(() => {
                                            $.fn.dataTable.ext.buttons.csvHtml5.action(e, dt, button, config, cb);
                                            WaitingModal.hide();
                                        }, 1000);
                                    }
                                }
                            ]
                        }
                    ]
                }
            ]
        });

        new $.fn.dataTable.Buttons(dataTable, {
            "name": "deleteButton",
            "buttons": [
                {
                    "text": '<i class="bi-x-circle-fill pe-1"></i>Delete',
                    "attr": {
                        "data-bs-tooltip": "Delete results in JATOS"
                    },
                    "className": `btn ${this.btnClass} text-nowrap`,
                    "action": this.deleteSelectedResultsCallback
                }
            ]
        });

        dataTable.buttons('refreshButton', null).containers()
            .attr('id', 'resultsTableRefresh')
            .appendTo('#resultsTableUpperToolbar>div');
        dataTable.buttons('exportButtonGroup', null).containers()
            .attr('id', 'resultsTableExport')
            .appendTo('#resultsTableUpperToolbar>div');
        dataTable.buttons('deleteButton', null).containers()
            .attr('id', 'resultsTableDelete')
            .appendTo('#resultsTableUpperToolbar>div');
        $('#resultTableToolbars').prependTo('#resultsTable_wrapper');
    }

    // Lower toolbar contains select buttons, filter/search, SearchBuilder, customization button, and table length
    generateLower = () => {
        const dataTable = this.dataTable;
        new $.fn.dataTable.Buttons(dataTable, {
            "name": "selectButtonGroup",
            "buttons": [
                {
                    "extend": "selectAll",
                    "text": "All",
                    "attr": {
                        "data-bs-tooltip": "Select all results (including the ones on different table pages)"
                    }
                },
                {
                    "extends": "selectAll",
                    "text": "Visible",
                    "attr": {
                        "data-bs-tooltip": "Select only the currently visible results on this page"
                    },
                    "action": (e, dt, node, config) => {
                        $(node).tooltip("dispose");
                        dt.rows().deselect();
                        dt.rows({ page: 'current' }).select();
                    },
                },
                {
                    "extends": "selectAll",
                    "text": "Filtered",
                    "attr": {
                        "data-bs-tooltip": "Select only the filtered results (including the ones on different table pages)"
                    },
                    "action": (e, dt, node, config) => {
                        $(node).tooltip("dispose");
                        dt.rows().deselect();
                        dt.rows({ search: 'applied' }).select();
                    },
                },
                {
                    "extend": "selectNone",
                    "text": "Deselect",
                    "attr": {
                        "data-bs-tooltip": "Deselect all results"
                    },
                    "action": (e, dt, node, config) => {
                        $(node).tooltip("dispose");
                        dt.rows().deselect();
                    },
                }
            ]
        });
        new $.fn.dataTable.Buttons(dataTable, {
            "name": "customizeButton",
            "buttons": [
                {
                    "extend": "colvis",
                    "text": "Customize",
                    "attr": {
                        "data-bs-tooltip": "Show/hide columns of this table"
                    },
                    "columns": ":not(.no-colvis)"
                }
            ]
        });

        $('#resultsTableUpperToolbar').after($('#resultsTableLowerToolbar'));

        // Select buttons
        dataTable.buttons('selectButtonGroup', null).containers()
                .removeClass("flex-wrap")
                .addClass("mb-0")
                .appendTo('#resultsTableSelect');

        // Filter - swap dataTables filter field with our own
        $(".dt-search").remove();

        // Search Builder
        $("#resultsTableLowerToolbar").after(dataTable.searchBuilder.container());
        $(".dtsb-searchBuilder").addClass("collapse card card-body ms-2 my-2");

        // Customize button
        dataTable.buttons('customizeButton', null).containers().appendTo('#resultsTableCustomize');
        $('#resultsTableCustomize>div').addClass("mb-0 me-2");

        // Table length select
        $(".dt-length select").removeClass("form-select-sm");
        $(".dt-length label").remove();
        $('.dt-length').insertAfter('#resultsTableCustomize');
    }

    drawAllSelectCheckboxes = () => {
        this.dataTable.rows().iterator('row', (context, index) => {
            this.drawSelectCheckbox(this.dataTable.row(index).node());
        });
    }

    drawSelectCheckbox = (row) => {
        const isSelected = $(row).hasClass("selected");
        $(row).find('.select-checkbox')
            .toggleClass('btn-secondary', !isSelected)
            .toggleClass(this.btnClass, isSelected);
        $(row).find('.select-checkbox i')
            .toggleClass('bi-square', !isSelected)
            .toggleClass('bi-check-lg', isSelected);
    }

    listenToSelects = () => {
        this.dataTable.on('select deselect', (e, dt, type, indexes) => {
            if (type == "row") {
                this.dataTable.rows(indexes).nodes().to$().each((index, selectedRow) => {
                    this.drawSelectCheckbox(selectedRow);
                });
                this.toggleDeselectAllButton();
            }
        });
    }

    listenToSearch = () => {
        // Firefox doesn't empty search field on page reload, so we do it manually
        $('#resultsTableSearch input').val("");

        // More about DataTables search: https://datatables.net/reference/api/search()
        const searchCallback = () => {
            const input = $('#resultsTableSearch input').val();
            const isRegex = $('#resultsTableSearch .regex').hasClass('active');
            const isCaseSensitive = $('#resultsTableSearch .case-sensitive').hasClass('active');
            this.dataTable.search(input, isRegex, !isRegex, !isCaseSensitive).draw();
        };
        $('#resultsTableSearch input').on('keyup click', searchCallback);
        $('#resultsTableSearch button.regex, button.case-sensitive').on('click', searchCallback);
    }

    toggleDeselectAllButton = () => {
        if (this.dataTable.rows('.selected').any()) {
            this.dataTable.buttons(['.buttons-select-none']).enable();
        } else {
            this.dataTable.buttons(['.buttons-select-none']).disable();
        }
    }
}