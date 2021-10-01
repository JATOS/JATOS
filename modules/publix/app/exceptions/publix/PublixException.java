package exceptions.publix;

import play.mvc.Http;

/**
 * Causes a request to return with a HTTP return type that is defined in the
 * child classes of this abstract class.
 *
 * @author Kristian Lange
 */
public abstract class PublixException extends Exception {

    private int httpStatus = Http.Status.BAD_REQUEST; // default is BadRequest

    public PublixException(String message) {
        super(message);
    }

    public PublixException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

}
