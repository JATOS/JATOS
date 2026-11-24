package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import general.common.Common;
import http.common.Http.Context;
import http.common.UploadBodyParsers;
import json.common.DefaultJson;
import models.common.Study;
import play.Logger;
import play.Logger.ALogger;
import play.libs.Files.TemporaryFile;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.importexport.StudyArchiveImportExport;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.Map;

import static messaging.common.FlashMessagingHelper.SUCCESS;
import static models.common.User.Role.SUPERUSER;
import static models.common.User.Role.USER;

/**
 * Controller that cares for import/export of components and studies.
 */
@Singleton
public class ImportExport extends Controller {

    private static final ALogger LOGGER = Logger.of(ImportExport.class);

    private final StudyArchiveImportExport studyArchiveImportExport;
    private final DefaultJson defaultJson;

    @Inject
    ImportExport(StudyArchiveImportExport studyArchiveImportExport,
                 DefaultJson defaultJson) {
        this.studyArchiveImportExport = studyArchiveImportExport;
        this.defaultJson = defaultJson;
    }

    /**
     * POST request that checks whether this is a legitimate import of a JATOS study archive, e.g. if the study or its
     * directory already exists. The actual import happens in the method importStudyConfirmed().
     */
    @Async(Executor.IO)
    @Auth(roles = {USER, SUPERUSER})
    @BodyParser.Of(UploadBodyParsers.StudyArchive.class)
    public Result importStudy(Http.Request request) {
        // Get file from request
        Http.MultipartFormData<TemporaryFile> body = request.body().asMultipartFormData();
        Http.MultipartFormData.FilePart<TemporaryFile> filePart = body == null ? null : body.getFile(Study.STUDY);

        if (filePart == null) {
            return badRequest("File missing");
        }

        if (filePart.getFileSize() > Common.getStudyArchiveMaxSize()) {
            return status(413, "Uploaded file exceeds the configured size limit");
        }

        try {
            Path file = filePart.getRef().path();
            Map<String, Object> resultInfo = studyArchiveImportExport.importStudy(file);
            return ok(defaultJson.objAsJsonNode(resultInfo));
        } catch (Exception e) {
            studyArchiveImportExport.cleanupAfterStudyImport();
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
    @Auth(roles = {USER, SUPERUSER})
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
            Study study = studyArchiveImportExport.importStudyConfirmed(keepProperties, keepAssets,
                    keepCurrentAssetsName, renameAssets);
            importedStudyId = study.getId();
        } finally {
            studyArchiveImportExport.cleanupAfterStudyImport();
        }
        String scope = !keepProperties ? (!keepAssets ? "properties and assets" : "properties") : "assets";
        Context.current().response().putFlash(SUCCESS, "Replaced " + scope + " with those from the study archive.");
        return ok(Long.toString(importedStudyId));
    }

    @Async(Executor.IO)
    @Auth(roles = {USER, SUPERUSER})
    public Result cancelStudyImport() {
        studyArchiveImportExport.cancelStudyImport();
        return noContent();
    }

}
