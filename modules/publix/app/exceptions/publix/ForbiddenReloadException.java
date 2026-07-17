package exceptions.publix;

/**
 * Special exception used during study flow management. Thrown if a JATOS component attempted a not allowed reload.
 */
public class ForbiddenReloadException extends RuntimeException {

    private final String studyUuid;

	public ForbiddenReloadException(String studyUuid, String message) {
		super(message);
        this.studyUuid = studyUuid;
	}

    public String getStudyUuid() {
        return studyUuid;
    }

}
