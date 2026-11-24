package services.publix.idcookie.exceptions;

import exceptions.common.JatosException;

import java.io.Serial;

/**
 * Signals a not existing ID cookie.
 */
public class IdCookieNotFoundException extends JatosException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IdCookieNotFoundException(String message) {
        super(message);
    }

}
