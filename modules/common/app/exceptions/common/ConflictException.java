package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

import java.io.Serial;

import static play.mvc.Http.Status.CONFLICT;

/**
 * Runtime Exception that causes the request to return with HTTP status 409 (Conflict).
 */
public class ConflictException extends HttpException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(CONFLICT, message);
    }

    public ConflictException(String message, ErrorCode errorCode) {
        super(CONFLICT, message, errorCode);
    }

}
