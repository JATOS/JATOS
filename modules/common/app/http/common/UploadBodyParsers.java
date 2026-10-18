package http.common;

import general.common.Common;
import play.api.mvc.PlayBodyParsers;
import play.mvc.BodyParser;

import javax.inject.Inject;

/**
 * Multipart request limits for uploads whose file limits exceed the general HTTP limit.
 */
public final class UploadBodyParsers {

    /**
     * Space for multipart boundaries, headers and form fields, in addition to the file itself.
     */
    public static final long MULTIPART_OVERHEAD_ALLOWANCE = 1024 * 1024;

    private UploadBodyParsers() {
    }

    /**
     * Streams result files to disk within the per-file limit plus multipart overhead.
     */
    public static class ResultFile extends BodyParser.MultipartFormData {
        @Inject
        public ResultFile(PlayBodyParsers parsers) {
            super(parsers, Common.getResultUploadsMaxFileSize() + MULTIPART_OVERHEAD_ALLOWANCE);
        }
    }

    /**
     * Streams study assets to disk within the per-file limit plus multipart overhead.
     */
    public static class StudyAsset extends BodyParser.MultipartFormData {
        @Inject
        public StudyAsset(PlayBodyParsers parsers) {
            // Empty assets are valid, including text files intentionally cleared through the API.
            super(parsers, Common.getStudyAssetsMaxFileSize() + MULTIPART_OVERHEAD_ALLOWANCE, true);
        }
    }

    /**
     * Streams study archives to disk within the archive limit plus multipart overhead.
     */
    public static class StudyArchive extends BodyParser.MultipartFormData {
        @Inject
        public StudyArchive(PlayBodyParsers parsers) {
            super(parsers, Common.getStudyArchiveMaxSize() + MULTIPART_OVERHEAD_ALLOWANCE);
        }
    }
}
