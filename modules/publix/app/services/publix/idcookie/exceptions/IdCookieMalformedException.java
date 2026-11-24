package services.publix.idcookie.exceptions;

import java.io.Serial;

/**
 * Thrown if an ID cookie is malformed. It is on purpose not a subclass of RuntimeException.
 */
public class IdCookieMalformedException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public IdCookieMalformedException(String message) {
        super(message);
    }

}
