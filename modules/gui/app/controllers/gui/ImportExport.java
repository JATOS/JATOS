package controllers.gui;

import auth.gui.AuthAction.Auth;
import auth.gui.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import exceptions.gui.*;
import general.common.Common;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import models.gui.ApiEnvelope.ErrorCode;
import org.apache.commons.lang3.exception.ExceptionUtils;
import play.Logger;
import play.Logger.ALogger;
import play.core.utils.HttpHeaderParameterEncoding;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import services.gui.AuthorizationService;
import services.gui.ImportExportService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.StudyService;
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Controller that cares for import/export of components and studies.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class ImportExport extends Controller {

    private static final ALogger LOGGER = Logger.of(ImportExport.class);

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final AuthService authService;
    private final ImportExportService importExportService;
    private final StudyService studyService;
    private final AuthorizationService authorizationService;

    @Inject
    ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower, AuthService authService,
            ImportExportService importExportService, StudyService studyService, AuthorizationService authorizationService) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.authService = authService;
        this.importExportService = importExportService;
        this.studyService = studyService;
        this.authorizationService = authorizationService;
    }

    /**
     * POST request that imports a JATOS study archive to JATOS. It's only used by the API. The difference
     * to the methods used by the GUI (importStudy and importStudyConfirmed) is that here all is done in one request
     * and all confirmation (e.g. keepProperties, keepAssets) has to be specified beforehand.
     *
     * @param keepProperties        If true and the study exists already in JATOS the current properties are kept.
     *                              Default is `false` (properties are overwritten by default). If the study doesn't
     *                              already exist, this parameter has no effect.
     * @param keepAssets            If true and the study exists already in JATOS the current study assets directory is
     *                              kept. Default is `false` (assets are overwritten by default). If the study doesn't
     *                              already exist, this parameter has no effect.
     * @param keepCurrentAssetsName If the assets are going to be overwritten (`keepAssets=false`), this flag indicates
     *                              if the name of the currently installed assets directory should be kept. A `false`
     *                              indicates that the name should be taken from the uploaded one. Default is `true`.
     * @param renameAssets          If the study assets directory already exists in JATOS but belongs to a different
     *                              study, it cannot be overwritten. In this case you can set `renameAssets=true` to let
     *                              JATOS add a suffix to the assets directory name (original name + "_" + a number).
     *                              Default is `true`.
     */
    @Transactional
    @Auth
    public ImportStudyResult importStudyApi(Http.Request request, boolean keepProperties, boolean keepAssets,
                                                                    boolean keepCurrentAssetsName, boolean renameAssets) throws HttpException, IOException {
        User signedinUser = authService.getSignedinUser();

        File file = extractStudyArchiveFile(request);

        Map<String, Object> importInfo;
        try {
             importInfo = importExportService.importStudy(signedinUser, file);
        } catch (Exception e) {
            importExportService.cleanupAfterStudyImport();
            LOGGER.info(".importStudy: Import of study failed - " + ExceptionUtils.getRootCause(e).getMessage());
            throw new InternalServerErrorException("Import of study failed: " + ExceptionUtils.getRootCause(e).getMessage());
        }

        try {
            Study study = importExportService.importStudyConfirmed(signedinUser, keepProperties, keepAssets,
                    keepCurrentAssetsName, renameAssets);
            return new ImportStudyResult(importInfo, study);
        } finally {
            importExportService.cleanupAfterStudyImport();
        }
    }

    public static final class ImportStudyResult {
        public final Map<String, Object> importInfo;
        public final Study study;

        public ImportStudyResult(Map<String, Object> importInfo, Study study) {
            this.importInfo = importInfo;
            this.study = study;
        }

        public boolean wasOverwritten() {
            return Boolean.TRUE.equals(importInfo.get("studyExists"));
        }
    }

    /**
     * Extracts the study archive file from the body. Supports:
     * - multipart/form-data with the field "study"
     * - application/zip with raw body
     * - application/octet-stream with raw body
     */
    private File extractStudyArchiveFile(Http.Request request) throws BadRequestException, IOException {
        String contentType = request.contentType().orElse("").toLowerCase();

        if (contentType.startsWith("multipart/form-data")) {
            if (request.body().asMultipartFormData() == null) {
                throw new BadRequestException(MessagesStrings.FILE_MISSING, ErrorCode.MISSING_FILE);
            }
            FilePart<Object> filePart = request.body().asMultipartFormData().getFile(Study.STUDY);
            if (filePart == null) {
                throw new BadRequestException(MessagesStrings.FILE_MISSING, ErrorCode.MISSING_FILE);
            }
            if (!Study.STUDY.equals(filePart.getKey())) {
                throw new BadRequestException(MessagesStrings.NO_STUDY_UPLOAD);
            }
            return (File) filePart.getFile();
        }

        // Raw body: application/zip or application/octet-stream (or anything else you decide to allow)
        // We intentionally accept both because many clients default to octet-stream.
        if ("application/zip".equals(contentType)
                || "application/jzip".equals(contentType)
                || "application/octet-stream".equals(contentType)
                || contentType.isEmpty()) {
            Http.RawBuffer raw = request.body().asRaw();
            if (raw == null) {
                throw new BadRequestException(MessagesStrings.FILE_MISSING, ErrorCode.MISSING_FILE);
            }
            // Prefer a temp file if Play stored it on disk (common for larger uploads)
            File rawFile = raw.asFile();
            if (rawFile != null && rawFile.exists() && rawFile.length() > 0) {
                return rawFile;
            }
            // Fallback: raw bytes (small uploads); write to a temp file
            if (raw.asBytes() == null || raw.asBytes().isEmpty()) {
                throw new BadRequestException(MessagesStrings.FILE_MISSING, ErrorCode.MISSING_FILE);
            }
            Path tmp = Files.createTempFile("jatos-study-upload-", ".jzip");
            Files.write(tmp, raw.asBytes().toArray());
            return tmp.toFile();
        }
        throw new BadRequestException(
                "Unsupported Content-Type '" + contentType + "'. Use multipart/form-data, application/zip, or application/octet-stream."
        );
    }

    /**
     * POST request that checks whether this is a legitimate import of a JATOS study archive, e.g. if the
     * study or its directory already exists. The actual import happens in method importStudyConfirmed().
     */
    @Transactional
    @Auth
    public Result importStudy(Http.Request request) {
        User signedinUser = authService.getSignedinUser();

        FilePart<Object> filePart = request.body().asMultipartFormData().getFile(Study.STUDY);

        if (filePart == null) {
            return badRequest(MessagesStrings.FILE_MISSING);
        }
        if (!Study.STUDY.equals(filePart.getKey())) {
            // If wrong key the upload comes from wrong form
            return badRequest(MessagesStrings.NO_STUDY_UPLOAD);
        }

        try {
            File file = (File) filePart.getFile();
            Map<String, Object> resultInfo = importExportService.importStudy(signedinUser, file);
            return ok(JsonUtils.asJsonNode(resultInfo));
        } catch (Exception e) {
            importExportService.cleanupAfterStudyImport();
            LOGGER.info(".importStudy: Import of study failed - " + ExceptionUtils.getRootCause(e).getMessage());
            return badRequest("Import of study failed: " + ExceptionUtils.getRootCause(e).getMessage());
        }
    }

    /**
     * POST request that does the actual import of a JATOS study archive. This endpoint gets called always
     * after importStudy(). It's only used by the GUI.
     *
     * It expects the following parameters in a JSON object in the request body:
     * 1) keepProperties - If true and the study exists already in JATOS the current properties are kept.
     *                             Default is `false` (properties are overwritten by default). If the study doesn't
     *                             already exist, this parameter has no effect.
     * 2) keepAssets - If true and the study exists already in JATOS the current study assets directory is
     *                 kept. Default is `false` (assets are overwritten by default). If the study doesn't
     *                 already exist, this parameter has no effect.
     * 3) keepCurrentAssetsName - If the assets are going to be overwritten (`keepAssets=false`), this flag indicates
     *                            if the name of the currently installed assets directory should be kept. A `false`
     *                            indicates that the name should be taken from the uploaded one. Default is `true`.
     * 4) renameAssets - If the study assets directory already exists in JATOS but belongs to a different
     *                   study, it cannot be overwritten. In this case you can set `renameAssets=true` to let
     *                   JATOS add a suffix to the assets directory name (original name + "_" + a number).
     *                   Default is `true`.
     */
    @Transactional
    @Auth
    public Result importStudyConfirmed(Http.Request request) throws JatosGuiException {
        User signedinUser = authService.getSignedinUser();

        // Get confirmation: overwrite study's properties and/or study assets
        JsonNode json = request.body().asJson();
        if (json == null || json.findPath("keepProperties") == null ||
                json.findPath("keepAssets") == null) {
            LOGGER.error(".importStudyConfirmed: " + "JSON is malformed");
            return badRequest("Import of study failed");
        }
        boolean keepProperties = json.findPath("keepProperties").asBoolean();
        boolean keepAssets = json.findPath("keepAssets").asBoolean();
        boolean keepCurrentAssetsName = json.findPath("keepCurrentAssetsName").booleanValue();
        boolean renameAssets = json.findPath("renameAssets").booleanValue();

        long importedStudyId = 0L;
        try {
            Study study = importExportService.importStudyConfirmed(signedinUser, keepProperties, keepAssets,
                    keepCurrentAssetsName, renameAssets);
            importedStudyId = study.getId();
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            jatosGuiExceptionThrower.throwHome(request, e);
        } finally {
            importExportService.cleanupAfterStudyImport();
        }
        return ok(Long.toString(importedStudyId));
    }

    /**
     * GET request that exports a JATOS study archive. The archive is a zip compressed file that contains
     * the study asset directory and the study properties as JSON saved in a .jas file.
     */
    @Transactional
    @Auth
    public Result exportStudy(String id) throws ForbiddenException, NotFoundException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, signedinUser);

        File zipFile;
        try {
            zipFile = importExportService.createStudyExportZipFile(study);
        } catch (IOException e) {
            String errorMsg = MessagesStrings.studyExportFailure(study.getId(), study.getTitle());
            LOGGER.error(".exportStudy: " + errorMsg, e);
            return internalServerError(errorMsg);
        }

        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_study_" + study.getUuid() + "." + Common.getStudyArchiveSuffix());
        // We need the "Content-Disposition" header for API calls (not for the GUI)
        //noinspection ResultOfMethodCallIgnored
        return ok().streamed(
                        Helpers.okFileStreamed(zipFile, zipFile::delete),
                        Optional.of(zipFile.length()),
                        Optional.of("application/zip"))
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
    }

}
