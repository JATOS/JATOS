package publix.exceptions;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
public class BadRequestPublixException extends PublixException {
	
	public BadRequestPublixException(String message) {
		super(message, HttpStatus.SC_BAD_REQUEST);
	}
	
}
