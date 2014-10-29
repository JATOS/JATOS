package exceptions;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
public class UnsupportedMediaTypePublixException extends PublixException {

	public UnsupportedMediaTypePublixException(String message) {
		super(message, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
	}

}
