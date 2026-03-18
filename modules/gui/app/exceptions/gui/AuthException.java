package exceptions.gui;

import general.common.ApiEnvelope.ErrorCode;

public class AuthException extends JatosException {

	public AuthException(String message) {
		super(message, ErrorCode.AUTH_ERROR);
	}

}
