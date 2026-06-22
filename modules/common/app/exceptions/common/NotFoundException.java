package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

import static play.mvc.Http.Status.NOT_FOUND;

/**
 * Runtime Exception that causes the request to return with HTTP status 404 (Not Found).
 */
public class NotFoundException extends HttpException {

    public NotFoundException(String message) {
        super(NOT_FOUND, message, ErrorCode.NOT_FOUND);
    }

    public NotFoundException(String message, ErrorCode errorCode) {
        super(NOT_FOUND, message, errorCode);
    }

}
