package services.gui.mcp;

import java.io.Serial;

/**
 * Tool-specific input failure that can be reported as actionable feedback to the model.
 */
public class InvalidToolInputException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Describes how the caller must correct the tool arguments.
     */
    public InvalidToolInputException(String message) {
        super(message);
    }
}
