package exceptions.publix;

import org.apache.http.HttpStatus;

/**
 * This exception should only be thrown if something within JATOS doesn't behave
 * like it should (there is a bug in JATOS). In production mode this should not
 * happen. It causes the request to return with an HTTP status 500 (Internal
 * Server Error).
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public class InternalServerErrorPublixException extends PublixException {

	public InternalServerErrorPublixException(String message) {
		super(message, HttpStatus.SC_INTERNAL_SERVER_ERROR);
	}

}
