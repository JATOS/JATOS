export { isAllSelected }

const isAllSelected = () => {
    const dataTable = $("#resultsTable").DataTable();
    return dataTable.rows({selected: true}).count() == dataTable.rows().count();
}