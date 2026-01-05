package exceptions.common;

import play.mvc.Http;

public class BadRequestException extends HttpException {

    public BadRequestException(String message) {
        super(message, Http.Status.BAD_REQUEST);
    }

}
