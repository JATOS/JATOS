package exceptions;

import org.apache.http.HttpStatus;

import com.google.common.net.MediaType;

@SuppressWarnings("serial")
public class BadRequestPublixException extends PublixException {
	
	public BadRequestPublixException(String message, MediaType mediaType) {
		super(message, mediaType, HttpStatus.SC_BAD_REQUEST);
	}
	
	public BadRequestPublixException(String message) {
		super(message, MediaType.HTML_UTF_8, HttpStatus.SC_BAD_REQUEST);
	}
	
}
