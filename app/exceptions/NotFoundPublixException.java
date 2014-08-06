package exceptions;

import org.apache.http.HttpStatus;

import com.google.common.net.MediaType;

@SuppressWarnings("serial")
public class NotFoundPublixException extends PublixException {

	public NotFoundPublixException(String message, MediaType mediaType) {
		super(message, mediaType, HttpStatus.SC_NOT_FOUND);
	}

	public NotFoundPublixException(String message) {
		super(message, MediaType.HTML_UTF_8, HttpStatus.SC_NOT_FOUND);
	}

}
