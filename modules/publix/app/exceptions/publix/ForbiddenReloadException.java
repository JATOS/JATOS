package exceptions.publix;

/**
 * Thrown if a JATOS component attempted a not allowed reload
 * 
 * @author Kristian Lange
 */
public class ForbiddenReloadException extends RuntimeException {

    private final String uuid;

	public ForbiddenReloadException(String uuid, String message) {
		super(message);
        this.uuid = uuid;
	}

    public String getUuid() {
        return uuid;
    }

}
