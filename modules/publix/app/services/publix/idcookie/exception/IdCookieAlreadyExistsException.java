package services.publix.idcookie.exception;

/**
 * Is thrown if a second IdCookie with the same study result ID is tried to be
 * created.
 * 
 * @author Kristian Lange (2016)
 */
@SuppressWarnings("serial")
public class IdCookieAlreadyExistsException extends Exception {

	public IdCookieAlreadyExistsException(String message) {
		super(message);
	}

}
