package exceptions.gui;

import play.data.Form;

public class ValidationException extends Exception {

    private final Form<?> form;

    public ValidationException(String message, Form<?> form) {
        super(message);
        this.form = form;
    }

    public <T> Form<T> getForm() {
        return (Form<T>) form;
    }

}
