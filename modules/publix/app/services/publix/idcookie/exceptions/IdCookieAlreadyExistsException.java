package services.publix.idcookie.exceptions;

import exceptions.common.JatosException;

/**
 * Is thrown if a second JATOS ID cookie with the same study result ID is tried to be
 * created.
 */
public class IdCookieAlreadyExistsException extends JatosException {

    public IdCookieAlreadyExistsException(String message) {
        super(message);
    }

}
