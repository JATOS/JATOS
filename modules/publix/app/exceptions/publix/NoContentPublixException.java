package exceptions.publix;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
public class NoContentPublixException extends PublixException {

	public NoContentPublixException(String message) {
		super(message, HttpStatus.SC_NO_CONTENT);
	}

}
