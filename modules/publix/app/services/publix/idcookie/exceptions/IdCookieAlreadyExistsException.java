package services.publix.idcookie.exceptions;

/**
 * Is thrown if a second JATOS ID cookie with the same study result ID is tried to be
 * created.
 *
 * @author Kristian Lange
 */
public class IdCookieAlreadyExistsException extends RuntimeException {

    public IdCookieAlreadyExistsException(String message) {
        super(message);
    }

}
