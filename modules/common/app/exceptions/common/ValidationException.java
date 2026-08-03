package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

import java.io.Serial;

/**
 * This exception is used where Play's Form validation with {@link play.data.validation.ValidationError} can't be used,
 * e.g., during authentication when there is no form.
 */
public class ValidationException extends JatosException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message, ErrorCode.VALIDATION_ERROR);
    }

}
