package services.publix.idcookie.exception;

/**
 * Is thrown if the max number of IdCookies is reached.
 *
 * @author Kristian Lange (2016)
 */
@SuppressWarnings("serial")
public class IdCookieCollectionFullException extends Exception {

    public IdCookieCollectionFullException(String message) {
        super(message);
    }

}
