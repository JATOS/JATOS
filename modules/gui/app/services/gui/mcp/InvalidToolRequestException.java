package services.gui.mcp;

import java.io.Serial;

/**
 * A malformed tool request, distinct from invalid inputs to a known tool.
 */
public class InvalidToolRequestException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Describes the malformed request without exposing backend details.
     */
    public InvalidToolRequestException(String message) {
        super(message);
    }
}
