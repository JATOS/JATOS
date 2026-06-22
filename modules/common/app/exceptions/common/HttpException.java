package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

/**
 * Runtime Exception that causes a request to return with an HTTP return status.
 */
public class HttpException extends JatosException {

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
