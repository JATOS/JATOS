import * as Alerts from "./alerts.js";
import * as Helpers from "./helpers.js";

$("#studySidebar").on('show.bs.offcanvas', () => {
    Alerts.hideAll();
    drawStudyList();
});

$("#studySidebar").on('shown.bs.offcanvas', recoverSearch);

$("#studySidebar").on('focusout', saveSearch);

function drawStudyList() {
    $.ajax({
        url: window.routes.Home.sidebarData,
        success: function(result) {
            fillStudyList(result);
        },
        error : function(err) {
            Alerts.error(err.responseText);
        }
    });
}

function fillStudyList(studyArray) {
    $("#studySidebar .list-group").empty();
    studyArray.forEach((study) => {
        const isVisibleStudy = window.study && window.study.id === study.id ? "active" : "";
        const componentList = study.componentList.map(c => `&#10;&nbsp;• ${c.title}`).join("");
        const componentsBadge = `<span class="badge rounded-pill no-info-icon text-bg-component" data-toggle="tooltip" title="This study has ${study.componentList.length} components.${componentList}">${study.componentList.length}</span>`;
        const deactivatedBadge = !study.isActive ? `<span class="badge rounded-pill no-info-icon text-bg-danger" data-toggle="tooltip" title="This study got deactivated by an JATOS admin. A deactivated study cannot be started by participants anymore (but an already started study run can be continued). You can still see and edit this study and export its result data."><i class="bi-exclamation-triangle-fill"></i></span>` : "";
        const lockedBadge = study.isLocked ? `<span class="badge rounded-pill no-info-icon text-bg-warning" data-toggle="tooltip" title="This study is locked."><i class="bi-lock-fill"></i></span>` : "";
        const groupStudyBadge = study.isGroupStudy ? `<span class="badge rounded-pill no-info-icon text-bg-secondary" data-toggle="tooltip" title="This study is a group study."><i class="bi-people-fill"></i></span>` : "";
        const allowPreviewBadge = study.isAllowPreview ? `<span class="badge rounded-pill no-info-icon text-bg-secondary" data-toggle="tooltip" title="This study allows previews."><i class="bi-basket2-fill"></i></span>` : "";
        const linearStudyBadge = study.isLinearStudy ? `<span class="badge rounded-pill no-info-icon text-bg-secondary" data-toggle="tooltip" title="This study only allows a linear study flow."><i class="bi-forward-fill"></i></span>` : "";
        const html = `
                <a href="${window.common.jatosUrlBasePath}jatos/${study.id}" class="list-group-item no-info-icon ${isVisibleStudy}" data-toggle="tooltip" title="${study.title}&#013;ID: ${study.id}&#013;UUID: ${study.uuid}">
                    <div class="d-flex w-100 align-items-center justify-content-between">
                        <div class="fw-bold d-inline-block text-truncate">${study.title}</div>
                        <div class="d-inline-flex gap-1 text-nowrap">${componentsBadge}${deactivatedBadge}${lockedBadge}${groupStudyBadge}${allowPreviewBadge}${linearStudyBadge}</div>
                    </div>
                    <div class="mb-1 fs-6 fw-light text-truncate">ID: ${study.id} | UUID: ${study.uuid}</div>
                </a>`;
        $("#studySidebar .list-group").append(html);
    });
    Helpers.activatePopovers();
}

$("#studySearchInput").on("keyup", function() {
    const value = $(this).val().toLowerCase();
    $("#studySidebar a.list-group-item").filter(function() {
        $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)
    });
});

function recoverSearch() {
    const searchValue = localStorage.getItem("studySidebarSearch");
    document.getElementById("studySearchInput").value = searchValue;
    $('#studySearchInput').keyup();
}

function saveSearch() {
    const searchValue = document.getElementById("studySearchInput").value;
    localStorage.setItem("studySidebarSearch", searchValue);
}