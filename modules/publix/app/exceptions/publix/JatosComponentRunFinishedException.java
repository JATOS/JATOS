package exceptions.publix;

/**
 * Thrown if a JATOS component attempted a not allowed reload
 * 
 * @author Kristian Lange
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
