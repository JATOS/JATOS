package exceptions.gui;

import general.common.ApiEnvelope.ErrorCode;

import static play.mvc.Http.Status.FORBIDDEN;

public class ForbiddenException extends HttpException {

    public ForbiddenException(String message) {
        super(FORBIDDEN, message, ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message, ErrorCode code) {
        super(FORBIDDEN, message, code);
    }

}
