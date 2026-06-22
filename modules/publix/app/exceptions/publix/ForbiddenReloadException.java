package exceptions.publix;

/**
 * Special exception used during study flow management. Thrown if a JATOS component attempted a not allowed reload.
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
