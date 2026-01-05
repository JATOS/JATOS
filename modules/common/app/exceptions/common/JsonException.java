package exceptions.common;

/**
 * Runtime exception for JSON de-/serialisation errors in JATOS
 */
public class JsonException extends RuntimeException {

    public JsonException(String message) {
        super(message);
    }

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }

    public JsonException(Throwable cause) {
        super(cause);
    }
}
