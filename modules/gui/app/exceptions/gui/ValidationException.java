package exceptions.gui;

import play.data.Form;

public class ValidationException extends Exception {

    private Form<?> form;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Form<?> form) {
        super(message);
        this.form = form;
    }

    public <T> Form<T> getForm() {
        return form != null ? (Form<T>) form : null;
    }

}
