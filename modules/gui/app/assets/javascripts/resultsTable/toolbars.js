export { Toolbars }

import * as Alerts from '../alerts.js';
import * as Helpers from '../helpers.js';
import * as WaitingModal from "../waitingModal.js";

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
            // Necessary - otherwise the button doesn't work with manually selected rows
            this.toggleDeselectAllButton();
            Helpers.setButtonWidthToMax("button.collapse-result-data");
        });
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
                    "titleAttr": "Reload results and refresh the table",
                    "action": function ( e, dt, node, config ) {
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
                    "titleAttr": "Export results to your local file system",
                    "className": `btn ${this.btnClass} me-2`,
                    "buttons": [
                        {
                            "text": '<span data-toggle="tooltip" title="A JATOS Results Archive (JRZIP) contains everything (metadata, result data and result files), all packed in a ZIP archive. Hence every ZIP unpacker can be used to get to the files.">JATOS Results Archive</span>',
                            "className": this.dropdownClass,
                            "action": () => this.exportResultsCallback(window.routes.Api.exportResults(false))
                        },
                        {
                            "text": '<span data-toggle="tooltip" title="Exports data only from results, as zip package or plain text file">Data only</span>',
                            "className": this.dropdownClass,
                            "extend": "collection",
                            "buttons": [
                                {
                                    "text": '<span data-toggle="tooltip" title="Exports data in a zip package. Each result\'s data has its own file within the zip.">ZIP</span>',
                                    "className": this.dropdownClass,
                                    "action": () => this.exportResultsCallback(window.routes.Api.exportResultData(false,false))
                                },
                                {
                                    "text": '<span data-toggle="tooltip" title="Exports data as one plain text file. The result\'s data are stored one after another with a line-break between them.">Plain Text</span>',
                                    "className": this.dropdownClass,
                                    "action": () => this.exportResultsCallback(window.routes.Api.exportResultData(true,false))
                                }
                            ]
                        },
                        {
                            "text": '<span data-toggle="tooltip" title="Exports files only from results, packed in a zip file">Files only</span>',
                            "className": "text-nowrap dropdown-item-study",
                            "action": () => this.exportResultsCallback(window.routes.Api.exportResultFiles)
                        },
                        {
                            "text": '<span data-toggle="tooltip" title="Exports only the metadata of the results. Choose between JSON and CSV format.">Metadata only</span>',
                            "extend": "collection",
                            "collectionLayout": "dropdown",
                            "className": "w-100 dropdown-item-study",
                            "buttons": [
                                {
                                    "text": '<span data-toggle="tooltip" title="Exports metadata in JSON format. It exports metadata of study results and their component results.">JSON</span>',
                                    "className": this.dropdownClass,
                                    "action": () => this.exportResultsCallback(window.routes.Api.exportResultMetadata(false))
                                },
                                {
                                    "text": '<span data-toggle="tooltip" title="Exports metadata in CSV format. It exports only what is currently visible in this table. Metadata of component results are not included.">CSV</span>',
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
                    "titleAttr": "Delete results in JATOS",
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
                    "titleAttr": "Select all results (including the ones on different table pages)"
                },
                {
                    "extends": "selectAll",
                    "text": "Visible",
                    "action": function(e, dt, node, config) {
                        dt.rows().deselect();
                        dt.rows({ page: 'current' }).select();
                    },
                    "titleAttr": "Select only the currently visible results on this page"
                },
                {
                    "extends": "selectAll",
                    "text": "Filtered",
                    "action": function(e, dt, node, config) {
                        dt.rows().deselect();
                        dt.rows({ search: 'applied' }).select();
                    },
                    "titleAttr": "Select only the filtered results (including the ones on different table pages)"
                },
                {
                    "extend": "selectNone",
                    "text": "Deselect",
                    "action": function(e, dt, node, config) {
                        dt.rows().deselect();
                    },
                    "titleAttr": "Deselect all results"
                }
            ]
        });
        new $.fn.dataTable.Buttons(dataTable, {
            "name": "customizeButton",
            "buttons": [
                {
                    "extend": "colvis",
                    "text": "Customize",
                    "titleAttr": "Show/hide columns of this table",
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

    listenToSelects = () => {
        this.dataTable.on('select', (e, dt, type, indexes) => {
            if (type == "row") {
                this.dataTable.rows(indexes).nodes().to$().each((index, selectedRow) => {
                    $(selectedRow).find('.select-checkbox').removeClass('btn-secondary').addClass(this.btnClass);
                    $(selectedRow).find('.select-checkbox i').removeClass('bi-square').addClass('bi-check-lg');
                });
            }
        });

        this.dataTable.on('deselect', (e, dt, type, indexes) => {
            if (type == "row") {
                this.dataTable.rows(indexes).nodes().to$().each((index, selectedRow) => {
                    $(selectedRow).find('.select-checkbox').removeClass(this.btnClass).addClass('btn-secondary');
                    $(selectedRow).find('.select-checkbox i').removeClass('bi-check-lg').addClass('bi-square');
                });
            }
        });
    }

    listenToSearch = () => {
        const searchResultsTable = () => {
            this.dataTable.search(
                $('#resultsTableSearch input').val(),
                $('#resultsTableSearch .regex').hasClass('active'),
                !$('#resultsTableSearch .regex').hasClass('active'),
                !$('#resultsTableSearch .caseSensitive').hasClass('active')
            ).draw();
        }
        $('#resultsTableSearch input').on('keyup click', searchResultsTable);
        $('#resultsTableSearch button.regex, button.caseSensitive').on('click', searchResultsTable);
    }

    toggleDeselectAllButton = () => {
        if (this.dataTable.rows('.selected').any()) {
            this.dataTable.buttons(['.buttons-select-none']).enable();
        } else {
            this.dataTable.buttons(['.buttons-select-none']).disable();
        }
    }
}