package services.publix.idcookie.exceptions;

import exceptions.common.JatosException;

import java.io.Serial;

/**
 * Is thrown if a second JATOS ID cookie with the same study result ID is tried to be
 * created.
 */
public class IdCookieAlreadyExistsException extends JatosException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IdCookieAlreadyExistsException(String message) {
        super(message);
    }

}
