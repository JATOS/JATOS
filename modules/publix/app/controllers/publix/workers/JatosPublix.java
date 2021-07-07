package controllers.publix.workers;

import controllers.publix.IPublix;
import controllers.publix.JatosGroupChannel;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.*;
import general.common.Common;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.JatosWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixHelpers;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieModel;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.JatosStudyAuthorisation;
import utils.common.Helpers;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of JATOS' public API for studies and components that are
 * started via JATOS' UI (run study or run component). A JATOS run is done by a
 * JatosWorker.
 *
 * Between the UI and Publix a session variable is used to pass on the
 * information whether it is a study run or a component run. In case it is a
 * component run there is a second session variable which contains the component
 * UUID.
 *
 * @author Kristian Lange
 */
@Singleton
public class JatosPublix extends Publix<JatosWorker> implements IPublix {

    private static final ALogger LOGGER = Logger.of(JatosPublix.class);

    /**
     * Distinguish between study run and component run. In case of an component
     * run additionally distinguish between the start or whether it is already
     * finished.
     */
    public enum JatosRun {
        RUN_STUDY, // A full study run
        RUN_COMPONENT_START, // Single component run just started
        RUN_COMPONENT_FINISHED // Single component run in finished state
    }

    /**
     * Name of a key in the session. It stores the username of the logged in JATOS user.
     */
    public static final String SESSION_USERNAME = "username";

    private final PublixUtils publixUtils;
    private final JatosStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final StudyLogger studyLogger;

    @Inject
    JatosPublix(JPAApi jpa, PublixUtils publixUtils,
            JatosStudyAuthorisation studyAuthorisation,
            ResultCreator resultCreator, JatosGroupChannel groupChannel,
            IdCookieService idCookieService, PublixErrorMessages errorMessages,
            StudyAssets studyAssets, JsonUtils jsonUtils,
            ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, StudyLogger studyLogger, IOUtils ioUtils) {
        super(jpa, publixUtils, studyAuthorisation, groupChannel,
                idCookieService, errorMessages, studyAssets, jsonUtils,
                componentResultDao, studyResultDao, studyLogger, ioUtils);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
        this.studyLogger = studyLogger;
    }

    @Override
    public Result startStudy(Http.Request request, StudyLink studyLink) throws PublixException {
        Batch batch = studyLink.getBatch();
        Study study = batch.getStudy();
        JatosWorker worker = publixUtils.retrieveLoggedInUser(request).getWorker();
        studyAuthorisation.checkWorkerAllowedToStartStudy(request, worker, study, batch);

        String componentUuid = null;
        JatosRun jatosRun = publixUtils.fetchJatosRunFromSession(request);
        switch (jatosRun) {
            case RUN_STUDY:
                componentUuid = publixUtils.retrieveFirstActiveComponent(study).getUuid();
                break;
            case RUN_COMPONENT_START:
                componentUuid = request.session().getOptional("run_component_uuid").orElse("unknown");
                break;
            case RUN_COMPONENT_FINISHED:
                throw new ForbiddenPublixException("This study was never started in JATOS.");
        }
        publixUtils.finishOldestStudyResult();
        StudyResult studyResult = resultCreator.createStudyResult(studyLink, worker);
        publixUtils.setUrlQueryParameter(request, studyResult);
        idCookieService.writeIdCookie(studyResult, jatosRun);

        String username = request.session().getOptional(JatosPublix.SESSION_USERNAME).orElse("unknown");
        LOGGER.info(".startStudy: studyLinkId " + studyLink.getId() + ", "
                + "studyResultId" + studyResult.getId() + ", "
                + "studyId " + study.getId() + ", "
                + "batchId " + batch.getId() + ", "
                + "logged-in username " + username + ", "
                + "workerId " + worker.getId());
        studyLogger.log(studyLink, "Started study run with " + JatosWorker.UI_WORKER_TYPE + " worker", worker);
        return redirect(controllers.publix.routes.PublixInterceptor
                .startComponent(studyResult.getUuid(), componentUuid, null))
                .removingFromSession(request, "jatos_run")
                .removingFromSession(request, "run_component_uuid");
    }

    @Override
    public Result startComponent(Http.Request request, StudyResult studyResult, Component component, String message)
            throws PublixException {
        IdCookieModel idCookie = idCookieService.getIdCookie(studyResult.getId());
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        JatosWorker worker = (JatosWorker) studyResult.getWorker();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request, worker, study, batch);
        publixUtils.checkComponentBelongsToStudy(study, component);

        // Check if it's a single component run or a whole study run
        JatosRun jatosRun = idCookie.getJatosRun();
        switch (jatosRun) {
            case RUN_STUDY:
                break;
            case RUN_COMPONENT_START:
                jatosRun = JatosRun.RUN_COMPONENT_FINISHED;
                break;
            case RUN_COMPONENT_FINISHED:
                ComponentResult lastComponentResult = studyResult.getLastComponentResult()
                        .orElseThrow(() -> new InternalServerErrorPublixException("Couldn't find last run component."));
                if (!lastComponentResult.getComponent().equals(component)) {
                    // It's already the second component (first is finished and it
                    // isn't a reload of the same one). Finish study after first component.
                    return redirect(controllers.publix.routes.PublixInterceptor
                            .finishStudy(studyResult.getUuid(), true, null));
                }
                break;
        }

        ComponentResult componentResult;
        try {
            componentResult = publixUtils.startComponent(component, studyResult, message);
        } catch (ForbiddenReloadException | ForbiddenNonLinearFlowException e) {
            return redirect(controllers.publix.routes.PublixInterceptor
                    .finishStudy(studyResult.getUuid(), false, e.getMessage()));
        }
        idCookieService.writeIdCookie(studyResult, componentResult, jatosRun);
        return studyAssets.retrieveComponentHtmlFile(study.getDirName(), component.getHtmlFilePath()).asJava()
                .removingFromSession(request, "jatos_run")
                .removingFromSession(request, "run_component_uuid");
    }

    @Override
    public Result abortStudy(Http.Request request, StudyResult studyResult, String message) throws PublixException {
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        JatosWorker worker = (JatosWorker) studyResult.getWorker();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request, worker, study, batch);

        if (!PublixHelpers.studyDone(studyResult)) {
            publixUtils.abortStudy(message, studyResult);
            groupChannel.closeGroupChannelAndLeaveGroup(studyResult);
        }
        idCookieService.discardIdCookie(studyResult.getId());
        studyLogger.log(study, "Aborted study run", worker);

        if (Helpers.isAjax()) {
            return ok(" "); // jQuery.ajax cannot handle empty responses
        }
        if (message != null) {
            return redirect(Common.getPlayHttpContext() + "jatos/" + study.getId())
                    .flashing("info", PublixErrorMessages.studyFinishedWithMessage(message));
        } else {
            return redirect(Common.getPlayHttpContext() + "jatos/" + study.getId());
        }
    }

    @Override
    public Result finishStudy(Http.Request request, StudyResult studyResult, Boolean successful, String message)
            throws PublixException {
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        JatosWorker worker = (JatosWorker) studyResult.getWorker();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request, worker, study, batch);

        if (!PublixHelpers.studyDone(studyResult)) {
            publixUtils.finishStudyResult(successful, message, studyResult);
            groupChannel.closeGroupChannelAndLeaveGroup(studyResult);
        }
        idCookieService.discardIdCookie(studyResult.getId());
        studyLogger.log(study, "Finished study run", worker);

        if (Helpers.isAjax()) {
            return ok(" "); // jQuery.ajax cannot handle empty responses
        }
        if (message != null) {
            return redirect(Common.getPlayHttpContext() + "jatos/" + study.getId())
                    .flashing("info", PublixErrorMessages.studyFinishedWithMessage(message));
        } else {
            return redirect(Common.getPlayHttpContext() + "jatos/" + study.getId());
        }
    }

}
