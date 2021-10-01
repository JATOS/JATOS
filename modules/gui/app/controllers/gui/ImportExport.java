package controllers.gui;

import akka.stream.IOResult;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.common.Common;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.*;
import play.Logger;
import play.Logger.ALogger;
import play.core.utils.HttpHeaderParameterEncoding;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import services.gui.*;
import utils.common.IOUtils;
import utils.common.JsonUtils;
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
    private final JsonUtils jsonUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final StudyResultDao studyResultDao;
    private final ComponentResultDao componentResultDao;

    @Inject
    ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker, IOUtils ioUtils,
            JsonUtils jsonUtils, AuthenticationService authenticationService, ImportExportService importExportService,
            ResultService resultService, StudyDao studyDao, ComponentDao componentDao,
            StudyResultDao studyResultDao, ComponentResultDao componentResultDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.jsonUtils = jsonUtils;
        this.ioUtils = ioUtils;
        this.authenticationService = authenticationService;
        this.importExportService = importExportService;
        this.resultService = resultService;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.studyResultDao = studyResultDao;
        this.componentResultDao = componentResultDao;
    }

    /**
     * POST request that checks whether this is a legitimate study import, whether the study or its directory already
     * exists. The actual import happens in importStudyConfirmed(). Returns JSON.
     */
    @Transactional
    @Authenticated
    public Result importStudy(Http.Request request) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();

        // Get file from request
        FilePart<Object> filePart = request.body().asMultipartFormData().getFile(Study.STUDY);

        if (filePart == null) {
            jatosGuiExceptionThrower.throwAjax(MessagesStrings.FILE_MISSING, Http.Status.BAD_REQUEST);
        }
        if (!Study.STUDY.equals(filePart.getKey())) {
            // If wrong key the upload comes from wrong form
            jatosGuiExceptionThrower.throwAjax(MessagesStrings.NO_STUDY_UPLOAD, Http.Status.BAD_REQUEST);
        }

        JsonNode responseJson = null;
        try {
            File file = (File) filePart.getFile();
            responseJson = importExportService.importStudy(loggedInUser, file);
        } catch (Exception e) {
            importExportService.cleanupAfterStudyImport();
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(responseJson);
    }

    /**
     * POST request that does Actual import of study and its study assets directory. Always subsequent of an importStudy() call.
     */
    @Transactional
    @Authenticated
    public Result importStudyConfirmed(Http.Request request) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();

        // Get confirmation: overwrite study's properties and/or study assets
        JsonNode json = request.body().asJson();
        try {
            importExportService.importStudyConfirmed(loggedInUser, json);
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
    public Result exportStudy(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        File zipFile = null;
        try {
            zipFile = importExportService.createStudyExportZipFile(study);
        } catch (IOException e) {
            String errorMsg = MessagesStrings.studyExportFailure(studyId, study.getTitle());
            LOGGER.error(".exportStudy: " + errorMsg, e);
            jatosGuiExceptionThrower.throwAjax(errorMsg, Http.Status.INTERNAL_SERVER_ERROR);
        }

        String zipFileName = ioUtils.generateFileName(study.getTitle(), IOUtils.JZIP_FILE_SUFFIX);
        String filenameInHeader = HttpHeaderParameterEncoding.encode("filename", zipFileName);
        return okFileStreamed(zipFile, zipFile::delete, "application/zip")
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filenameInHeader);
    }

    /**
     * GET request that exports a component. Returns a .jac file with the component in JSON.
     */
    @Transactional
    @Authenticated
    public Result exportComponent(Long studyId, Long componentId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        JsonNode componentAsJson = null;
        try {
            componentAsJson = jsonUtils.componentAsJsonForIO(component);
        } catch (IOException e) {
            String errorMsg = MessagesStrings.componentExportFailure(componentId, component.getTitle());
            jatosGuiExceptionThrower.throwAjax(errorMsg, Http.Status.INTERNAL_SERVER_ERROR);
        }

        String filename = ioUtils.generateFileName(component.getTitle(), IOUtils.COMPONENT_FILE_SUFFIX);
        String filenameInHeader = HttpHeaderParameterEncoding.encode("filename", filename);
        return ok(componentAsJson).withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filenameInHeader);
    }

    /**
     * POST request that checks whether this is a legitimate component import. The actual import happens in
     * importComponentConfirmed(). Returns JSON with the results.
     */
    @Transactional
    @Authenticated
    public Result importComponent(Http.Request request, Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        ObjectNode json = null;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);

            FilePart<Object> filePart = request.body().asMultipartFormData().getFile(Component.COMPONENT);
            json = importExportService.importComponent(study, filePart);
        } catch (ForbiddenException | BadRequestException | IOException e) {
            importExportService.cleanupAfterComponentImport();
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }
        return ok(json);
    }

    /**
     * POST request that actually imports a component.
     */
    @Transactional
    @Authenticated
    public Result importComponentConfirmed(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();

        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            String tempComponentFileName = session(ImportExportService.SESSION_TEMP_COMPONENT_FILE);
            importExportService.importComponentConfirmed(study, tempComponentFileName);
        } catch (Exception e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        } finally {
            importExportService.cleanupAfterComponentImport();
        }
        return ok(RequestScopeMessaging.getAsJson());
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
        request.body().asJson().get("resultIds").forEach(node -> studyResultIdList.add(node.asLong()));
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
        request.body().asJson().get("resultIds").forEach(node -> componentResultIdList.add(node.asLong()));
        return exportResultData(loggedInUser, componentResultIdList);
    }

    private Result exportResultData(User loggedInUser, List<Long> componentResultIdList) {
        if (Common.isResultDataExportUseTmpFile()) {
            File tmpFile = resultService.getComponentResultDataAsTmpFile(loggedInUser, componentResultIdList);
            return okFileStreamed(tmpFile, tmpFile::delete, "text/plain; charset=utf-8");
        } else {
            Source<ByteString, ?> dataSource = resultService.streamComponentResultData(loggedInUser,
                    componentResultIdList);
            return ok().chunked(dataSource).as("text/plain; charset=utf-8");
        }
    }

    @Transactional
    @Authenticated
    public Result downloadSingleResultFile(Long studyId, Long studyResultId, Long componetResultId, String filename)
            throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        try {
            String filenameInHeader = HttpHeaderParameterEncoding.encode("filename", filename);
            return ok(ioUtils.getResultUploadFileSecurely(studyResultId, componetResultId, filename))
                    .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filenameInHeader);
        } catch (IOException e) {
            return badRequest("File does not exist");
        }
    }

    @Transactional
    @Authenticated
    public Result exportResultFilesOfStudyResults(Http.Request request) throws IOException, JatosGuiException {
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
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        if (resultFileList.isEmpty()) return notFound("No result files found");

        File zipFile = File.createTempFile("resultFiles", "." + IOUtils.ZIP_FILE_SUFFIX);
        zipFile.deleteOnExit();
        ZipUtil.zipFiles(resultFileList, zipFile);
        return okFileStreamed(zipFile, zipFile::delete, "application/zip");
    }

    @Transactional
    @Authenticated
    public Result exportResultFilesOfComponentResults(Http.Request request) throws IOException, JatosGuiException {
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
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        if (resultFileList.isEmpty()) return notFound("No result files found");

        File zipFile = File.createTempFile("resultFiles", "." + IOUtils.ZIP_FILE_SUFFIX);
        zipFile.deleteOnExit();
        ZipUtil.zipFiles(resultFileList, zipFile);

        return okFileStreamed(zipFile, zipFile::delete, "application/zip");
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
