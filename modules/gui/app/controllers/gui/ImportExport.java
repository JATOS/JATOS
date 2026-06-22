package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import exceptions.common.JatosException;
import general.common.Common;
import http.common.Http.Context;
import general.common.MessagesStrings;
import json.common.DefaultJson;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.core.utils.HttpHeaderParameterEncoding;
import play.libs.Files.TemporaryFile;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.AuthorizationService;
import services.gui.ImportExportService;
import services.gui.StudyService;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.User.Role.USER;

/**
 * Controller that cares for import/export of components and studies.
 */
@Singleton
public class ImportExport extends Controller {

    private static final ALogger LOGGER = Logger.of(ImportExport.class);

    private final ImportExportService importExportService;
    private final StudyService studyService;
    private final AuthorizationService authorizationService;
    private final DefaultJson defaultJson;

    @Inject
    ImportExport(ImportExportService importExportService,
                 StudyService studyService,
                 AuthorizationService authorizationService,
                 DefaultJson defaultJson) {
        this.importExportService = importExportService;
        this.studyService = studyService;
        this.authorizationService = authorizationService;
        this.defaultJson = defaultJson;
    }

    /**
     * POST request that checks whether this is a legitimate import of a JATOS study archive, e.g. if the study or its
     * directory already exists. The actual import happens in the method importStudyConfirmed().
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result importStudy(Http.Request request) {
        // Get file from request
        Http.MultipartFormData<TemporaryFile> body = request.body().asMultipartFormData();
        Http.MultipartFormData.FilePart<TemporaryFile> filePart = body.getFile(Study.STUDY);

        if (filePart == null) {
            return badRequest(MessagesStrings.FILE_MISSING);
        }

        try {
            Path file = filePart.getRef().path();
            Map<String, Object> resultInfo = importExportService.importStudy(file);
            return ok(defaultJson.objAsJsonNode(resultInfo));
        } catch (Exception e) {
            importExportService.cleanupAfterStudyImport();
            throw e;
        }
    }

    /**
     * POST request that does the actual import of a JATOS study archive. This endpoint gets called always after
     * importStudy(). It's only used by the GUI.
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
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result importStudyConfirmed(Http.Request request) {
        // Get confirmation: overwrite study's properties and/or study assets
        JsonNode json = request.body().asJson();
        if (json == null
                || json.findPath("keepProperties") == null
                || json.findPath("keepAssets") == null) {
            LOGGER.error(".importStudyConfirmed: " + "JSON is malformed");
            return badRequest("Import of study failed");
        }
        boolean keepProperties = json.findPath("keepProperties").asBoolean();
        boolean keepAssets = json.findPath("keepAssets").asBoolean();
        boolean keepCurrentAssetsName = json.findPath("keepCurrentAssetsName").booleanValue();
        boolean renameAssets = json.findPath("renameAssets").booleanValue();

        long importedStudyId;
        try {
            Study study = importExportService.importStudyConfirmed(keepProperties, keepAssets,
                    keepCurrentAssetsName, renameAssets);
            importedStudyId = study.getId();
        } finally {
            importExportService.cleanupAfterStudyImport();
        }
        return ok(Long.toString(importedStudyId));
    }

    /**
     * GET request that exports a JATOS study archive. The archive is a zip compressed file that contains the study
     * asset directory and the study properties as JSON saved in a .jas file.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result exportStudy(String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Path zipFile;
        try {
            zipFile = importExportService.createStudyExportZipFile(study);
        } catch (Exception e) {
            String errorMsg = "Export of study \"" + study.getTitle() + "\" (ID " + study.getId() + ") failed.";
            LOGGER.error(".exportStudy: " + errorMsg, e);
            return internalServerError(errorMsg);
        }

        String cdHeader = "attachment; "
                + HttpHeaderParameterEncoding.encode("filename", "jatos_study_"
                + study.getUuid() + "." + Common.getStudyArchiveSuffix());
        try {
            // We need the "Content-Disposition" header for API calls (not for the GUI)
            Context.current().response().setHeader(CONTENT_DISPOSITION, cdHeader);
            return ok().streamed(
                            IOUtils.okFileStreamed(zipFile, IOUtils.deleteFile(zipFile)),
                            Optional.of(Files.size(zipFile)),
                            Optional.of("application/zip"));
        } catch (Exception e) {
            IOUtils.deleteFile(zipFile).run();
            throw new JatosException(e);
        }
    }

}
