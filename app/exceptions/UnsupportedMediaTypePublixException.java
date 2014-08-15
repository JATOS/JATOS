package exceptions;

import org.apache.http.HttpStatus;

import com.google.common.net.MediaType;

@SuppressWarnings("serial")
public class UnsupportedMediaTypePublixException extends PublixException {

	public UnsupportedMediaTypePublixException(String message,
			MediaType mediaType) {
		super(message, mediaType, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
	}

	public UnsupportedMediaTypePublixException(String message) {
		super(message, MediaType.HTML_UTF_8,
				HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
	}

}
