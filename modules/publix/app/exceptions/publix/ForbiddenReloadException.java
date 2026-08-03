package exceptions.publix;

import java.io.Serial;

/**
 * Special exception used during study flow management. Thrown if a JATOS component attempted a not allowed reload.
 */
public class ForbiddenReloadException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String studyUuid;

	public ForbiddenReloadException(String studyUuid, String message) {
		super(message);
        this.studyUuid = studyUuid;
	}

    public String getStudyUuid() {
        return studyUuid;
    }

}
