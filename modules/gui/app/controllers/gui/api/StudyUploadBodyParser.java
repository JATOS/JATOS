package controllers.gui.api;

import general.common.Common;
import http.common.UploadBodyParsers;
import org.apache.pekko.util.ByteString;
import play.api.mvc.PlayBodyParsers;
import play.api.http.HttpConfiguration;
import play.http.HttpErrorHandler;
import play.libs.F;
import play.libs.streams.Accumulator;
import play.mvc.BodyParser;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Spills binary study archives to disk while preserving JSON parsing and the dedicated multipart archive limit.
 */
public class StudyUploadBodyParser extends BodyParser.Default {

    private final BodyParser<Object> archiveParser;
    private final BodyParser<Object> multipartParser;

    /**
     * Keeps only a small archive buffer in memory and uses the configured archive limit.
     */
    @Inject
    public StudyUploadBodyParser(HttpErrorHandler errorHandler, HttpConfiguration configuration,
                                 PlayBodyParsers parsers) {
        super(errorHandler, configuration, parsers);
        multipartParser = BodyParser.widen(new UploadBodyParsers.StudyArchive(parsers));
        archiveParser = BodyParser.widen(new BodyParser.Raw(parsers, Common.getStudyArchiveMaxMemoryBuffer(), Common.getStudyArchiveMaxSize()));
    }

    /**
     * Selects streaming raw parsing for archives; multipart archives use their own limit, and JSON retains its standard limit.
     */
    @Override
    public Accumulator<ByteString, F.Either<Result, Object>> apply(Http.RequestHeader request) {
        String type = request.contentType().orElse("").toLowerCase(Locale.ROOT);
        if (type.equals("multipart/form-data")) return multipartParser.apply(request);
        if (type.isEmpty() || type.equals("application/zip") || type.equals("application/jzip")
                || type.equals("application/octet-stream")) {
            return archiveParser.apply(request);
        }
        return super.apply(request);
    }
}
