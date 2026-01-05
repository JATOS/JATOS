package exceptions.common;

import play.mvc.Http;

/**
 * It causes the request to return with an HTTP status 403 (Forbidden).
 *
 * @author Kristian Lange
 */
public class ForbiddenException extends HttpException {

	public ForbiddenException(String message) {
		super(message, Http.Status.FORBIDDEN);
	}

}
