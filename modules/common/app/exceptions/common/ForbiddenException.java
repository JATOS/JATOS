package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

import java.io.Serial;

import static play.mvc.Http.Status.FORBIDDEN;

/**
 * Runtime Exception that causes the request to return with an HTTP status 403 (Forbidden).
 */
public class ForbiddenException extends HttpException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(FORBIDDEN, message, ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message, ErrorCode code) {
        super(FORBIDDEN, message, code);
    }

}
