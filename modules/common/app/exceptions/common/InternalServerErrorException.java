package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

import static play.mvc.Http.Status.INTERNAL_SERVER_ERROR;

/**
 * This runtime Exception should only be thrown if something within JATOS doesn't behave like it should (there is a bug
 * in JATOS). In production mode this should not happen. It causes the request to return with HTTP status 500 (Internal
 * Server Error).
 */
public class InternalServerErrorException extends HttpException {

    public InternalServerErrorException(String message) {
        super(INTERNAL_SERVER_ERROR, message, ErrorCode.UNEXPECTED_ERROR);
    }

    public InternalServerErrorException(String message, ErrorCode errorCode) {
        super(INTERNAL_SERVER_ERROR, message, errorCode);
    }

}
