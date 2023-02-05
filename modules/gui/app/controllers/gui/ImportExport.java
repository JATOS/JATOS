package controllers.gui;

import auth.gui.AuthAction.Auth;
import auth.gui.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.StudyDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.core.utils.HttpHeaderParameterEncoding;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import services.gui.Checker;
import services.gui.ImportExportService;
import services.gui.JatosGuiExceptionThrower;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Controller that cares for import/export of components, studies and their result data.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class ImportExport extends Controller {

    private static final ALogger LOGGER = Logger.of(ImportExport.class);

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final AuthService authenticationService;
    private final ImportExportService importExportService;
    private final StudyDao studyDao;

    @Inject
    ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
            AuthService authenticationService, ImportExportService importExportService, StudyDao studyDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.authenticationService = authenticationService;
        this.importExportService = importExportService;
        this.studyDao = studyDao;
    }

    /**
     * POST request that imports a study zip file to JATOS. It's only used by the API. The difference to the methods
     * used by the GUI (importStudy and importStudyConfirmed) is, that here all is done in one request and all
     * confirmation (e.g. keepProperties, keepAssets) has to be specified beforehand.
     */
    @Transactional
    @Auth
    public Result importStudyApi(Http.Request request, boolean keepProperties, boolean keepAssets,
            boolean keepCurrentAssetsName, boolean renameAssets) throws ForbiddenException, NotFoundException, IOException {
        User loggedInUser = authenticationService.getLoggedInUser();

        // Get file from request
        if (request.body().asMultipartFormData() == null) {
            return badRequest(MessagesStrings.FILE_MISSING);
        }
        FilePart<Object> filePart = request.body().asMultipartFormData().getFile(Study.STUDY);
        if (filePart == null) {
            return badRequest(MessagesStrings.FILE_MISSING);
        }
        if (!Study.STUDY.equals(filePart.getKey())) {
            // If wrong key the upload comes from wrong form
            return badRequest(MessagesStrings.NO_STUDY_UPLOAD);
        }

        JsonNode responseJson;
        try {
            File file = (File) filePart.getFile();
            responseJson = importExportService.importStudy(loggedInUser, file);
        } catch (Exception e) {
            importExportService.cleanupAfterStudyImport();
            LOGGER.info(".importStudy: Import of study failed - " + e.getCause().getMessage());
            return badRequest("Import of study failed: " + e.getMessage());
        }

        try {
            importExportService.importStudyConfirmed(loggedInUser, keepProperties, keepAssets,
                    keepCurrentAssetsName, renameAssets);
        } finally {
            importExportService.cleanupAfterStudyImport();
        }
        return ok(responseJson);
    }

    /**
     * POST request that checks whether this is a legitimate study import, whether the study or its directory already
     * exists. The actual import happens in importStudyConfirmed(). Returns JSON.
     */
    @Transactional
    @Auth
    public Result importStudy(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser();

        // Get file from request
        FilePart<Object> filePart = request.body().asMultipartFormData().getFile(Study.STUDY);

        if (filePart == null) {
            return badRequest(MessagesStrings.FILE_MISSING);
        }
        if (!Study.STUDY.equals(filePart.getKey())) {
            // If wrong key the upload comes from wrong form
            return badRequest(MessagesStrings.NO_STUDY_UPLOAD);
        }

        JsonNode responseJson;
        try {
            File file = (File) filePart.getFile();
            responseJson = importExportService.importStudy(loggedInUser, file);
        } catch (Exception e) {
            importExportService.cleanupAfterStudyImport();
            LOGGER.info(".importStudy: Import of study failed - " + e.getCause().getMessage());
            return badRequest("Import of study failed: " + e.getMessage());
        }
        return ok(responseJson);
    }

    /**
     * POST request that does Actual import of study and its study assets directory. Always subsequent of an
     * importStudy() call.
     */
    @Transactional
    @Auth
    public Result importStudyConfirmed(Http.Request request) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();

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

        try {
            importExportService.importStudyConfirmed(loggedInUser, keepProperties, keepAssets,
                    keepCurrentAssetsName, renameAssets);
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            jatosGuiExceptionThrower.throwHome(e);
        } finally {
            importExportService.cleanupAfterStudyImport();
        }
        return ok(RequestScopeMessaging.getAsJson());
    }

    /**
     * GET request that exports a study. Returns a .zip file that contains the study asset directory and the study as JSON as a .jas
     * file.
     */
    @Transactional
    @Auth
    public Result exportStudy(String id) throws ForbiddenException, NotFoundException {
        Study study;
        Optional<Long> studyId = Helpers.parseLong(id);
        if (studyId.isPresent()) {
            study = studyDao.findById(studyId.get());
            if (study == null) return notFound("Couldn't find study with ID " + studyId.get());
        } else {
            Optional<Study> s = studyDao.findByUuid(id);
            if (!s.isPresent()) return notFound("Couldn't find study with UUID " + id);
            study = s.get();
        }

        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkStandardForStudy(study, study.getId(), loggedInUser);

        File zipFile;
        try {
            zipFile = importExportService.createStudyExportZipFile(study);
        } catch (IOException e) {
            String errorMsg = MessagesStrings.studyExportFailure(study.getId(), study.getTitle());
            LOGGER.error(".exportStudy: " + errorMsg, e);
            return internalServerError(errorMsg);
        }

        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_study_" + study.getUuid() + ".jzip");
        //noinspection ResultOfMethodCallIgnored
        return ok().streamed(
                        Helpers.okFileStreamed(zipFile, zipFile::delete),
                        Optional.of(zipFile.length()),
                        Optional.of("application/zip"))
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
    }

}
