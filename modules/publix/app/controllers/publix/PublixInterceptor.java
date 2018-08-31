package controllers.publix;

import controllers.publix.actionannotation.PublixAccessLoggingAction.PublixAccessLogging;
import controllers.publix.workers.*;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.PublixException;
import models.common.workers.*;
import play.Application;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.IdCookieService;
import utils.common.HttpUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;

/**
 * Interceptor for Publix: it intercepts requests for JATOS' public API (Publix)
 * and forwards them to one of the implementations of the API (all extend
 * Publix). Each implementation deals with different workers (e.g. workers from
 * MechTurk, Personal Multiple workers).
 * <p>
 * When a study is started the implementation to use is determined by parameters
 * in the request's query string. Then the worker type is put into JATOS' ID
 * cookie (IdCookie) and used in subsequent requests of the same study run.
 * <p>
 * 1. Requests coming from Jatos' UI run (if clicked on run study/component
 * button) run will be forwarded to JatosPublix. They use JatosWorker.<br>
 * 2. Requests coming from a Personal Single run will be forwarded to
 * PersonalSinglePublix. They use PersonalSingleWorker.<br>
 * 3. Requests coming from a Personal Multiple run will be forwarded to
 * PersonalMultiplePublix. They use PersonalMultipleWorker.<br>
 * 4. Requests coming from an General Single run will be forwarded to
 * GeneralSinglePublix. They use the GeneralSingleWorker.<br>
 * 5. Requests coming from an General Multiple run will be forwarded to
 * GeneralMultiplePublix. They use the GeneralMultipleWorker.<br>
 * 6. Requests coming from MechTurk or MechTurk Sandbox will be forwarded to
 * MTPublix. They use MTWorker and MTSandboxWorker.<br>
 *
 * @author Kristian Lange
 */
@Singleton
@PublixAccessLogging
public class PublixInterceptor extends Controller implements IPublix {

    private final IdCookieService idCookieService;
    private final Provider<Application> application;

    @Inject
    public PublixInterceptor(IdCookieService idCookieService, Provider<Application> application) {
        this.idCookieService = idCookieService;
        this.application = application;
    }

    @Override
    @Transactional
    public Result startStudy(Long studyId, Long batchId) throws PublixException {
        Result result;
        String workerType = getWorkerTypeFromQuery();
        switch (workerType) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).startStudy(studyId, batchId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).startStudy(studyId, batchId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .startStudy(studyId, batchId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).startStudy(studyId, batchId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).startStudy(studyId, batchId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).startStudy(studyId, batchId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result startComponent(Long studyId, Long componentId, Long studyResultId)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class)
                        .startComponent(studyId, componentId, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .startComponent(studyId, componentId, studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .startComponent(studyId, componentId, studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .startComponent(studyId, componentId, studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .startComponent(studyId, componentId, studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).startComponent(studyId,
                        componentId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result startComponentByPosition(Long studyId, Integer position, Long studyResultId)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class)
                        .startComponentByPosition(studyId, position, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .startComponentByPosition(studyId, position, studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .startComponentByPosition(studyId, position, studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .startComponentByPosition(studyId, position, studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .startComponentByPosition(studyId, position, studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class)
                        .startComponentByPosition(studyId, position, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result startNextComponent(Long studyId, Long studyResultId) throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class)
                        .startNextComponent(studyId, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .startNextComponent(studyId, studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .startNextComponent(studyId, studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .startNextComponent(studyId, studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .startNextComponent(studyId, studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class)
                        .startNextComponent(studyId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result getInitData(Long studyId, Long componentId, Long studyResultId)
            throws PublixException, IOException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class)
                        .getInitData(studyId, componentId, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .getInitData(studyId, componentId, studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .getInitData(studyId, componentId, studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .getInitData(studyId, componentId, studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .getInitData(studyId, componentId, studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class)
                        .getInitData(studyId, componentId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result setStudySessionData(Long studyId, Long studyResultId) throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class)
                        .setStudySessionData(studyId, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .setStudySessionData(studyId, studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .setStudySessionData(studyId, studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .setStudySessionData(studyId, studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .setStudySessionData(studyId, studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class)
                        .setStudySessionData(studyId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result heartbeat(Long studyId, Long studyResultId) throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).heartbeat(studyId,
                        studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .heartbeat(studyId, studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .heartbeat(studyId, studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .heartbeat(studyId, studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .heartbeat(studyId, studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).heartbeat(studyId,
                        studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result submitResultData(Long studyId, Long componentId, Long studyResultId)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class)
                        .submitResultData(studyId, componentId, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .submitResultData(studyId, componentId, studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .submitResultData(studyId, componentId, studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .submitResultData(studyId, componentId, studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .submitResultData(studyId, componentId, studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class)
                        .submitResultData(studyId, componentId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result appendResultData(Long studyId, Long componentId, Long studyResultId)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class)
                        .appendResultData(studyId, componentId, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .appendResultData(studyId, componentId, studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .appendResultData(studyId, componentId, studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .appendResultData(studyId, componentId, studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .appendResultData(studyId, componentId, studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class)
                        .appendResultData(studyId, componentId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result finishComponent(Long studyId, Long componentId, Long studyResultId,
            Boolean successful, String errorMsg) throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).finishComponent(
                        studyId, componentId, studyResultId, successful, errorMsg);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .finishComponent(studyId, componentId, studyResultId, successful, errorMsg);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .finishComponent(studyId, componentId, studyResultId, successful, errorMsg);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .finishComponent(studyId, componentId, studyResultId, successful, errorMsg);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .finishComponent(studyId, componentId, studyResultId, successful, errorMsg);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).finishComponent(studyId,
                        componentId, studyResultId, successful, errorMsg);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result abortStudy(Long studyId, Long studyResultId, String message)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class)
                        .abortStudy(studyId, studyResultId, message);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .abortStudy(studyId, studyResultId, message);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .abortStudy(studyId, studyResultId, message);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .abortStudy(studyId, studyResultId, message);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .abortStudy(studyId, studyResultId, message);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class)
                        .abortStudy(studyId, studyResultId, message);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result finishStudy(Long studyId, Long studyResultId, Boolean successful, String errorMsg)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class)
                        .finishStudy(studyId, studyResultId, successful, errorMsg);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class)
                        .finishStudy(studyId, studyResultId, successful, errorMsg);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class)
                        .finishStudy(studyId, studyResultId, successful, errorMsg);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class)
                        .finishStudy(studyId, studyResultId, successful, errorMsg);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class)
                        .finishStudy(studyId, studyResultId, successful, errorMsg);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class)
                        .finishStudy(studyId, studyResultId, successful, errorMsg);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @Transactional
    public Result log(Long studyId, Long componentId, Long studyResultId)
            throws PublixException {
        switch (getWorkerTypeFromIdCookie(studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class)
                        .log(studyId, componentId, studyResultId);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class)
                        .log(studyId, componentId, studyResultId);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class)
                        .log(studyId, componentId, studyResultId);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class)
                        .log(studyId, componentId, studyResultId);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class)
                        .log(studyId, componentId, studyResultId);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class)
                        .log(studyId, componentId, studyResultId);
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
    }

    /**
     * Uses Guice to create a new instance of the given class, a class that must
     * inherit from Publix.
     */
    private <T extends Publix<?>> T instanceOfPublix(Class<T> publixClass) {
        return application.get().injector().instanceOf(publixClass);
    }

    /**
     * Checks JATOS' ID cookie for which type of worker is doing the study.
     * Returns a String specifying the worker type.
     */
    private String getWorkerTypeFromIdCookie(Long studyResultId) throws PublixException {
        if (studyResultId == null) {
            throw new BadRequestPublixException("Study result doesn't exist.");
        }
        return idCookieService.getIdCookie(studyResultId).getWorkerType();
    }

    /**
     * Checks the request's query string which type of worker is doing the
     * study. Returns a String specifying the worker type. Before a study is
     * started the worker type is specified via a parameter in the query string.
     */
    private String getWorkerTypeFromQuery() throws BadRequestPublixException {
        // Check for JATOS worker
        String jatosWorkerId = HttpUtils.getQueryString(JatosPublix.JATOS_WORKER_ID);
        if (jatosWorkerId != null) {
            return JatosWorker.WORKER_TYPE;
        }
        // Check for MT worker and MT Sandbox worker
        String mtWorkerId = HttpUtils.getQueryString(MTPublix.MT_WORKER_ID);
        if (mtWorkerId != null) {
            return instanceOfPublix(MTPublix.class).retrieveWorkerType();
        }
        // Check for Personal Single Worker
        String personalSingleWorkerId = HttpUtils
                .getQueryString(PersonalSinglePublix.PERSONAL_SINGLE_WORKER_ID);
        if (personalSingleWorkerId != null) {
            return PersonalSingleWorker.WORKER_TYPE;
        }
        // Check for Personal Multiple Worker
        String pmWorkerId = HttpUtils.getQueryString(
                PersonalMultiplePublix.PERSONAL_MULTIPLE_WORKER_ID);
        if (pmWorkerId != null) {
            return PersonalMultipleWorker.WORKER_TYPE;
        }
        // Check for General Single Worker
        String generalSingle = HttpUtils
                .getQueryString(GeneralSinglePublix.GENERALSINGLE);
        if (generalSingle != null) {
            return GeneralSingleWorker.WORKER_TYPE;
        }
        // Check for General Multiple Worker
        String generalMultiple = HttpUtils
                .getQueryString(GeneralMultiplePublix.GENERALMULTIPLE);
        if (generalMultiple != null) {
            return GeneralMultipleWorker.WORKER_TYPE;
        }
        throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
    }

}
