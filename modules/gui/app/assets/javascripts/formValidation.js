export { clear, show }

function clear(form) {
    $(`${form} input`).removeClass("is-valid").removeClass("is-invalid");
    $(`${form} .invalid-feedback`).remove();
}

function show(form, errors) {
    clear(form);
    Object.keys(errors).forEach(function(name) {
        errors[name].forEach(function(error) {
            const inputElement = $(`${form} [name="${name}"]`);
            if (inputElement.parents('.input-group').length > 0) {
                inputElement.addClass("is-invalid");
                inputElement.parents('.input-group').append(`<div class="invalid-feedback">${error}</div>`);
            } else {
                inputElement.addClass("is-invalid");
                inputElement.after(`<div class="invalid-feedback">${error}</div>`);
            }
        });
    });
}