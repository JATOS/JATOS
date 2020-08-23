package controllers.gui;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.stream.IOResult;
import akka.stream.OverflowStrategy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Controller that cares for import/export of components, studies and their result data.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class ImportExport extends Controller {

    private static final ALogger LOGGER = Logger.of(ImportExport.class);

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final AuthenticationService authenticationService;
    private final ImportExportService importExportService;
    private final ResultDataExporter resultDataExporter;
    private final IOUtils ioUtils;
    private final JsonUtils jsonUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final StudyResultDao studyResultDao;
    private final ComponentResultDao componentResultDao;

    @Inject
    ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker, IOUtils ioUtils,
            JsonUtils jsonUtils, AuthenticationService authenticationService, ImportExportService importExportService,
            ResultDataExporter resultDataStringGenerator, StudyDao studyDao, ComponentDao componentDao,
            StudyResultDao studyResultDao, ComponentResultDao componentResultDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.jsonUtils = jsonUtils;
        this.ioUtils = ioUtils;
        this.authenticationService = authenticationService;
        this.importExportService = importExportService;
        this.resultDataExporter = resultDataStringGenerator;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.studyResultDao = studyResultDao;
        this.componentResultDao = componentResultDao;
    }

    /**
     * Ajax request
     * <p>
     * Checks whether this is a legitimate study import, whether the study or its directory already exists. The actual
     * import happens in importStudyConfirmed(). Returns JSON.
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
     * Ajax request
     * <p>
     * Actual import of study and its study assets directory. Always subsequent of an importStudy() call.
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
     * Ajax request
     * <p>
     * Export a study. Returns a .zip file that contains the study asset directory and the study as JSON as a .jas
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
     * Ajax request
     * <p>
     * Export of a component. Returns a .jac file with the component in JSON.
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
     * Ajax request
     * <p>
     * Checks whether this is a legitimate component import. The actual import happens in importComponentConfirmed().
     * Returns JSON with the results.
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
     * Ajax request
     * <p>
     * Actual import of component.
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
     * Ajax request with chunked streaming
     * <p>
     * Returns all result data of ComponentResults belonging to the given StudyResults. The StudyResults are specified
     * by their IDs in the request's body. Returns the result data as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfStudyResults(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> studyResultIdList = new ArrayList<>();
        request.body().asJson().get("resultIds").forEach(node -> studyResultIdList.add(node.asLong()));

        int bufferSize = studyResultIdList.size();
        Source<ByteString, ?> source = Source.<ByteString>actorRef(bufferSize, OverflowStrategy.fail())
                .mapMaterializedValue(sourceActor -> {
                    CompletableFuture.runAsync(() -> {
                        resultDataExporter.byStudyResultIds(sourceActor, studyResultIdList, loggedInUser);
                        sourceActor.tell(new Status.Success(NotUsed.getInstance()), ActorRef.noSender());
                    });
                    return sourceActor;
                });
        return ok().chunked(source).as("text/plain; charset=utf-8");
    }

    /**
     * Ajax request with chunked streaming
     * <p>
     * Returns all result data of ComponentResults. The ComponentResults are specified by their IDs in the request's
     * body. Returns the result data as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfComponentResults(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> componentResultIdList = new ArrayList<>();
        request.body().asJson().get("resultIds").forEach(node -> componentResultIdList.add(node.asLong()));

        int bufferSize = componentResultIdList.size();
        Source<ByteString, ?> source = Source.<ByteString>actorRef(bufferSize, OverflowStrategy.fail())
                .mapMaterializedValue(sourceActor -> {
                    CompletableFuture.runAsync(() -> {
                        resultDataExporter.byComponentResultIds(sourceActor, componentResultIdList, loggedInUser);
                        sourceActor.tell(new Status.Success(NotUsed.getInstance()), ActorRef.noSender());
                    });
                    return sourceActor;
                });
        return ok().chunked(source).as("text/plain; charset=utf-8");
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
        final Source<ByteString, CompletionStage<IOResult>> fileSource = FileIO.fromFile(file);
        Source<ByteString, CompletionStage<IOResult>> wrap = fileSource.mapMaterializedValue(
                action -> action.whenCompleteAsync((ioResult, exception) -> handler.run()));
        return ok().streamed(wrap, Optional.of(file.length()), Optional.of(contentType));
    }

}
