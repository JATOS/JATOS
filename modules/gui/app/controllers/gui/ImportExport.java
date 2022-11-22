package controllers.gui;

import akka.stream.IOResult;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import general.common.StudyLogger;
import general.gui.RequestScopeMessaging;
import models.common.ComponentResult;
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
import services.gui.*;
import utils.common.Helpers;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static services.gui.ResultStreamer.*;

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
    private final AuthenticationService authenticationService;
    private final ImportExportService importExportService;
    private final ResultStreamer resultStreamer;
    private final IOUtils ioUtils;
    private final ComponentResultIdsExtractor componentResultIdsExtractor;
    private final StudyDao studyDao;
    private final StudyResultDao studyResultDao;
    private final ComponentResultDao componentResultDao;
    private final StudyLogger studyLogger;

    @Inject
    ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker, IOUtils ioUtils,
            AuthenticationService authenticationService, ImportExportService importExportService,
            ResultStreamer resultStreamer, ComponentResultIdsExtractor componentResultIdsExtractor, StudyDao studyDao,
            StudyResultDao studyResultDao, ComponentResultDao componentResultDao, StudyLogger studyLogger) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.ioUtils = ioUtils;
        this.authenticationService = authenticationService;
        this.importExportService = importExportService;
        this.resultStreamer = resultStreamer;
        this.componentResultIdsExtractor = componentResultIdsExtractor;
        this.studyDao = studyDao;
        this.studyResultDao = studyResultDao;
        this.componentResultDao = componentResultDao;
        this.studyLogger = studyLogger;
    }

    /**
     * POST request that imports a study zip file to JATOS. It's only used by the API. The difference to the methods
     * used by the GUI (importStudy and importStudyConfirmed) is, that here all is done in one request and all
     * confirmation (e.g. overwriteProperties, overwriteDir) has to be specified beforehand.
     */
    @Transactional
    @Authenticated
    public Result importStudyApi(Http.Request request, boolean overwriteProperties, boolean overwriteDir,
            boolean keepCurrentDirName, boolean renameDir) throws ForbiddenException, NotFoundException, IOException {
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
            LOGGER.info(".importStudyApi: Import of study failed");
            return badRequest("Import of study failed: " + e.getMessage());
        }

        try {
            importExportService.importStudyConfirmed(loggedInUser, overwriteProperties, overwriteDir,
                    keepCurrentDirName, renameDir);
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
    @Authenticated
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
            LOGGER.info(".importStudy: Import of study failed");
            return badRequest("Import of study failed: " + e.getMessage());
        }
        return ok(responseJson);
    }

    /**
     * POST request that does Actual import of study and its study assets directory. Always subsequent of an
     * importStudy() call.
     */
    @Transactional
    @Authenticated
    public Result importStudyConfirmed(Http.Request request) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();

        // Get confirmation: overwrite study's properties and/or study assets
        JsonNode json = request.body().asJson();
        if (json == null || json.findPath("overwriteProperties") == null ||
                json.findPath("overwriteDir") == null) {
            LOGGER.error(".importStudyConfirmed: " + "JSON is malformed");
            return badRequest("Import of study failed");
        }
        boolean overwriteProperties = json.findPath("overwriteProperties").asBoolean();
        boolean overwriteDir = json.findPath("overwriteDir").asBoolean();
        boolean keepCurrentDirName = json.findPath("keepCurrentDirName").booleanValue();
        boolean renameDir = json.findPath("renameDir").booleanValue();

        try {
            importExportService.importStudyConfirmed(loggedInUser, overwriteProperties, overwriteDir,
                    keepCurrentDirName, renameDir);
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
    @Authenticated
    public Result exportStudy(Long studyId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkStandardForStudy(study, studyId, loggedInUser);

        File zipFile;
        try {
            zipFile = importExportService.createStudyExportZipFile(study);
        } catch (IOException e) {
            String errorMsg = MessagesStrings.studyExportFailure(studyId, study.getTitle());
            LOGGER.error(".exportStudy: " + errorMsg, e);
            return internalServerError(errorMsg);
        }

        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_study_" + study.getUuid() + ".jzip");
        return okFileStreamed(zipFile, zipFile::delete, "application/zip")
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
    }

    /**
     * Returns all result data of ComponentResults. The results are specified by IDs (can be any kind) in the
     * request's body. Returns the result data as plain text (each result data in a new line) or in a zip file (each
     * result data in its own file). Both options use streaming to reduce memory usage.
     */
    @Transactional
    @Authenticated
    public Result exportResultsData(Http.Request request, boolean asPlainText)
            throws BadRequestException, ForbiddenException, NotFoundException {
        User loggedInUser = authenticationService.getLoggedInUser();
        JsonNode json = request.body().asJson();
        if (json == null) return badRequest("Malformed request body");

        List<Long> componentResultIdList = componentResultIdsExtractor.extract(json);
        if (asPlainText) {
            List<Long> studyResultIdList = studyResultDao.findIdsByComponentResultIds(componentResultIdList);
            List<Study> studyList = studyDao.findByStudyResultIds(studyResultIdList);
            for (Study study : studyList) {
                checker.checkStandardForStudy(study, study.getId(), loggedInUser);
            }
            Source<ByteString, ?> dataSource = resultStreamer.streamComponentResult(componentResultIdList);
            studyList.forEach(s -> studyLogger.log(s, loggedInUser, "Exported result data"));
            String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_data_"
                    + Helpers.getDateTimeYyyyMMddHHmmss() + ".txt");
            return ok().chunked(dataSource).as("application/octet-stream")
                    .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
        } else {
            Source<ByteString, ?> dataSource = resultStreamer.streamResults(componentResultIdList, loggedInUser,
                    ResultsType.DATA_ONLY);
            String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_data_"
                    + Helpers.getDateTimeYyyyMMddHHmmss() + ".zip");
            return ok().chunked(dataSource).as("application/zip")
                    .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
        }
    }

    /**
     * Returns a single result
     */
    @Transactional
    @Authenticated
    public Result exportSingleResultFile(Long componentResultId, String filename)
            throws ForbiddenException, NotFoundException {
        ComponentResult componentResult = componentResultDao.findById(componentResultId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkComponentResult(componentResult, loggedInUser, false);

        File file;
        try {
            Study study = componentResult.getComponent().getStudy();
            file = ioUtils.getResultUploadFileSecurely(componentResult.getStudyResult().getId(), componentResultId, filename);
            studyLogger.log(study, loggedInUser, "Exported single result file");
        } catch (IOException e) {
            return badRequest("File does not exist");
        }
        return ok(file);
    }

    /**
     * Returns all files belonging to results in a zip. The results are specified by IDs (can be any kind) in the
     * request's body.
     */
    @Transactional
    @Authenticated
    public Result exportResultsFiles(Http.Request request)
            throws IOException, BadRequestException, ForbiddenException, NotFoundException {
        User loggedInUser = authenticationService.getLoggedInUser();
        JsonNode json = request.body().asJson();
        if (json == null) return badRequest("Malformed request body");

        List<Long> crids = componentResultIdsExtractor.extract(json);
        Source<ByteString, ?> dataSource = resultStreamer.streamResults(crids, loggedInUser, ResultsType.FILES_ONLY);
        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_files_"
                + Helpers.getDateTimeYyyyMMddHHmmss() + ".zip");
        return ok().chunked(dataSource).as("application/zip")
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
    }

    /**
     * Returns all results (metadata, data, files) in a zip file. The results are specified by IDs (can be any kind) in
     * the request's body.
     */
    @Transactional
    @Authenticated
    public Result exportResults(Http.Request request) throws BadRequestException {
        User loggedInUser = authenticationService.getLoggedInUser();
        JsonNode json = request.body().asJson();
        if (json == null) return badRequest("Malformed request body");

        List<Long> crids = componentResultIdsExtractor.extract(json);
        Source<ByteString, ?> dataSource = resultStreamer.streamResults(crids, loggedInUser, ResultsType.COMBINED);
        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_"
                + Helpers.getDateTimeYyyyMMddHHmmss() + ".zip");
        return ok().chunked(dataSource).as("application/zip")
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
    }

    /**
     * Returns all result's metadata in a zip file. The results are specified by IDs (can be any kind) in
     * the request's body.
     */
    @Transactional
    @Authenticated
    public Result exportResultsMetadata(
            Http.Request request) throws BadRequestException, ForbiddenException, NotFoundException, IOException {
        User loggedInUser = authenticationService.getLoggedInUser();
        JsonNode json = request.body().asJson();
        if (json == null) return badRequest("Malformed request body");

        List<Long> crids = componentResultIdsExtractor.extract(json);
        File file = resultStreamer.writeResultsMetadata(crids, loggedInUser);
        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_metadata_"
                + Helpers.getDateTimeYyyyMMddHHmmss() + ".json");
        return okFileStreamed(file, file::delete, "application/json")
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
    }

    /**
     * Helper function to allow an action after a file was sent (e.g. delete the file)
     */
    private Result okFileStreamed(final File file, final Runnable handler, final String contentType) {
        final Source<ByteString, CompletionStage<IOResult>> fileSource = FileIO.fromFile(file)
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "));
        Source<ByteString, CompletionStage<IOResult>> wrap = fileSource.mapMaterializedValue(
                action -> action.whenCompleteAsync((ioResult, exception) -> handler.run()));
        return ok().streamed(wrap, Optional.of(file.length()), Optional.of(contentType));
    }

}
