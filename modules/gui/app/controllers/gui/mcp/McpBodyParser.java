package controllers.gui.mcp;

import play.http.HttpErrorHandler;
import play.mvc.BodyParser;

import javax.inject.Inject;

/**
 * Bound MCP messages independently of the much larger study upload limits.
 */
public class McpBodyParser extends BodyParser.TolerantText {

    /**
     * Limits incoming MCP message bodies to 64 KiB and uses Play's standard error handling.
     */
    @Inject
    public McpBodyParser(HttpErrorHandler errorHandler) {
        super(65536, errorHandler);
    }
}
