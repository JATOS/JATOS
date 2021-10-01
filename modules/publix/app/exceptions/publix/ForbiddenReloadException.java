package exceptions.publix;

/**
 * Thrown if a JATOS component is not allowed to reload but it this was
 * attempted.
 * 
 * @author Kristian Lange
 */
public class ForbiddenReloadException extends Exception {

	public ForbiddenReloadException(String message) {
		super(message);
	}

}
