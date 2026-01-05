package exceptions.common;

/**
 * Base runtime exception for I/O-related errors in JATOS
 */
public class IOException extends RuntimeException {

    public IOException(String message) {
        super(message);
    }

    public IOException(String message, Throwable cause) {
        super(message, cause);
    }

    public IOException(Throwable cause) {
        super(cause);
    }
}
