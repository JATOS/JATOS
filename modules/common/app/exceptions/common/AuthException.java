package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

import java.io.Serial;

/**
 * Runtime Exception for authentication/authorization errors in JATOS
 */
public class AuthException extends JatosException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthException(String message) {
        super(message, ErrorCode.AUTH_ERROR);
    }

}
