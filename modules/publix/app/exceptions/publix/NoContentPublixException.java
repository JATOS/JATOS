package exceptions.publix;

import org.apache.http.HttpStatus;

/**
 * It causes the request to return with an HTTP status 204 (No Content).
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public class NoContentPublixException extends PublixException {

	public NoContentPublixException(String message) {
		super(message, HttpStatus.SC_NO_CONTENT);
	}

}
