package exceptions;

import org.apache.http.HttpStatus;

import com.google.common.net.MediaType;

@SuppressWarnings("serial")
public class OkPublixException extends PublixException {

	public OkPublixException(String message, MediaType mediaType) {
		super(message, mediaType, HttpStatus.SC_OK);
	}

	public OkPublixException(String message) {
		super(message, MediaType.HTML_UTF_8, HttpStatus.SC_OK);
	}

}
