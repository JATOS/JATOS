package exceptions.publix;

import org.apache.http.HttpStatus;

/**
 * It causes the request to return with an HTTP status 415 (Unsupported Media
 * Type).
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public class UnsupportedMediaTypePublixException extends PublixException {

	public UnsupportedMediaTypePublixException(String message) {
		super(message, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
	}

}
