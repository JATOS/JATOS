package exceptions.publix;

import org.apache.http.HttpStatus;

/**
 * It causes the request to return with an HTTP status 403 (Forbidden).
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public class ForbiddenPublixException extends PublixException {

	public ForbiddenPublixException(String message) {
		super(message, HttpStatus.SC_FORBIDDEN);
	}

}
