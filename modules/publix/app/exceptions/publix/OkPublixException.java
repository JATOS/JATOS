package exceptions.publix;

import org.apache.http.HttpStatus;

/**
 * It causes the request to return with an HTTP status 200 (OK).
 * 
 * @author Kristian Lange
 *
 */
@SuppressWarnings("serial")
public class OkPublixException extends PublixException {

	public OkPublixException(String message) {
		super(message, HttpStatus.SC_OK);
	}

}
