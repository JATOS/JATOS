package services.publix.idcookie.exceptions;

/**
 * Is thrown if the limit of JATOS ID cookies is reached.
 *
 * @author Kristian Lange
 */
public class IdCookieCollectionFullException extends RuntimeException {

    public IdCookieCollectionFullException(String message) {
        super(message);
    }

}
