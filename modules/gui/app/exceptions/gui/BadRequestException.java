package exceptions.gui;

@SuppressWarnings("serial")
public class BadRequestException extends Exception {

	public BadRequestException(String message) {
		super(message);
	}

}
