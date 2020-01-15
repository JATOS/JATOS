package controllers.publix;

import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenNonLinearFlowException;
import exceptions.publix.ForbiddenReloadException;
import exceptions.publix.PublixException;
import general.common.Common;
import general.common.StudyLogger;
import models.common.*;
import models.common.ComponentResult.ComponentState;
import models.common.StudyResult.StudyState;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixHelpers;
import services.publix.PublixUtils;
import services.publix.StudyAuthorisation;
import services.publix.idcookie.IdCookieModel;
import services.publix.idcookie.IdCookieService;
import utils.common.HttpUtils;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Optional;

import static play.libs.Files.TemporaryFile;
import static play.mvc.Http.Request;

/**
 * Abstract controller class for all controllers that implement the IPublix
 * interface. It defines common methods and constants.
 *
 * @author Kristian Lange
 */
@Singleton
public abstract class Publix<T extends Worker> extends Controller implements IPublix {

    private static final ALogger LOGGER = Logger.of(Publix.class);

    protected final JPAApi jpa;
    protected final PublixUtils<T> publixUtils;
    protected final StudyAuthorisation<T> studyAuthorisation;
    protected final GroupChannel<T> groupChannel;
    protected final IdCookieService idCookieService;
    protected final PublixErrorMessages errorMessages;
    protected final StudyAssets studyAssets;
    protected final JsonUtils jsonUtils;
    protected final ComponentResultDao componentResultDao;
    protected final StudyResultDao studyResultDao;
    protected final StudyLogger studyLogger;
    protected final IOUtils ioUtils;

    public Publix(JPAApi jpa, PublixUtils<T> publixUtils,
            StudyAuthorisation<T> studyAuthorisation, GroupChannel<T> groupChannel,
            IdCookieService idCookieService, PublixErrorMessages errorMessages,
            StudyAssets studyAssets, JsonUtils jsonUtils, ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, StudyLogger studyLogger, IOUtils ioUtils) {
        this.jpa = jpa;
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.groupChannel = groupChannel;
        this.idCookieService = idCookieService;
        this.errorMessages = errorMessages;
        this.studyAssets = studyAssets;
        this.jsonUtils = jsonUtils;
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.studyLogger = studyLogger;
        this.ioUtils = ioUtils;
    }

    @Override
    public Result startComponent(Long studyId, Long componentId, Long studyResultId, String message)
            throws PublixException {
        LOGGER.info(".startComponent: studyId " + studyId + ", " + "componentId " + componentId + ", "
                + "studyResultId " + studyResultId + ", " + "message '" + message + "'");
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
        T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
        Component component = publixUtils.retrieveComponent(study, componentId);
        StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId);
        publixUtils.setPreStudyStateByComponentId(studyResult, study, componentId);

        ComponentResult componentResult;
        try {
            componentResult = publixUtils.startComponent(component, studyResult, message);
        } catch (ForbiddenReloadException | ForbiddenNonLinearFlowException e) {
            return redirect(controllers.publix.routes.PublixInterceptor
                    .finishStudy(studyId, studyResult.getId(), false, e.getMessage()));
        }

        idCookieService.writeIdCookie(worker, batch, studyResult, componentResult);
        return studyAssets.retrieveComponentHtmlFile(study.getDirName(),
                component.getHtmlFilePath()).asJava();
    }

    @Override
    public Result getInitData(Long studyId, Long componentId, Long studyResultId)
            throws PublixException, IOException {
        LOGGER.info(".getInitData: studyId " + studyId + ", " + "componentId "
                + componentId + ", " + "studyResultId " + studyResultId);
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
        T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
        Component component = publixUtils.retrieveComponent(study, componentId);
        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
        publixUtils.checkComponentBelongsToStudy(study, component);
        StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId);
        ComponentResult componentResult;
        try {
            componentResult = publixUtils.retrieveStartedComponentResult(component, studyResult);
        } catch (ForbiddenReloadException | ForbiddenNonLinearFlowException e) {
            return redirect(controllers.publix.routes.PublixInterceptor
                    .finishStudy(studyId, studyResult.getId(), false, e.getMessage()));
        }
        if (studyResult.getStudyState() != StudyState.PRE) {
            studyResult.setStudyState(StudyState.DATA_RETRIEVED);
        }
        studyResultDao.update(studyResult);
        componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
        componentResultDao.update(componentResult);

        return ok(jsonUtils.initData(batch, studyResult, study, component));
    }

    @Override
    public Result setStudySessionData(Long studyId, Long studyResultId) throws PublixException {
        LOGGER.info(".setStudySessionData: studyId " + studyId + ", "
                + "studyResultId " + studyResultId);
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
        T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
        StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId);
        String studySessionData = request().body().asText();
        studyResult.setStudySessionData(studySessionData);
        studyResultDao.update(studyResult);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    @Override
    public Result heartbeat(Long studyId, Long studyResultId) throws PublixException {
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
        Study study = publixUtils.retrieveStudy(studyId);
        T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
        StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId);
        studyResult.setLastSeenDate(new Timestamp(new Date().getTime()));
        studyResultDao.update(studyResult);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    @Override
    public Result submitResultData(Long studyId, Long componentId, Long studyResultId)
            throws PublixException {
        LOGGER.info(".submitResultData: studyId " + studyId + ", "
                + "componentId " + componentId + ", " + "studyResultId "
                + studyResultId);
        return submitOrAppendResultData(studyId, componentId, studyResultId, false);
    }

    @Override
    public Result appendResultData(Long studyId, Long componentId, Long studyResultId)
            throws PublixException {
        LOGGER.info(".appendResultData: studyId " + studyId + ", "
                + "componentId " + componentId + ", " + "studyResultId "
                + studyResultId);
        return submitOrAppendResultData(studyId, componentId, studyResultId, true);
    }

    private Result submitOrAppendResultData(Long studyId, Long componentId,
            Long studyResultId, boolean append) throws PublixException {
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
        T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
        Component component = publixUtils.retrieveComponent(study, componentId);
        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
        publixUtils.checkComponentBelongsToStudy(study, component);

        StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId);
        Optional<ComponentResult> componentResult = publixUtils.retrieveCurrentComponentResult(studyResult);
        if (!componentResult.isPresent()) {
            String error = PublixErrorMessages.componentNeverStarted(studyId, componentId, "submitResultData");
            return redirect(routes.PublixInterceptor.finishStudy(studyId, studyResult.getId(), false, error));
        }

        String postedResultData = request().body().asText();
        String resultData;
        if (append) {
            String currentResultData = componentResult.get().getData();
            resultData = currentResultData != null ? currentResultData + postedResultData : postedResultData;
        } else {
            resultData = postedResultData;
        }
        componentResult.get().setData(resultData);
        componentResult.get().setComponentState(ComponentState.RESULTDATA_POSTED);
        componentResultDao.update(componentResult.get());
        studyLogger.logResultDataStoring(componentResult.get());
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    @Override
    public Result uploadResultFile(Request request, Long studyId, Long componentId, Long studyResultId, String filename)
            throws PublixException {
        if (!Common.isResultUploadsEnabled()) return forbidden("File upload not allowed. Contact your admin.");

        IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
        T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
        Component component = publixUtils.retrieveComponent(study, componentId);
        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
        publixUtils.checkComponentBelongsToStudy(study, component);

        StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId);
        Optional<ComponentResult> componentResult = publixUtils.retrieveCurrentComponentResult(studyResult);
        if (!componentResult.isPresent()) {
            String error = PublixErrorMessages.componentNeverStarted(studyId, componentId, "uploadResultFile");
            return redirect(routes.PublixInterceptor.finishStudy(studyId, studyResult.getId(), false, error));
        }

        MultipartFormData<TemporaryFile> body = request.body().asMultipartFormData();
        MultipartFormData.FilePart<TemporaryFile> filePart = body.getFile("file");
        if (filePart == null) return badRequest("Missing file");
        TemporaryFile tmpFile = filePart.getRef();
        try {
            if (filePart.getFileSize() > Common.getResultUploadsMaxFileSize()) {
                return badRequest("File size too large");
            }
            if (ioUtils.getResultUploadDirSize(studyResultId) > Common.getResultUploadsLimitPerStudyRun()) {
                return badRequest("Reached max file size limit per study run");
            }
            if (!IOUtils.checkFilename(filename)) {
                return badRequest("Bad filename");
            }

            Path destFile = ioUtils.getResultUploadFileSecurely(
                    studyResultId, componentResult.get().getId(), filename).toPath();
            tmpFile.moveFileTo(destFile, true);
            studyLogger.logResultUploading(destFile, componentResult.get());
        } catch (IOException e) {
            return badRequest("File upload failed");
        }
        return ok("File uploaded");
    }

    @Override
    public Result downloadResultFile(Long studyId, Long studyResultId, String filename, Optional<Long> componentId)
            throws PublixException {
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
        T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
        Component component = null;
        if (componentId.isPresent()) {
            component = publixUtils.retrieveComponent(study, componentId.get());
            publixUtils.checkComponentBelongsToStudy(study, component);
        }
        StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId);
        Optional<File> file = publixUtils.retrieveLastUploadedResultFile(studyResult, component, filename);
        return file.isPresent() ? ok(file.get(), false) : notFound("Result file not found: " + filename);
    }

    @Override
    public Result abortStudy(Long studyId, Long studyResultId, String message)
            throws PublixException {
        LOGGER.info(".abortStudy: studyId " + studyId + ", " + ", "
                + "studyResultId " + studyResultId + ", " + "message '"
                + message + "'");
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
        T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

        StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId);
        if (!PublixHelpers.studyDone(studyResult)) {
            publixUtils.abortStudy(message, studyResult);
            groupChannel.closeGroupChannelAndLeaveGroup(studyResult);
        }
        idCookieService.discardIdCookie(studyResult.getId());
        studyLogger.log(study, "Aborted study run", worker);

        if (HttpUtils.isAjax()) {
            return ok(" "); // jQuery.ajax cannot handle empty responses
        } else {
            return ok(views.html.publix.abort.render());
        }
    }

    @Override
    public Result finishStudy(Long studyId, Long studyResultId,
            Boolean successful, String message) throws PublixException {
        LOGGER.info(".finishStudy: studyId " + studyId + ", " + "studyResultId "
                + studyResultId + ", " + "successful " + successful + ", "
                + "message '" + message + "'");
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
        T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

        StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId);
        if (!PublixHelpers.studyDone(studyResult)) {
            publixUtils.finishStudyResult(successful, message, studyResult);
            groupChannel.closeGroupChannelAndLeaveGroup(studyResult);
        }
        idCookieService.discardIdCookie(studyResult.getId());
        studyLogger.log(study, "Finished study run", worker);

        if (HttpUtils.isAjax()) {
            return ok(" "); // jQuery.ajax cannot handle empty responses
        } else {
            if (!successful) {
                return ok(views.html.publix.error.render(message));
            } else {
                return studyAssets.sendEndPageHtml(study.getDirName()).asJava();
            }
        }
    }

    @Override
    public Result log(Long studyId, Long componentId, Long studyResultId) throws PublixException {
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
        T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
        String msg = request().body().asText();
        LOGGER.info("logging from client: study ID " + studyId
                + ", component ID " + componentId + ", worker ID "
                + worker.getId() + ", study result ID " + studyResultId
                + ", message '" + msg + "'.");
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

}
