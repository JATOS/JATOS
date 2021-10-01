package exceptions.publix;

import play.mvc.Http;

/**
 * It causes the request to return with an HTTP status 404 (Not Found).
 *
 * @author Kristian Lange
 */
public class NotFoundPublixException extends PublixException {

	public NotFoundPublixException(String message) {
		super(message, Http.Status.NOT_FOUND);
	}

}
