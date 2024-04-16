export { isAllSelected }

import * as Alerts from '../alerts.js';
import * as Helpers from '../helpers.js';

const isAllSelected = () => {
    const dataTable = $("#resultsTable").DataTable();
    return dataTable.rows({selected: true}).count() == dataTable.rows().count();
}