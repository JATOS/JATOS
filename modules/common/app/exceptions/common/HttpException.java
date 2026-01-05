package exceptions.common;

import play.mvc.Http;

/**
 * Causes a request to return with a HTTP return type that is defined in the
 * child classes of this abstract class.
 *
 * @author Kristian Lange
 */
public class HttpException extends RuntimeException {

    private int httpStatus = Http.Status.BAD_REQUEST; // default is BadRequest

    public HttpException(String message) {
        super(message);
    }

    public HttpException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
