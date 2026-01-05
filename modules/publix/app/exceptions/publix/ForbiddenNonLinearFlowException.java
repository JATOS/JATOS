package exceptions.publix;

/**
 * Thrown if a JATOS tries to start a component in a linear study that is before the current running component
 *
 * @author Kristian Lange
 */
public class ForbiddenNonLinearFlowException extends RuntimeException {

    private final String uuid;

	public ForbiddenNonLinearFlowException(String uuid, String message) {
		super(message);
        this.uuid = uuid;
	}

    public String getUuid() {
        return uuid;
    }

}
