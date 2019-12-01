package exceptions.publix;

/**
 * Thrown if a JATOS tries to start a component in a linear study that is before the current running component
 *
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public class ForbiddenNonLinearFlowException extends Exception {

	public ForbiddenNonLinearFlowException(String message) {
		super(message);
	}

}
