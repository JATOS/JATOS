package exceptions.common;

import play.mvc.Http;

/**
 * It causes the request to return with an HTTP status 404 (Not Found).
 *
 * @author Kristian Lange
 */
public class NotFoundException extends HttpException {

    public NotFoundException(String message) {
        super(message, Http.Status.NOT_FOUND);
    }

}
