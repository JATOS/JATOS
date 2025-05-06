package services.publix.idcookie.exception;

/**
 * Is thrown if the limit of JATOS ID cookies is reached.
 *
 * @author Kristian Lange
 */
public class IdCookieCollectionFullException extends Exception {

    public IdCookieCollectionFullException(String message) {
        super(message);
    }

}
