package exceptions.publix;

import org.apache.http.HttpStatus;

/**
 * It causes the request to return with an HTTP status 404 (Not Found).
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public class NotFoundPublixException extends PublixException {

	public NotFoundPublixException(String message) {
		super(message, HttpStatus.SC_NOT_FOUND);
	}

}
