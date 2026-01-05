package services.publix.idcookie.exceptions;

/**
 * Thrown if an ID cookie is malformed. It is on purpose not a subclass of RuntimeException.
 */
public class IdCookieMalformedException extends Exception {

    public IdCookieMalformedException(String message) {
        super(message);
    }

}
