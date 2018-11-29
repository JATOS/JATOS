package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import services.gui.*;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private final ResultDataExportService resultDataExportService;
    private final IOUtils ioUtils;
    private final JsonUtils jsonUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final WorkerDao workerDao;

    @Inject
    ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower,
            Checker checker, IOUtils ioUtils, JsonUtils jsonUtils,
            AuthenticationService authenticationService,
            ImportExportService importExportService,
            ResultDataExportService resultDataStringGenerator,
            StudyDao studyDao, ComponentDao componentDao, WorkerDao workerDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.jsonUtils = jsonUtils;
        this.ioUtils = ioUtils;
        this.authenticationService = authenticationService;
        this.importExportService = importExportService;
        this.resultDataExportService = resultDataStringGenerator;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.workerDao = workerDao;
    }

    /**
     * Ajax request
     * <p>
     * Checks whether this is a legitimate study import, whether the study or
     * its directory already exists. The actual import happens in
     * importStudyConfirmed(). Returns JSON.
     */
    @Transactional
    @Authenticated
    public Result importStudy() throws JatosGuiException {
        LOGGER.debug(".importStudy");
        User loggedInUser = authenticationService.getLoggedInUser();

        // Get file from request
        FilePart<Object> filePart = request().body().asMultipartFormData().getFile(Study.STUDY);

        if (filePart == null) {
            jatosGuiExceptionThrower
                    .throwAjax(MessagesStrings.FILE_MISSING, Http.Status.BAD_REQUEST);
        }
        if (!Study.STUDY.equals(filePart.getKey())) {
            // If wrong key the upload comes from wrong form
            jatosGuiExceptionThrower
                    .throwAjax(MessagesStrings.NO_STUDY_UPLOAD, Http.Status.BAD_REQUEST);
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
     * Actual import of study and its study assets directory. Always subsequent
     * of an importStudy() call.
     */
    @Transactional
    @Authenticated
    public Result importStudyConfirmed() throws JatosGuiException {
        LOGGER.debug(".importStudyConfirmed");
        User loggedInUser = authenticationService.getLoggedInUser();

        // Get confirmation: overwrite study's properties and/or study assets
        JsonNode json = request().body().asJson();
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
     * Export a study. Returns a .zip file that contains the study asset
     * directory and the study as JSON as a .jas file.
     */
    @Transactional
    @Authenticated
    public Result exportStudy(Long studyId) throws JatosGuiException {
        LOGGER.debug(".exportStudy: studyId " + studyId);
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

        String zipFileName = ioUtils.generateFileName(study.getTitle(), IOUtils.ZIP_FILE_SUFFIX);
        response().setHeader("Content-disposition", "attachment; filename=" + zipFileName);
        return ok(zipFile).as("application/x-download");
    }

    /**
     * Ajax request
     * <p>
     * Export of a component. Returns a .jac file with the component in JSON.
     */
    @Transactional
    @Authenticated
    public Result exportComponent(Long studyId, Long componentId) throws JatosGuiException {
        LOGGER.debug(".exportComponent: studyId " + studyId + ", "
                + "componentId " + componentId);
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
            String errorMsg =
                    MessagesStrings.componentExportFailure(componentId, component.getTitle());
            jatosGuiExceptionThrower.throwAjax(errorMsg, Http.Status.INTERNAL_SERVER_ERROR);
        }

        String filename =
                ioUtils.generateFileName(component.getTitle(), IOUtils.COMPONENT_FILE_SUFFIX);
        response().setHeader("Content-disposition", "attachment; filename=" + filename);
        return ok(componentAsJson).as("application/x-download");
    }

    /**
     * Ajax request
     * <p>
     * Checks whether this is a legitimate component import. The actual import
     * happens in importComponentConfirmed(). Returns JSON with the results.
     */
    @Transactional
    @Authenticated
    public Result importComponent(Long studyId) throws JatosGuiException {
        LOGGER.debug(".importComponent: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        ObjectNode json = null;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);

            FilePart<Object> filePart =
                    request().body().asMultipartFormData().getFile(Component.COMPONENT);
            json = importExportService.importComponent(study, filePart);
        } catch (ForbiddenException | BadRequestException | IOException e) {
            importExportService.cleanupAfterComponentImport();
            jatosGuiExceptionThrower.throwStudy(e, study.getId());
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
        LOGGER.debug(".importComponentConfirmed: " + "studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();

        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            String tempComponentFileName = session(ImportExportService.SESSION_TEMP_COMPONENT_FILE);
            importExportService.importComponentConfirmed(study, tempComponentFileName);
        } catch (Exception e) {
            jatosGuiExceptionThrower.throwStudy(e, study.getId());
        } finally {
            importExportService.cleanupAfterComponentImport();
        }
        return ok(RequestScopeMessaging.getAsJson());
    }

    /**
     * Ajax request (uses download.js on the client side)
     * <p>
     * Returns all result data of ComponentResults belonging to the given StudyResults. The
     * StudyResults are specified by their IDs in the request's body. Returns the result data as
     * text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfStudyResults() throws JatosGuiException {
        LOGGER.debug(".exportDataOfStudyResults");
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> studyResultIdList = new ArrayList<>();
        request().body().asJson().get("resultIds")
                .forEach(node -> studyResultIdList.add(node.asLong()));
        String resultData = "";
        try {
            resultData = resultDataExportService
                    .fromStudyResultIdList(studyResultIdList, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(resultData);
    }

    /**
     * Ajax request  (uses download.js on the client side)
     * <p>
     * Returns all result data of ComponentResults belonging to StudyResults belonging to the
     * given study. Returns the result data as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfAllStudyResults(Long studyId) throws JatosGuiException {
        LOGGER.debug(".exportDataOfAllStudyResults: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        String resultData = "";
        try {
            resultData = resultDataExportService.forStudy(loggedInUser, study);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(resultData);
    }

    /**
     * Ajax request (uses download.js on the client side)
     * <p>
     * Returns all result data of ComponentResults. The ComponentResults are specified by their IDs
     * in the request's body. Returns the result data as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfComponentResults() throws JatosGuiException {
        LOGGER.debug(".exportDataOfComponentResults");
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> componentResultIdList = new ArrayList<>();
        request().body().asJson().get("resultIds")
                .forEach(node -> componentResultIdList.add(node.asLong()));
        String resultData = "";
        try {
            resultData = resultDataExportService
                    .fromComponentResultIdList(componentResultIdList, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(resultData);
    }

    /**
     * Ajax request (uses download.js on the client side)
     * <p>
     * Returns all result data of ComponentResults belonging to the given component and study.
     * Returns the result data as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfAllComponentResults(Long studyId, Long componentId)
            throws JatosGuiException {
        LOGGER.debug(".exportDataOfAllComponentResults: studyId " + studyId
                + ", " + "componentId " + componentId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Study study = studyDao.findById(studyId);
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        String resultData = "";
        try {
            resultData = resultDataExportService.forComponent(loggedInUser, component);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        return ok(resultData);
    }

    /**
     * Ajax request (uses download.js on the client side)
     * <p>
     * Returns all result data of ComponentResults belonging to the given worker's StudyResults.
     * Returns the result data as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportAllResultDataOfWorker(Long workerId) throws JatosGuiException {
        LOGGER.debug(".exportAllResultDataOfWorker: workerId " + workerId);
        Worker worker = workerDao.findById(workerId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkWorker(worker, workerId);
        } catch (BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        String resultData = "";
        try {
            resultData = resultDataExportService.forWorker(loggedInUser, worker);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(resultData);
    }

}
