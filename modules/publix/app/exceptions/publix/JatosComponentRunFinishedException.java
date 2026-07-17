package exceptions.publix;

/**
 * Special exception used during study flow management. Thrown if a JATOS component attempted a not allowed reload.
 */
public class JatosComponentRunFinishedException extends RuntimeException {

    private final String studyUuid;

	public JatosComponentRunFinishedException(String studyUuid) {
		super();
        this.studyUuid = studyUuid;
	}

    public String getStudyUuid() {
        return studyUuid;
    }

}
