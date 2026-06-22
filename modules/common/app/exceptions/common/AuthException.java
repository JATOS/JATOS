package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

/**
 * Runtime Exception for authentication/authorization errors in JATOS
 */
public class AuthException extends JatosException {

    public AuthException(String message) {
        super(message, ErrorCode.AUTH_ERROR);
    }

}
