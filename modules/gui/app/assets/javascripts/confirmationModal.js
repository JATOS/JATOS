/*
 * Shows a confirmation Modal. Uses Bootstrap and jQuery.
 */

export { show }

import * as Helpers from "./helpers.js";
import * as Alerts from "./alerts.js";

const alerts = new Alerts.Named("confirmation-modal");
$("#confirmationModal").on('hide.bs.modal', alerts.hideAll);

function show({
        title = "This is a title",
        text = "",
        btnText = "xxx",
        btnClass = "btn-danger",
        safetyCheck = false,
        safetyCheckClass = "form-danger",
        action }) {
    $('#confirmationModal .modal-title').text(title);
    $('#confirmationModalText').html(text);
    $('#confirmationModalSafetyCheck').toggleClass("d-none", !safetyCheck);
    $('#confirmationModalSafetyCheckbox').prop('checked', false);
    if (safetyCheckClass) $('#confirmationModalSafetyCheckbox').parent().addClass(safetyCheckClass);
    $('#confirmationModalConfirmed').text(btnText);
    $('#confirmationModalConfirmed').addClass(btnClass);
    $('#confirmationModal').modal('show');
    $('#confirmationModalConfirmed').off('click').on('click', function() {
        const safetyChecked = $('#confirmationModalSafetyCheckbox').prop('checked');
        if (safetyCheck && !safetyChecked) {
            alerts.error("You can only proceed if the checkbox is checked.", 10000);
        } else {
            $('#confirmationModal').modal('hide');
            action();
        }
    });
}