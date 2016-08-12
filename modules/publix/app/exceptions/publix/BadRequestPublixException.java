package exceptions.publix;

import org.apache.http.HttpStatus;

/**
 * It causes the request to return with an HTTP status 400 (Bad Request).
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public class BadRequestPublixException extends PublixException {

	public BadRequestPublixException(String message) {
		super(message, HttpStatus.SC_BAD_REQUEST);
	}

}
