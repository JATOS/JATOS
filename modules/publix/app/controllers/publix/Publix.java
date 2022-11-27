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
import scala.Option;
import services.publix.PublixErrorMessages;
import services.publix.PublixHelpers;
import services.publix.PublixUtils;
import services.publix.StudyAuthorisation;
import services.publix.idcookie.IdCookieService;
import utils.common.Helpers;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Optional;

import static play.libs.Files.TemporaryFile;
import static play.mvc.Http.Request;

/**
 * Abstract parent controller class for all worker type Publix classes
 *
 * @author Kristian Lange
 */
@Singleton
public abstract class Publix<T extends Worker> extends Controller implements IPublix {

    private static final ALogger LOGGER = Logger.of(Publix.class);

    protected final JPAApi jpa;
    protected final PublixUtils publixUtils;
    protected final StudyAuthorisation studyAuthorisation;
    protected final GroupChannel<T> groupChannel;
    protected final IdCookieService idCookieService;
    protected final PublixErrorMessages errorMessages;
    protected final StudyAssets studyAssets;
    protected final JsonUtils jsonUtils;
    protected final ComponentResultDao componentResultDao;
    protected final StudyResultDao studyResultDao;
    protected final StudyLogger studyLogger;
    protected final IOUtils ioUtils;

    public Publix(JPAApi jpa, PublixUtils publixUtils,
            StudyAuthorisation studyAuthorisation, GroupChannel<T> groupChannel,
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
    public Result startComponent(Request request, StudyResult studyResult, Component component, String message)
            throws PublixException {
        Study study = studyResult.getStudy();

        ComponentResult componentResult;
        try {
            componentResult = publixUtils.startComponent(component, studyResult, message);
        } catch (ForbiddenReloadException | ForbiddenNonLinearFlowException e) {
            return redirect(controllers.publix.routes.PublixInterceptor
                    .finishStudy(studyResult.getUuid(), false, e.getMessage()));
        }

        publixUtils.setPreStudyState(componentResult);
        idCookieService.writeIdCookie(studyResult, componentResult);
        return studyAssets.retrieveComponentHtmlFile(study.getDirName(), component.getHtmlFilePath()).asJava();
    }

    @Override
    public Result getInitData(Request request, StudyResult studyResult, Component component)
            throws PublixException, IOException {
        Worker worker = studyResult.getWorker();
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request.session(), worker, study, batch);
        publixUtils.checkComponentBelongsToStudy(study, component);
        ComponentResult componentResult;
        try {
            componentResult = publixUtils.retrieveStartedComponentResult(component, studyResult);
        } catch (ForbiddenReloadException | ForbiddenNonLinearFlowException e) {
            return redirect(controllers.publix.routes.PublixInterceptor
                    .finishStudy(studyResult.getUuid(), false, e.getMessage()));
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
    public Result setStudySessionData(Request request, StudyResult studyResult) throws PublixException {
        Worker worker = studyResult.getWorker();
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request.session(), worker, study, batch);
        String studySessionData = request.body().asText();
        studyResultDao.updateStudySessionData(studyResult.getId(), studySessionData);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    @Override
    public Result heartbeat(Request request, StudyResult studyResult) {
        studyResult.setLastSeenDate(new Timestamp(new Date().getTime()));
        studyResultDao.update(studyResult);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    @Override
    public Result submitOrAppendResultData(Request request, StudyResult studyResult, Component component,
            boolean append) throws PublixException {
        Worker worker = studyResult.getWorker();
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request.session(), worker, study, batch);
        publixUtils.checkComponentBelongsToStudy(study, component);

        Optional<ComponentResult> componentResult = publixUtils.retrieveCurrentComponentResult(studyResult);
        if (!componentResult.isPresent()) {
            LOGGER.info(".submitOrAppendResultData: " + "studyResultId " + studyResult.getId() + ", "
                    + "componentId " + component.getId() + " - " + "Can't fetch current ComponentResult");
            return forbidden("Impossible to put result data to component result");
        }

        String postedResultData = request.body().asText();
        if (postedResultData == null) return badRequest("Result data empty");


        if (append) {
            componentResultDao.appendData(componentResult.get().getId(), postedResultData);
        } else {
            componentResultDao.replaceData(componentResult.get().getId(), postedResultData);
        }

        if (componentResult.get().getDataSize() + postedResultData.getBytes(StandardCharsets.UTF_8).length
                > Common.getResultDataMaxSize()) {
            String maxSize = Helpers.humanReadableByteCount(Common.getResultDataMaxSize());
            LOGGER.info(".submitOrAppendResultData: " + "studyResultId " + studyResult.getId() + ", "
                    + "componentId " + component.getId() + " - " + "Result data size exceeds allowed " + maxSize);
            return badRequest("Result data size exceeds allowed " + maxSize + ". Consider using result files instead.");
        }

        studyLogger.logResultDataStoring(componentResult.get(), postedResultData, append);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    @Override
    public Result uploadResultFile(Request request, StudyResult studyResult, Component component, String filename)
            throws PublixException {
        if (!Common.isResultUploadsEnabled()) {
            LOGGER.info(getLogForUploadResultFile(studyResult, component, filename, "File upload not allowed."));
            return forbidden("File upload not allowed. Contact your admin.");
        }

        Worker worker = studyResult.getWorker();
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request.session(), worker, study, batch);
        publixUtils.checkComponentBelongsToStudy(study, component);

        Optional<ComponentResult> componentResult = publixUtils.retrieveCurrentComponentResult(studyResult);
        if (!componentResult.isPresent()) {
            LOGGER.info(getLogForUploadResultFile(studyResult, component, filename,
                    "Can't fetch current ComponentResult"));
            return forbidden("Impossible to upload result file to component result");
        }

        MultipartFormData<TemporaryFile> body = request.body().asMultipartFormData();
        MultipartFormData.FilePart<TemporaryFile> filePart = body.getFile("file");
        if (filePart == null) {
            LOGGER.info(getLogForUploadResultFile(studyResult, component, filename, "Missing file"));
            return badRequest("Missing file");
        }
        TemporaryFile tmpFile = filePart.getRef();
        try {
            if (filePart.getFileSize() > Common.getResultUploadsMaxFileSize()) {
                LOGGER.info(getLogForUploadResultFile(studyResult, component, filename, "File size too large"));
                return badRequest("File size too large");
            }
            if (ioUtils.getResultUploadDirSize(studyResult.getId()) > Common.getResultUploadsLimitPerStudyRun()) {
                LOGGER.info(getLogForUploadResultFile(studyResult, component, filename,
                        "Reached max file size limit per study run"));
                return badRequest("Reached max file size limit per study run");
            }
            if (!IOUtils.checkFilename(filename)) {
                LOGGER.info(getLogForUploadResultFile(studyResult, component, filename, "Bad filename"));
                return badRequest("Bad filename");
            }

            Path destFile = ioUtils.getResultUploadFileSecurely(
                    studyResult.getId(), componentResult.get().getId(), filename).toPath();
            tmpFile.moveFileTo(destFile, true);
            studyLogger.logResultUploading(destFile, componentResult.get());
        } catch (IOException e) {
            LOGGER.info(getLogForUploadResultFile(studyResult, component, filename, "File upload failed"));
            return badRequest("File upload failed");
        }
        return ok("File uploaded");
    }

    private String getLogForUploadResultFile(StudyResult sr, Component c, String filename, String logMsg) {
        return ".uploadResultFile: studyResultId " + sr.getId() + ", " + "componentId " + c.getId()
                + ", " + "filename " + filename + " - " + logMsg;
    }

    @Override
    public Result downloadResultFile(Request request, StudyResult studyResult, String filename, String componentId)
            throws PublixException {
        Worker worker = studyResult.getWorker();
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request.session(), worker, study, batch);

        Component component = null;
        if (componentId != null) {
            component = publixUtils.retrieveComponent(study, Long.parseLong(componentId));
            publixUtils.checkComponentBelongsToStudy(study, component);
        }
        Optional<File> file = publixUtils.retrieveLastUploadedResultFile(studyResult, component, filename);
        return file.isPresent() ? ok(file.get(), false) : notFound("Result file not found: " + filename);
    }

    @Override
    public Result abortStudy(Request request, StudyResult studyResult, String message) throws PublixException {
        Worker worker = studyResult.getWorker();
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request.session(), worker, study, batch);

        if (!PublixHelpers.studyDone(studyResult)) {
            publixUtils.abortStudy(message, studyResult);
            groupChannel.closeGroupChannelAndLeaveGroup(studyResult);
        }
        idCookieService.discardIdCookie(studyResult.getId());
        studyLogger.log(study, "Aborted study run", worker);

        if (Helpers.isAjax()) {
            return ok(" "); // jQuery.ajax cannot handle empty responses
        } else {
            return ok(views.html.publix.abort.render());
        }
    }

    @Override
    public Result finishStudy(Request request, StudyResult studyResult, Boolean successful, String message)
            throws PublixException {
        Worker worker = studyResult.getWorker();
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request.session(), worker, study, batch);

        if (!PublixHelpers.studyDone(studyResult)) {
            publixUtils.finishStudyResult(successful, message, studyResult);
            groupChannel.closeGroupChannelAndLeaveGroup(studyResult);
        }
        idCookieService.discardIdCookie(studyResult.getId());
        studyLogger.log(study, "Finished study run", worker);

        if (Helpers.isAjax()) {
            return ok(" "); // jQuery.ajax cannot handle empty responses
        } else {
            if (!successful) {
                return ok(views.html.publix.error.render(message));
            } else {
                return redirect(routes.StudyAssets.endPage(studyResult.getUuid(), Option.empty()));
            }
        }
    }

    @Override
    public Result log(Request request, StudyResult studyResult, Component component) throws PublixException {
        Worker worker = studyResult.getWorker();
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request.session(), worker, study, batch);
        String msg = request.body().asText().replaceAll("\\R+", " ").replaceAll("\\s+", " ");
        LOGGER.info("Logging from client: studyResult " + studyResult.getId() + ", "
                + "batchId " + batch.getId() + ", "
                + "studyId " + study.getId() + ", "
                + "componentId" + component.getId() + ", "
                + "workerId " + worker.getId() + ", "
                + "message '" + msg + "'");
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

}
