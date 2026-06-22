package services.publix.idcookie.exceptions;

import exceptions.common.JatosException;

/**
 * Signals a not existing ID cookie.
 */
public class IdCookieNotFoundException extends JatosException {

    public IdCookieNotFoundException(String message) {
        super(message);
    }

}
