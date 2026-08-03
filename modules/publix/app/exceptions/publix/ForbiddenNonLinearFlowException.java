package exceptions.publix;

import java.io.Serial;

/**
 * Special exception used during study flow management. Exception that is thrown if a JATOS tries to start a component
 * in a linear study that is before the current running component
 */
public class ForbiddenNonLinearFlowException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String studyUuid;

    public ForbiddenNonLinearFlowException(String studyUuid, String message) {
        super(message);
        this.studyUuid = studyUuid;
    }

    public String getStudyUuid() {
        return studyUuid;
    }

}
