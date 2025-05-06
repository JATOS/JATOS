package services.publix.idcookie.exception;

/**
 * Is thrown if a second JATOS ID cookie with the same study result ID is tried to be
 * created.
 *
 * @author Kristian Lange
 */
public class IdCookieAlreadyExistsException extends Exception {

    public IdCookieAlreadyExistsException(String message) {
        super(message);
    }

}
