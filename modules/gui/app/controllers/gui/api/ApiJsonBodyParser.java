package controllers.gui.api;

import general.common.Common;
import play.api.mvc.PlayBodyParsers;
import play.mvc.BodyParser;

import javax.inject.Inject;

/**
 * Bounds raw JSON requests before the API performs its own JSON validation.
 */
public class ApiJsonBodyParser extends BodyParser.Raw {

    /**
     * Rejects oversized JSON instead of spilling it to disk and reading it back into memory.
     */
    @Inject
    public ApiJsonBodyParser(PlayBodyParsers parsers) {
        super(parsers, Common.getHttpMaxMemoryBuffer(), Common.getHttpMaxMemoryBuffer());
    }
}
