package exceptions.gui;

import models.gui.ApiEnvelope.ErrorCode;

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

    public int getStatus() {
        return httpStatusCode;
    }

}
