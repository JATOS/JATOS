package exceptions.common;

import java.io.Serial;
import java.util.List;
import java.util.Map;

/**
 * Validation failure with field names and messages, never submitted values.
 */
public class FieldValidationException extends ValidationException {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final Map<String, List<String>> fieldErrors;

    public FieldValidationException(String field, String message) {
        this(message, Map.of(field, List.of(message)));
    }

    public FieldValidationException(String message, Map<String, List<String>> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }

    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }
}
