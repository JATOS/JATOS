package exceptions.common;

import play.mvc.Http;

/**
 * This exception should only be thrown if something within JATOS doesn't behave like it should (there is a bug in
 * JATOS). In production mode this should not happen. It causes the request to return with an HTTP status 500 (Internal
 * Server Error).
 *
 * @author Kristian Lange
 */
public class InternalServerErrorException extends HttpException {

    public InternalServerErrorException(String message) {
        super(message, Http.Status.INTERNAL_SERVER_ERROR);
    }

}
