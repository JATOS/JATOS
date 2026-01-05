package controllers.publix;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction;
import actions.common.TransactionalAction.Transactional;
import com.google.common.base.Strings;
import daos.common.ComponentDao;
import daos.common.StudyLinkDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerType;
import exceptions.common.BadRequestException;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
import filters.publix.IdCookieFilter;
import filters.publix.IdCookieFilter.IdCookies;
import models.common.Component;
import models.common.Study;
import models.common.StudyLink;
import models.common.StudyResult;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixHelpers;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Interceptor for Publix: handles all requests for JATOS' public API (Publix) and forwards them to one of the
 * implementations of the API (all extend Publix). Each implementation deals with different workers (e.g., workers from
 * MTurk, Personal Multiple workers).
 *
 * A study run starts with the 'run' method that takes the study code as a parameter. The study code is the ID for a
 * StudyLink. The StudyLink determines the worker type and which Publix implementation will be called. All later
 * requests of this study run need at least the study result UUID and often the component UUID too.
 *
 * @author Kristian Lange
 */
@Singleton
public class PublixInterceptor extends Controller {

    private static final Logger.ALogger LOGGER = Logger.of(PublixInterceptor.class);

    private final PublixDispatcher publixDispatcher;
    private final StudyLinkDao studyLinkDao;
    private final StudyResultDao studyResultDao;
    private final ComponentDao componentDao;

    @Inject
    public PublixInterceptor(PublixDispatcher publixDispatcher,
                             StudyLinkDao studyLinkDao,
                             StudyResultDao studyResultDao,
                             ComponentDao componentDao) {
        this.publixDispatcher = publixDispatcher;
        this.studyLinkDao = studyLinkDao;
        this.studyResultDao = studyResultDao;
        this.componentDao = componentDao;
    }

    // @formatter:off
    /**
     * Shows the Study Entry page prior to a study run.
     * 1. Lets worker enter the study code
     * 2. Takes the study code as a query parameter. A text shown can be customized in the study properties.
     * It always shows a ▶ button that the worker has to press to confirm the intention of running the study.
     */
    @Async(Executor.IO)
    public Result studyEntry(Http.Request request, String studyCode) {
        String studyEntryMsg = null;
        String errMsg = null;
        boolean validStudyLink = false;

        if (!Strings.isNullOrEmpty(studyCode)) {
            StudyLink studyLink = studyLinkDao.findByStudyCode(studyCode);
            if (studyLink != null) {
                validStudyLink = true;
                studyEntryMsg = studyLink.getBatch().getStudy().getStudyEntryMsg();

                if (studyLink.getWorkerType() == WorkerType.PERSONAL_SINGLE) {
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

    /**
     * Facilitates multiple study runs in parallel, each in its own iframe. If 'frames' is 1 (or lower), it just redirects
     * to the normal run endpoint.
     */
    public Result runx(String code, Long frames, Long hSplit, Long vSplit) {
        LOGGER.info(".runx: code " + code + ", frames " + frames + ", hSplit " + hSplit + ", vSplit " + vSplit);
        if (Strings.isNullOrEmpty(code)) {
            throw new BadRequestException("Invalid study code");
        } else if (frames > 1) {
            return ok(views.html.publix.runx.render(code, frames, hSplit, vSplit));
        } else {
            return redirect(controllers.publix.routes.PublixInterceptor.run(code));
        }
    }

    @IdCookies
    @Async(Executor.IO)
    @Transactional
    public Result run(Http.Request request, String studyCode) {
        LOGGER.info(".run: studyCode " + studyCode);
        StudyLink studyLink = studyLinkDao.findByStudyCode(studyCode);
        if (studyLink == null) throw new BadRequestException("No valid study link");
        if (!studyLink.isActive()) throw new ForbiddenException("This study link is inactive");

        return publixDispatcher
                .forWorkerType(studyLink.getWorkerType())
                .startStudy(request, studyLink);
    }

    @IdCookies
    @Async(Executor.IO)
    @Transactional
    public Result startComponent(Http.Request request, String studyResultUuid, String componentUuid, String message) {
            StudyResult studyResult = fetchStudyResult(studyResultUuid);
            Component component = fetchComponent(componentUuid, studyResult.getStudy());
            checkStudyResultAndComponent(studyResult, component);
            LOGGER.info(".startComponent: studyResultId " + studyResult.getId() + ", " + "componentId " + component.getId());

            return publixDispatcher
                    .forWorkerType(studyResult.getWorkerType())
                    .startComponent(request, studyResult, component, message);
    }

    @Async(Executor.IO)
    @Transactional
    public Result getInitData(Http.Request request, String studyResultUuid, String componentUuid) {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        Component component = fetchComponent(componentUuid, studyResult.getStudy());
        checkStudyResultAndComponent(studyResult, component);
        LOGGER.info(".getInitData: studyResultId " + studyResult.getId() + ", " + "componentId " + component.getId());

        return publixDispatcher
                .forWorkerType(studyResult.getWorkerType())
                .getInitData(request, studyResult, component);
    }

    @Async(Executor.IO)
    @Transactional
    public Result setStudySessionData(Http.Request request, String studyResultUuid) {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        LOGGER.info(".setStudySessionData: studyResultId " + studyResult.getId());

        return publixDispatcher
                .forWorkerType(studyResult.getWorkerType())
                .setStudySessionData(request, studyResult);
    }

    @Async(Executor.IO)
    public Result heartbeat(Http.Request request, String studyResultUuid) {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);

        return publixDispatcher
                .forWorkerType(studyResult.getWorkerType())
                .heartbeat(request, studyResult);
    }

    @Async(Executor.IO)
    @Transactional
    public Result submitResultData(Http.Request request, String studyResultUuid, String componentUuid) {
        return submitOrAppendResultData(request, studyResultUuid, componentUuid, false);
    }

    @Async(Executor.IO)
    @Transactional
    public Result appendResultData(Http.Request request, String studyResultUuid, String componentUuid) {
        return submitOrAppendResultData(request, studyResultUuid, componentUuid, true);
    }

    private Result submitOrAppendResultData(Http.Request request, String studyResultUuid, String componentUuid,
                                            boolean append) {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        Component component = fetchComponent(componentUuid, studyResult.getStudy());
        checkStudyResultAndComponent(studyResult, component);
        LOGGER.info(".submitOrAppendResultData: studyResultId " + studyResult.getId() + ", "
                + "componentId " + component.getId());

        return publixDispatcher
                .forWorkerType(studyResult.getWorkerType())
                .submitOrAppendResultData(request, studyResult, component, append);
    }

    @Async(Executor.IO)
    @Transactional
    public Result uploadResultFile(Http.Request request, String studyResultUuid, String componentUuid,
                                   String filename) {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        Component component = fetchComponent(componentUuid, studyResult.getStudy());
        checkStudyResultAndComponent(studyResult, component);
        LOGGER.info(".uploadResultFile: studyResultId " + studyResult.getId() + ", "
                + "componentId " + component.getId() + ", " + "filename " + filename);

        return publixDispatcher
                .forWorkerType(studyResult.getWorkerType())
                .uploadResultFile(request, studyResult, component, filename);
    }

    @Async(Executor.IO)
    @Transactional
    public Result downloadResultFile(Http.Request request, String studyResultUuid, String filename, String componentId) {
            StudyResult studyResult = fetchStudyResult(studyResultUuid);
            LOGGER.info(".downloadResultFile: studyResultId " + studyResult.getId() + ", "
                    + "componentId " + componentId + ", "
                    + "filename " + filename);

            return publixDispatcher
                    .forWorkerType(studyResult.getWorkerType())
                    .downloadResultFile(request, studyResult, filename, componentId);
    }

    @Async(Executor.IO)
    @Transactional
    public Result abortStudy(Http.Request request, String studyResultUuid, String message) {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        LOGGER.info(".abortStudy: studyResultId " + studyResult.getId());

        return publixDispatcher
                .forWorkerType(studyResult.getWorkerType())
                .abortStudy(request, studyResult, message);
    }

    @Async(Executor.IO)
    @Transactional
    public Result finishStudy(Http.Request request, String studyResultUuid, Boolean successful, String message) {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        LOGGER.info(".finishStudy: studyResultId " + studyResult.getId() + ", " + "successful " + successful);

        return publixDispatcher
                .forWorkerType(studyResult.getWorkerType())
                .finishStudy(request, studyResult, successful, message);
    }

    @Async(Executor.IO)
    @Transactional
    public Result log(Http.Request request, String studyResultUuid, String componentUuid) {
        StudyResult studyResult = fetchStudyResult(studyResultUuid);
        Component component = fetchComponent(componentUuid, studyResult.getStudy());
        checkStudyResultAndComponent(studyResult, component);

        return publixDispatcher
                .forWorkerType(studyResult.getWorkerType())
                .log(request, studyResult, component);
    }

    private StudyResult fetchStudyResult(String uuid) {
        if (uuid == null || uuid.equals("undefined")) {
            throw new ForbiddenException("Error getting study result UUID");
        }
        return studyResultDao.findByUuid(uuid)
                .orElseThrow(() -> new BadRequestException("Study result " + uuid + " doesn't exist."));
    }

    private Component fetchComponent(String uuid, Study study) {
        if (uuid == null || uuid.equals("undefined")) {
            throw new ForbiddenException("Error getting component UUID");
        }
        return componentDao.findByUuid(uuid, study)
                .orElseThrow(() -> new NotFoundException("Component " + uuid + " doesn't exist."));
    }

    private void checkStudyResultAndComponent(StudyResult studyResult, Component component) {
        if (PublixHelpers.studyRunDone(studyResult)) {
            throw new ForbiddenException(
                    "Study run is already finished (study result " + studyResult.getId() + ")");
        }
        if (!studyResult.getStudy().hasComponent(component)) {
            throw new BadRequestException(
                    "Component " + component.getUuid() + " does not belong to study result " + studyResult.getUuid());
        }
        if (!component.isActive()) {
            throw new ForbiddenException("Component " + component.getId() + " is not active.");
        }
    }

}
