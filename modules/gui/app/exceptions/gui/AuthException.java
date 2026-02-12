package exceptions.gui;

import models.gui.ApiEnvelope.ErrorCode;

public class AuthException extends JatosException {

	public AuthException(String message) {
		super(message, ErrorCode.AUTH_ERROR);
	}

}
