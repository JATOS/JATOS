package controllers.publix;

import general.common.Common;
import play.http.HttpErrorHandler;
import play.mvc.BodyParser;

import javax.inject.Inject;

/**
 * Buffers result text in memory, bounded solely by the configured result-data limit.
 */
public class ResultDataBodyParser extends BodyParser.TolerantText {

    /**
     * Uses the same limit as the accumulated result-data check in Publix.
     */
    @Inject
    public ResultDataBodyParser(HttpErrorHandler errorHandler) {
        super(Common.getResultDataMaxSize(), errorHandler);
    }
}
