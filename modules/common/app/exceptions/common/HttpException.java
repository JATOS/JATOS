package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

import java.io.Serial;

/**
 * Runtime Exception that causes a request to return with an HTTP return status.
 */
public class HttpException extends JatosException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected int httpStatusCode;

    public HttpException(int status, String message) {
        super(message);
        this.httpStatusCode = status;
    }

    public HttpException(int status, String message, ErrorCode errorCode) {
        super(message, errorCode);
        this.httpStatusCode = status;
    }

    public int getHttpStatus() {
        return httpStatusCode;
    }
}
