package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

import static general.common.ApiEnvelope.ErrorCode.UNSPECIFIED;

/**
 * Parent class of all exceptions thrown in JATOS. It is a RuntimeException. Always has an ErrorCode. Can wrap a
 * 'cause' Throwable. It is handled globally by the ErrorHandler.
 */
public class JatosException extends RuntimeException {

    private final ErrorCode errorCode;

    public JatosException(Throwable cause) {
        super(cause);
        this.errorCode = UNSPECIFIED;
    }

    public JatosException(String message) {
        super(message);
        this.errorCode = UNSPECIFIED;
    }

    public JatosException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public JatosException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = UNSPECIFIED;
    }

    public JatosException(String message, Throwable cause, ErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Runs the given supplier and wraps any thrown exception in a JatosException.
     */
    public static <T> T unchecked(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new JatosException(e);
        }
    }

    /**
     * Runs the given runnable and wraps any thrown exception in a JatosException.
     */
    public static void unchecked(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new JatosException(e);
        }
    }

}
