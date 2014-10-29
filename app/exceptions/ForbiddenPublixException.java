package exceptions;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
public class ForbiddenPublixException extends PublixException {

	public ForbiddenPublixException(String message) {
		super(message, HttpStatus.SC_FORBIDDEN);
	}

}
