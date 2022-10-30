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
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import services.gui.*;
import utils.common.IOUtils;
import utils.common.ZipUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

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
    private final ResultService resultService;
    private final IOUtils ioUtils;
    private final StudyDao studyDao;
    private final StudyResultDao studyResultDao;
    private final ComponentResultDao componentResultDao;

    @Inject
    ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker, IOUtils ioUtils,
            AuthenticationService authenticationService, ImportExportService importExportService,
            ResultService resultService, StudyDao studyDao,
            StudyResultDao studyResultDao, ComponentResultDao componentResultDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.ioUtils = ioUtils;
        this.authenticationService = authenticationService;
        this.importExportService = importExportService;
        this.resultService = resultService;
        this.studyDao = studyDao;
        this.studyResultDao = studyResultDao;
        this.componentResultDao = componentResultDao;
    }

    /**
     * POST request that imports a study zip file to JATOS. It's only used by the API. The difference to the methods
     * used by the GUI (importStudy and importStudyConfirmed) is, that here all is done in one request and all
     * confirmation (e.g. overwriteProperties, overwriteDir) has to be specified beforehand.
     */
    @Transactional
    @Authenticated
    public Result importStudyApi(Http.Request request, boolean overwriteProperties, boolean overwriteDir,
            boolean keepCurrentDirName, boolean renameDir) {
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
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            LOGGER.error(".importStudyApi: " + e.getMessage());
            return internalServerError();
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
    public Result exportStudy(Long studyId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        }

        File zipFile;
        try {
            zipFile = importExportService.createStudyExportZipFile(study);
        } catch (IOException e) {
            String errorMsg = MessagesStrings.studyExportFailure(studyId, study.getTitle());
            LOGGER.error(".exportStudy: " + errorMsg, e);
            return internalServerError(errorMsg);
        }

        return okFileStreamed(zipFile, zipFile::delete, "application/octet-stream");
    }

    /**
     * Returns all result data of ComponentResults belonging to the given StudyResults. The StudyResults are specified
     * by a list of IDs in the request's body. Returns the result data as text, each line a result data. Depending on
     * the configuration it streams the results directly as a chunked response, or it first saves the results in a
     * temporary file and only when all are written sends the file in a normal response. Both approaches use streaming
     * to reduce memory usage.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfStudyResults(Http.Request request) throws IOException {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> studyResultIdList = new ArrayList<>();
        try {
            request.body().asJson().get("resultIds").forEach(node -> studyResultIdList.add(node.asLong()));
        } catch (Exception e) {
            return badRequest("Malformed JSON");
        }
        List<Long> componentResultIdList = componentResultDao.findIdsByStudyResultIds(studyResultIdList);
        return exportResultData(loggedInUser, componentResultIdList);
    }

    /**
     * Returns all result data of the given ComponentResults. The ComponentResults are specified
     * by a list of IDs in the request's body. Returns the result data as text, each line a result data. Depending on
     * the configuration it streams the results directly as a chunked response, or it first saves the results in a
     * temporary file and only when all are written sends the file in a normal response. Both approaches use streaming
     * to reduce memory usage.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfComponentResults(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> componentResultIdList = new ArrayList<>();
        try {
            request.body().asJson().get("resultIds").forEach(node -> componentResultIdList.add(node.asLong()));
        } catch (Exception e) {
            return badRequest("Malformed JSON");
        }
        return exportResultData(loggedInUser, componentResultIdList);
    }

    private Result exportResultData(User loggedInUser, List<Long> componentResultIdList) {
        if (Common.isResultDataExportUseTmpFile()) {
            File tmpFile = resultService.getComponentResultDataAsTmpFile(loggedInUser, componentResultIdList);
            return okFileStreamed(tmpFile, tmpFile::delete, "text/plain");
        } else {
            Source<ByteString, ?> dataSource = resultService.streamComponentResultData(loggedInUser,
                    componentResultIdList);
            return ok().chunked(dataSource).as("text/plain");
        }
    }

    @Transactional
    @Authenticated
    public Result downloadSingleResultFile(Long studyId, Long studyResultId, Long componetResultId, String filename) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        }

        try {
            return ok(ioUtils.getResultUploadFileSecurely(studyResultId, componetResultId, filename));
        } catch (IOException e) {
            return badRequest("File does not exist");
        }
    }

    @Transactional
    @Authenticated
    public Result exportResultFilesOfStudyResults(Http.Request request) throws IOException {
        User loggedInUser = authenticationService.getLoggedInUser();

        List<Path> resultFileList = new ArrayList<>();
        try {
            for (JsonNode node : request.body().asJson().get("resultIds")) {
                Long studyResultId = node.asLong();
                StudyResult studyResult = studyResultDao.findById(studyResultId);
                checker.checkStudyResult(studyResult, loggedInUser, false);
                Path path = Paths.get(IOUtils.getResultUploadsDir(studyResultId));
                if (Files.exists(path)) resultFileList.add(path);
            }
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            return badRequest("Malformed JSON");
        }
        if (resultFileList.isEmpty()) return notFound("No result files found");

        File zipFile = File.createTempFile("jatos_resultFiles_", ".zip");
        zipFile.deleteOnExit();
        ZipUtil.zipFiles(resultFileList, zipFile);
        return okFileStreamed(zipFile, zipFile::delete, "application/octet-stream");
    }

    @Transactional
    @Authenticated
    public Result exportResultFilesOfComponentResults(Http.Request request) throws IOException {
        User loggedInUser = authenticationService.getLoggedInUser();

        List<Path> resultFileList = new ArrayList<>();
        try {
            for (JsonNode node : request.body().asJson().get("resultIds")) {
                Long componentResultId = node.asLong();
                ComponentResult componentResult = componentResultDao.findById(componentResultId);
                checker.checkComponentResult(componentResult, loggedInUser, false);
                Path path = Paths.get(IOUtils.getResultUploadsDir(componentResult.getStudyResult().getId(),
                        componentResultId));
                if (Files.exists(path)) resultFileList.add(path);
            }
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            return badRequest("Malformed JSON");
        }
        if (resultFileList.isEmpty()) return notFound("No result files found");

        File zipFile = File.createTempFile("jatos_resultFiles_", ".zip");
        zipFile.deleteOnExit();
        ZipUtil.zipFiles(resultFileList, zipFile);

        return okFileStreamed(zipFile, zipFile::delete, "application/octet-stream");
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
