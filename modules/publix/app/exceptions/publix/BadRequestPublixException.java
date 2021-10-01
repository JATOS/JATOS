package exceptions.publix;

import play.mvc.Http;

/**
 * It causes the request to return with an HTTP status 400 (Bad Request).
 * 
 * @author Kristian Lange
 */
public class BadRequestPublixException extends PublixException {

	public BadRequestPublixException(String message) {
		super(message, Http.Status.BAD_REQUEST);
	}

}
