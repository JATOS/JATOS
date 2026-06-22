package exceptions.publix;

/**
 * Special exception used during study flow management. Thrown if a JATOS component attempted a not allowed reload.
 */
public class JatosComponentRunFinishedException extends RuntimeException {

    private final String uuid;

	public JatosComponentRunFinishedException(String uuid) {
		super();
        this.uuid = uuid;
	}

    public String getUuid() {
        return uuid;
    }

}
