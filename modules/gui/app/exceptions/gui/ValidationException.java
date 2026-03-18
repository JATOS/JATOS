package exceptions.gui;

import general.common.ApiEnvelope.ErrorCode;
import play.data.Form;

public class ValidationException extends JatosException {

    private Form<?> form;

    public ValidationException(String message) {
        super(message, ErrorCode.VALIDATION_ERROR);
    }

    public ValidationException(String message, Form<?> form) {
        super(message, ErrorCode.VALIDATION_ERROR);
        this.form = form;
    }

    public <T> Form<T> getForm() {
        return form != null ? (Form<T>) form : null;
    }

}
