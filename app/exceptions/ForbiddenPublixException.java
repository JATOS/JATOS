package exceptions;

import org.apache.http.HttpStatus;

import com.google.common.net.MediaType;

@SuppressWarnings("serial")
public class ForbiddenPublixException extends PublixException {

	public ForbiddenPublixException(String message, MediaType mediaType) {
		super(message, mediaType, HttpStatus.SC_FORBIDDEN);
	}

	public ForbiddenPublixException(String message) {
		super(message, MediaType.HTML_UTF_8, HttpStatus.SC_FORBIDDEN);
	}

}
