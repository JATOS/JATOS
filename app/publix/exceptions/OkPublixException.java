package publix.exceptions;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
public class OkPublixException extends PublixException {

	public OkPublixException(String message) {
		super(message, HttpStatus.SC_OK);
	}

}
