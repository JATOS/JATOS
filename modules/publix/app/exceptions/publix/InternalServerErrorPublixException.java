package exceptions.publix;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
public class InternalServerErrorPublixException extends PublixException {

	public InternalServerErrorPublixException(String message) {
		super(message, HttpStatus.SC_INTERNAL_SERVER_ERROR);
	}

}
