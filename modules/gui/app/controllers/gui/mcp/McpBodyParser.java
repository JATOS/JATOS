package controllers.gui.mcp;

import play.http.HttpErrorHandler;
import general.common.Common;
import play.mvc.BodyParser;

import javax.inject.Inject;

/**
 * Bound MCP messages independently of the much larger study upload limits.
 */
public class McpBodyParser extends BodyParser.TolerantText {

    /**
     * Bounds incoming MCP message bodies using the configured limit and Play's standard error handling.
     */
    @Inject
    public McpBodyParser(HttpErrorHandler errorHandler) {
        super(Common.getJatosMcpMaxRequestSize(), errorHandler);
    }
}
