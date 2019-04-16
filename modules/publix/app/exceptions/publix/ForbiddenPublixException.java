package exceptions.publix;

import play.mvc.Http;

/**
 * It causes the request to return with an HTTP status 403 (Forbidden).
 *
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public class ForbiddenPublixException extends PublixException {

	public ForbiddenPublixException(String message) {
		super(message, Http.Status.FORBIDDEN);
	}

}
