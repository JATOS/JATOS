package exceptions;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
public class NotFoundPublixException extends PublixException {

	public NotFoundPublixException(String message) {
		super(message, HttpStatus.SC_NOT_FOUND);
	}

}
