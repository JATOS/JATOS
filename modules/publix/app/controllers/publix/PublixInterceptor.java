package controllers.publix;

import com.google.common.base.Strings;
import controllers.publix.actionannotation.PublixAccessLoggingAction.PublixAccessLogging;
import controllers.publix.workers.*;
import daos.common.ComponentDao;
import daos.common.StudyLinkDao;
import daos.common.StudyResultDao;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;
import models.common.Component;
import models.common.StudyLink;
import models.common.StudyResult;
import models.common.workers.*;
import play.Application;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixHelpers;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

/**
 * Interceptor for Publix: handles all requests for JATOS' public API (Publix) and forwards them to one of the
 * implementations of the API (all extend Publix). Each implementation deals with different workers (e.g. workers from
 * MTurk, Personal Multiple workers).
 *
 * A study run starts with the 'run' method that takes the study code as a parameter. The study code is the ID for a
 * StudyLink. The StudyLink determines the worker type and which Publix implementation will be called. All subsequent
 * requests of this study run need at least the study result UUID and often the component UUID too.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
@PublixAccessLogging
public class PublixInterceptor extends Controller {

    private static final Logger.ALogger LOGGER = Logger.of(PublixInterceptor.class);

    private final StudyLinkDao studyLinkDao;
    private final StudyResultDao studyResultDao;
    private final ComponentDao componentDao;
    private final Provider<Application> application;

    @Inject
    public PublixInterceptor(StudyLinkDao studyLinkDao, StudyResultDao studyResultDao, ComponentDao componentDao,
            Provider<Application> application) {
        this.studyLinkDao = studyLinkDao;
        this.studyResultDao = studyResultDao;
        this.componentDao = componentDao;
        this.application = application;
    }

    /**
     * Shows the Study Entry page prior to a study run.
     * 1. Lets worker enter the study code
     * 2. Takes the study code as a query parameter. An text shown can be customized in the study properties.
     * It always shows a ▶ button that the worker has to press to confirm the intention of running the study.
     */
    @Transactional
    public Result studyEntry(Http.Request request, String studyCode) {
        String studyEntryMsg = null;
        String errMsg = null;
        boolean validStudyLink = false;

        if (!Strings.isNullOrEmpty(studyCode)) {
            StudyLink studyLink = studyLinkDao.findByStudyCode(studyCode);
            if (studyLink != null) {
                validStudyLink = true;
                studyEntryMsg = studyLink.getBatch().getStudy().getStudyEntryMsg();

                String workerType = studyLink.getWorkerType();
                if (workerType.equals(PersonalSingleWorker.WORKER_TYPE)
                        || workerType.equals(GeneralSingleWorker.WORKER_TYPE)) {
                    Optional<StudyResult> srOptional = studyResultDao.findByStudyCode(studyCode);
                    if (srOptional.isPresent() && !srOptional.get().getStudyState().equals(
                            StudyResult.StudyState.PRE)) {
                        validStudyLink = false;
                        errMsg = "This study link is valid only once.";
                    }
                }
            } else {
                errMsg = "No valid study code";
            }
        }
        return ok(views.html.publix.studyEntry.render(studyCode, validStudyLink, Helpers.getQueryString(request),
                studyEntryMsg, errMsg));
    }

    @Transactional
    public Result run(Http.Request request, String studyCode) throws PublixException {
        LOGGER.info(".run: studyCode " + studyCode);
        StudyLink studyLink = studyLinkDao.findByStudyCode(studyCode);
        if (studyLink == null) throw new BadRequestPublixException("No valid study link");
        if (!studyLink.isActive()) throw new ForbiddenPublixException("This study link is inactive");

        switch (studyLink.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class).startStudy(request, studyLink);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class).startStudy(request, studyLink);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class).startStudy(request, studyLink);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class).startStudy(request, studyLink);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class).startStudy(request, studyLink);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class).startStudy(request, studyLink);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    @Transactional
    public Result startComponent(Http.Request request, String studyResultUuid, String componentUuid, String message)
            throws PublixException {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        Component component = fetchComponent(componentUuid);
        checkStudyResultAndComponent(studyResult, component);
        LOGGER.info(".startComponent: studyResultId " + studyResult.getId() + ", "
                + "componentId " + component.getId());

        switch (studyResult.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class)
                        .startComponent(request, studyResult, component, message);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class)
                        .startComponent(request, studyResult, component, message);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class)
                        .startComponent(request, studyResult, component, message);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class)
                        .startComponent(request, studyResult, component, message);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class)
                        .startComponent(request, studyResult, component, message);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class).startComponent(request, studyResult, component, message);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    @Transactional
    public Result getInitData(Http.Request request, String studyResultUuid, String componentUuid)
            throws PublixException, IOException {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        Component component = fetchComponent(componentUuid);
        checkStudyResultAndComponent(studyResult, component);
        LOGGER.info(".getInitData: studyResultId " + studyResult.getId() + ", " + "componentId " + component.getId());

        switch (studyResult.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class).getInitData(request, studyResult, component);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class).getInitData(request, studyResult, component);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class).getInitData(request, studyResult, component);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class).getInitData(request, studyResult, component);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class).getInitData(request, studyResult, component);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class).getInitData(request, studyResult, component);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    @Transactional
    public Result setStudySessionData(Http.Request request, String studyResultUuid) throws PublixException {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        LOGGER.info(".setStudySessionData: studyResultId " + studyResult.getId());

        switch (studyResult.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class).setStudySessionData(request, studyResult);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class).setStudySessionData(request, studyResult);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class).setStudySessionData(request, studyResult);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class).setStudySessionData(request, studyResult);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class).setStudySessionData(request, studyResult);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class).setStudySessionData(request, studyResult);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    @Transactional
    public Result heartbeat(Http.Request request, String studyResultUuid) throws PublixException {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        LOGGER.info(".heartbeat: studyResultId " + studyResult.getId());

        switch (studyResult.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class).heartbeat(request, studyResult);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class).heartbeat(request, studyResult);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class).heartbeat(request, studyResult);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class).heartbeat(request, studyResult);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class).heartbeat(request, studyResult);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class).heartbeat(request, studyResult);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    @Transactional
    public Result submitResultData(Http.Request request, String studyResultUuid, String componentUuid)
            throws PublixException {
        return submitOrAppendResultData(request, studyResultUuid, componentUuid, false);
    }

    @Transactional
    public Result appendResultData(Http.Request request, String studyResultUuid, String componentUuid)
            throws PublixException {
        return submitOrAppendResultData(request, studyResultUuid, componentUuid, true);
    }

    private Result submitOrAppendResultData(Http.Request request, String studyResultUuid, String componentUuid,
            boolean append) throws PublixException {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        Component component = fetchComponent(componentUuid);
        checkStudyResultAndComponent(studyResult, component);
        LOGGER.info(".submitOrAppendResultData: studyResultId " + studyResult.getId() + ", "
                + "componentId " + component.getId());

        switch (studyResult.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class)
                        .submitOrAppendResultData(request, studyResult, component, append);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class)
                        .submitOrAppendResultData(request, studyResult, component, append);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class)
                        .submitOrAppendResultData(request, studyResult, component, append);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class)
                        .submitOrAppendResultData(request, studyResult, component, append);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class)
                        .submitOrAppendResultData(request, studyResult, component, append);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class)
                        .submitOrAppendResultData(request, studyResult, component, append);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    @Transactional
    public Result uploadResultFile(Http.Request request, String studyResultUuid, String componentUuid, String filename)
            throws PublixException {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        Component component = fetchComponent(componentUuid);
        checkStudyResultAndComponent(studyResult, component);
        LOGGER.info(".uploadResultFile: studyResultId " + studyResult.getId() + ", "
                + "componentId " + component.getId() + ", " + "filename " + filename);

        switch (studyResult.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class)
                        .uploadResultFile(request, studyResult, component, filename);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class)
                        .uploadResultFile(request, studyResult, component, filename);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class)
                        .uploadResultFile(request, studyResult, component, filename);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class)
                        .uploadResultFile(request, studyResult, component, filename);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class)
                        .uploadResultFile(request, studyResult, component, filename);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class)
                        .uploadResultFile(request, studyResult, component, filename);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    @Transactional
    public Result downloadResultFile(Http.Request request, String studyResultUuid, String filename, String componentId)
            throws PublixException {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        LOGGER.info(".downloadResultFile: studyResultId " + studyResult.getId() + ", "
                + "componentId " + componentId + ", "
                + "filename " + filename);

        switch (studyResult.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class)
                        .downloadResultFile(request, studyResult, filename, componentId);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class)
                        .downloadResultFile(request, studyResult, filename, componentId);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class)
                        .downloadResultFile(request, studyResult, filename, componentId);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class)
                        .downloadResultFile(request, studyResult, filename, componentId);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class)
                        .downloadResultFile(request, studyResult, filename, componentId);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class)
                        .downloadResultFile(request, studyResult, filename, componentId);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    @Transactional
    public Result abortStudy(Http.Request request, String studyResultUuid, String message) throws PublixException {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        LOGGER.info(".abortStudy: studyResultId " + studyResult.getId());

        switch (studyResult.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class).abortStudy(request, studyResult, message);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class).abortStudy(request, studyResult, message);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class).abortStudy(request, studyResult, message);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class).abortStudy(request, studyResult, message);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class).abortStudy(request, studyResult, message);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class).abortStudy(request, studyResult, message);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    @Transactional
    public Result finishStudy(Http.Request request, String studyResultUuid, Boolean successful, String message)
            throws PublixException {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        LOGGER.info(".finishStudy: studyResultId " + studyResult.getId() + ", " + "successful " + successful);

        switch (studyResult.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class)
                        .finishStudy(request, studyResult, successful, message);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class)
                        .finishStudy(request, studyResult, successful, message);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class)
                        .finishStudy(request, studyResult, successful, message);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class)
                        .finishStudy(request, studyResult, successful, message);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class)
                        .finishStudy(request, studyResult, successful, message);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class)
                        .finishStudy(request, studyResult, successful, message);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    @Transactional
    public Result log(Http.Request request, String studyResultUuid, String componentUuid) throws PublixException {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        Component component = fetchComponent(componentUuid);
        checkStudyResultAndComponent(studyResult, component);

        switch (studyResult.getWorkerType()) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class).log(request, studyResult, component);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class).log(request, studyResult, component);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class).log(request, studyResult, component);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class).log(request, studyResult, component);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class).log(request, studyResult, component);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class).log(request, studyResult, component);
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }
    }

    private StudyResult fetchStudyResult(String uuid) throws ForbiddenPublixException, BadRequestPublixException {
        if (uuid == null || uuid.equals("undefined")) {
            throw new ForbiddenPublixException("Error getting study result UUID");
        }
        return studyResultDao.findByUuid(uuid)
                .orElseThrow(() -> new BadRequestPublixException("Study result " + uuid + " doesn't exist."));
    }

    private Component fetchComponent(String uuid) throws NotFoundPublixException, ForbiddenPublixException {
        if (uuid == null || uuid.equals("undefined")) {
            throw new ForbiddenPublixException("Error getting component UUID");
        }
        return componentDao.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundPublixException("Component " + uuid + " doesn't exist."));
    }

    private void checkStudyResultAndComponent(StudyResult studyResult, Component component)
            throws BadRequestPublixException, ForbiddenPublixException {
        if (PublixHelpers.studyDone(studyResult)) {
            throw new ForbiddenPublixException(
                    "Study run is already finished (study result " + studyResult.getId() + ")");
        }
        if (!studyResult.getStudy().hasComponent(component)) {
            throw new BadRequestPublixException(
                    "Component " + component.getUuid() + " does not belong to study result " + studyResult.getUuid());
        }
        if (!component.isActive()) {
            throw new ForbiddenPublixException("Component " + component.getId() + " is not active.");
        }
    }

    /**
     * Uses Guice to create a new instance of the given class, a class that must inherit from Publix.
     */
    private <T extends Publix<?>> T instanceOfPublix(Class<T> publixClass) {
        return application.get().injector().instanceOf(publixClass);
    }

}
