package services.publix;

import controllers.publix.workers.JatosPublix;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.common.BadRequestException;
import exceptions.common.ForbiddenException;
import exceptions.common.IOException;
import exceptions.common.NotFoundException;
import exceptions.publix.ForbiddenNonLinearFlowException;
import exceptions.publix.ForbiddenReloadException;
import general.common.StudyLogger;
import group.GroupAdministration;
import models.common.*;
import models.common.ComponentResult.ComponentState;
import models.common.StudyResult.StudyState;
import models.common.workers.Worker;
import play.Logger;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import services.publix.idcookie.IdCookieService;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import java.io.File;
import java.sql.Timestamp;
import java.util.*;

/**
 * Service class with functions that are common for all classes that extend Publix and don't belong in a controller.
 *
 * @author Kristian Lange
 */
public class PublixUtils {

    private static final Logger.ALogger LOGGER = Logger.of(PublixUtils.class);

    private final JPAApi jpa;
    private final ResultCreator resultCreator;
    private final IdCookieService idCookieService;
    private final GroupAdministration groupAdministration;
    private final StudyResultDao studyResultDao;
    private final ComponentDao componentDao;
    private final ComponentResultDao componentResultDao;
    private final WorkerDao workerDao;
    private final UserDao userDao;
    private final StudyLogger studyLogger;
    private final IOUtils ioUtils;

    @Inject
    public PublixUtils(JPAApi jpa,
                       ResultCreator resultCreator,
                       IdCookieService idCookieService,
                       GroupAdministration groupAdministration,
                       StudyResultDao studyResultDao,
                       ComponentDao componentDao,
                       ComponentResultDao componentResultDao,
                       WorkerDao workerDao,
                       UserDao userDao,
                       StudyLogger studyLogger,
                       IOUtils ioUtils) {
        this.jpa = jpa;
        this.resultCreator = resultCreator;
        this.idCookieService = idCookieService;
        this.groupAdministration = groupAdministration;
        this.studyResultDao = studyResultDao;
        this.componentDao = componentDao;
        this.componentResultDao = componentResultDao;
        this.workerDao = workerDao;
        this.userDao = userDao;
        this.studyLogger = studyLogger;
        this.ioUtils = ioUtils;
    }

    /**
     * Retrieves the worker with the given worker ID from the DB.
     */
    public Worker retrieveWorker(Long workerId) {
        return workerDao.findById(workerId);
    }

    public ComponentResult startComponent(Component component, StudyResult studyResult) {
        return startComponent(component, studyResult, null);
    }

    /**
     * Start or restart a component. It either returns a newly started component or an exception but never null.
     */
    public ComponentResult startComponent(Component component, StudyResult studyResult, String message) {
        return jpa.withTransaction(em -> {
            // Deal with the last component
            Optional<ComponentResult> lastResultOpt = componentResultDao.findLastByStudyResult(studyResult);
            if (lastResultOpt.isPresent()) {

                ComponentResult lastResult = lastResultOpt.get();
                Study study = component.getStudy();
                Component lastComponent = lastResult.getComponent();
                int lastPosition = study.getComponentPosition(lastComponent);
                int position = study.getComponentPosition(component);

                if (study.isLinearStudy() && lastPosition > position) {
                    // Only linear study flow is allowed - the component to be started is before (by position) the current one
                    finishComponentResult(lastResult, ComponentState.FAIL, message);
                    throw new ForbiddenNonLinearFlowException(study.getUuid(),
                            PublixErrorMessages.forbiddenNonLinearStudyFlow(study.getTitle(), study.getId(),
                                    lastComponent.getId(), component.getId()));
                }

                if (lastComponent.equals(component)) {
                    // The component to be started is the same as the last one
                    if (component.isReloadable() || isFirstComponentInPreviewStudy(lastResult)) {
                        // Reload is allowed
                        finishComponentResult(lastResult, ComponentState.RELOADED, message);
                    } else {
                        // Worker tried to reload a non-reloadable component -> end
                        finishComponentResult(lastResult, ComponentState.FAIL, message);
                        String errorMsg = PublixErrorMessages.componentNotAllowedToReload(
                                studyResult.getStudy().getId(), component.getId());
                        throw new ForbiddenReloadException(study.getUuid(), errorMsg);
                    }

                } else {
                    // The previous component is different from the one to be started: just finish the old one
                    finishComponentResult(lastResult, ComponentState.FINISHED, message);
                }
            }
            return resultCreator.createComponentResult(studyResult, component);
        });
    }

    private void finishComponentResult(ComponentResult componentResult, ComponentState state, String message) {
        jpa.withTransaction(em -> {
            componentResult.setComponentState(state);
            componentResult.setEndDate(new Timestamp(new Date().getTime()));
            componentResult.setMessage(message);
            componentResultDao.merge(componentResult);
            // If data fields not set -> set at least data size = 0
            if (componentResult.getDataShort() == null) {
                componentResultDao.setDataSizeAndDataShort(componentResult.getId());
            }
        });
    }

    /**
     * Does everything to abort a study: ends the current component with state ABORTED, finishes all other Components
     * that might still be open, deletes all result data and ends the study with state ABORTED and sets the given
     * message.
     */
    public void abortStudy(String message, StudyResult studyResult) {
        jpa.withTransaction(em -> {
            // Put the current ComponentResult into state ABORTED and set the end date
            Timestamp endDate = new Timestamp(new Date().getTime());
            retrieveCurrentComponentResult(studyResult).ifPresent(currentComponentResult -> {
                finishComponentResult(currentComponentResult, ComponentState.ABORTED, null);
                currentComponentResult.setEndDate(endDate);
            });
            // Finish the other ComponentResults
            finishAllComponentResults(studyResult);

            // Clear all data and set ABORTED for all(!) ComponentResults
            for (ComponentResult componentResult : studyResult.getComponentResultList()) {
                componentResultDao.purgeData(componentResult.getId());
                componentResult.setComponentState(ComponentState.ABORTED);
                componentResultDao.merge(componentResult);
            }

            // Remove all uploaded result files
            try {
                ioUtils.removeResultUploadsDir(studyResult.getId());
            } catch (IOException e) {
                LOGGER.error("Cannot delete result upload files (srid " + studyResult.getId() + "): " + e.getMessage());
            }

            // Set StudyResult to state ABORTED and set a message
            studyResult.setStudyState(StudyState.ABORTED);
            studyResult.setMessage(message);
            studyResult.setEndDate(endDate);
            studyResult.setStudySessionData(null);
            studyResultDao.merge(studyResult);
        });
    }

    /**
     * Finishes a StudyResult (includes ComponentResults) and returns a confirmation code if it was successful.
     *
     * @param successful  If true, finishes all ComponentResults, generates a confirmation code and set the
     *                    StudyResult's and current ComponentResult's state to FINISHED. If false it sets both states to
     *                    FAIL and doesn't generate a confirmation code.
     * @param message     Will be set in the StudyResult. Can be null.
     * @param studyResult A StudyResult
     * @return The confirmation code or null if it was unsuccessful
     */
    public String finishStudyResult(Boolean successful, String message, StudyResult studyResult) {
        return jpa.withTransaction(em -> {
            String confirmationCode;
            StudyState studyState;
            ComponentState componentState;
            if (successful) {
                confirmationCode = studyResult.getWorker().generateConfirmationCode();
                componentState = ComponentState.FINISHED;
                studyState = StudyState.FINISHED;
            } else {
                confirmationCode = null;
                componentState = ComponentState.FAIL;
                studyState = StudyState.FAIL;
            }
            Timestamp endDate = new Timestamp(new Date().getTime());
            retrieveCurrentComponentResult(studyResult).ifPresent(componentResult ->
                    finishComponentResult(componentResult, componentState, null));
            studyResult.setStudyState(studyState);

            finishAllComponentResults(studyResult);
            studyResult.setConfirmationCode(confirmationCode);
            studyResult.setMessage(message);
            studyResult.setEndDate(endDate);
            // Clear study session data before finishing
            studyResult.setStudySessionData(null);
            studyResultDao.merge(studyResult);
            return confirmationCode;
        });
    }

    private void finishAllComponentResults(StudyResult studyResult) {
        studyResult.getComponentResultList().stream()
                .filter(componentResult -> !PublixHelpers.componentDone(componentResult))
                .forEach(componentResult -> finishComponentResult(componentResult, ComponentState.FINISHED, null));
    }

    /**
     * Checks if the max number of JATOS ID cookies is reached and if yes, finishes the oldest one(s) with a state FAIL.
     * Usually there is only one ID cookie that is to be discarded, but if the jatos.idCookies.limit config value got
     * recently decreased, there will be more than one. This method should only be called during the start of a study.
     */
    public void finishOldestStudyResult(Http.Request request) {
        jpa.withTransaction(em -> {
            while (idCookieService.maxIdCookiesReached(request)) {
                Long abandonedStudyResultId = idCookieService.getStudyResultIdFromOldestIdCookie(request);
                StudyResult abandonedStudyResult = studyResultDao.findById(abandonedStudyResultId);
                // If the abandoned study result isn't done, finish it.
                if (abandonedStudyResult != null && !PublixHelpers.studyRunDone(abandonedStudyResult)) {
                    groupAdministration.leave(abandonedStudyResult);
                    finishStudyResult(false, PublixErrorMessages.ABANDONED_STUDY_BY_COOKIE,
                            abandonedStudyResult);
                    studyLogger.log(abandonedStudyResult.getStudy(), "Finish abandoned study",
                            abandonedStudyResult.getWorker());
                }
                idCookieService.discardIdCookie(request, abandonedStudyResultId);
            }
        });
    }

    /**
     * Returns an Optional of the last ComponentResult of this studyResult, but only if it's not 'done'.
     */
    public Optional<ComponentResult> retrieveCurrentComponentResult(StudyResult studyResult) {
        Optional<ComponentResult> last = componentResultDao.findLastByStudyResult(studyResult);
        if (last.isPresent() && !PublixHelpers.componentDone(last.get())) {
            return last;
        } else {
            return Optional.empty();
        }
    }

    /**
     * Gets the current ComponentResult from the storage or if it doesn't exist yet, starts one for the given component.
     * The current ComponentResult doesn't have to be of the given Component.
     */
    public ComponentResult retrieveStartedComponentResult(Component component, StudyResult studyResult) {
        Optional<ComponentResult> current = retrieveCurrentComponentResult(studyResult);
        // Start the component if it was never started or if it's a reload of the component
        return current.orElseGet(() -> startComponent(component, studyResult));
    }

    /**
     * Returns the first component in the given study that is active. If there is no such component, it throws a
     * NotFoundPublixException.
     */
    public Component retrieveFirstActiveComponent(Study study) {
        Optional<Component> component = study.getFirstComponent();
        // Find the first active component or null if the study has no active components
        while (component.isPresent() && !component.get().isActive()) {
            component = study.getNextComponent(component.get());
        }
        if (!component.isPresent()) {
            throw new NotFoundException(PublixErrorMessages.studyHasNoActiveComponents(study.getId()));
        }
        return component.get();
    }

    /**
     * Returns the component with the given component ID that belongs to the given study.
     *
     * @param study       A Study
     * @param componentId The component's ID
     * @return The Component
     * @throws NotFoundException   Thrown if such a component doesn't exist.
     * @throws BadRequestException Thrown if the component doesn't belong to the given study.
     * @throws ForbiddenException  Thrown if the component isn't active.
     */
    public Component retrieveComponent(Study study, Long componentId) {
        Component component = componentDao.findById(componentId);
        if (component == null) {
            throw new NotFoundException(PublixErrorMessages.componentNotExist(study.getId(), componentId));
        }
        if (!component.getStudy().getId().equals(study.getId())) {
            throw new BadRequestException(PublixErrorMessages.componentNotBelongToStudy(study.getId(), componentId));
        }
        if (!component.isActive()) {
            throw new ForbiddenException(PublixErrorMessages.componentNotActive(study.getId(), componentId));
        }
        return component;
    }

    /**
     * Checks if this component belongs to this study and throws a BadRequestException if it doesn't.
     */
    public void checkComponentBelongsToStudy(Study study, Component component) {
        if (!component.getStudy().equals(study)) {
            throw new BadRequestException(PublixErrorMessages.componentNotBelongToStudy(study.getId(), component.getId()));
        }
    }

    /**
     * Throws ForbiddenException if the group doesn't allow messaging.
     */
    public void checkStudyIsGroupStudy(Study study) {
        if (!study.isGroupStudy()) {
            throw new ForbiddenException(PublixErrorMessages.studyNotGroupStudy(study.getId()));
        }
    }

    /**
     * Sets the StudyResult's StudyState to STARTED if the study is currently in state PRE and the study result moved
     * away from the first active component
     */
    public void setPreStudyState(ComponentResult componentResult) {
        StudyResult studyResult = componentResult.getStudyResult();
        Component component = componentResult.getComponent();
        Study study = component.getStudy();
        if (studyResult.getStudyState() == StudyState.PRE
                && !retrieveFirstActiveComponent(study).getId().equals(component.getId())) {
            studyResult.setStudyState(StudyState.STARTED);
        }
        studyResultDao.merge(studyResult);
    }

    /**
     * Returns true if the Study that belongs to the given ComponentResult allows previews and the component that
     * belongs to the given ComponentResult is the first active component in the study.
     */
    public boolean isFirstComponentInPreviewStudy(ComponentResult componentResult) {
        StudyResult studyResult = componentResult.getStudyResult();
        Component component = componentResult.getComponent();
        Study study = component.getStudy();
        return (studyResult.getStudyState() == StudyResult.StudyState.PRE
                && retrieveFirstActiveComponent(study).equals(component));
    }

    /**
     * Get query string parameters from the calling URL and put them into the field urlQueryParameters in StudyResult as
     * a JSON string.
     */
    public void setUrlQueryParameter(Http.Request request, StudyResult studyResult) {
        Map<String, String> queryMap = new HashMap<>();
        request.queryString().forEach((k, v) -> queryMap.put(k, v[0]));
        String parameter = JsonUtils.asJson(queryMap);
        studyResult.setUrlQueryParameters(parameter);
    }

    /**
     * Gets an uploaded result file with the given filename. If a component is given (not null), it only tries to get
     * the file from component results belonging to this component. In case of several possible files (can happen with
     * reloaded components) it returns the file uploaded last. If a component is not given (equals null), it searches
     * all component results of this study result for a file with this filename and returns the one that was uploaded
     * last.
     */
    public Optional<File> retrieveLastUploadedResultFile(StudyResult studyResult, Component component,
                                                         String filename) {
        List<Long> componentResultIdList = componentResultDao
                .findIdsByStudyResultAndComponent(studyResult.getId(), component);
        Collections.reverse(componentResultIdList);

        try {
            for (Long crId : componentResultIdList) {
                File file = ioUtils.getResultUploadFileSecurely(studyResult.getId(), crId, filename);
                if (file.exists()) return Optional.of(file);
            }
        } catch (IOException ignore) {
        }
        return Optional.empty();
    }

    /**
     * Retrieves the currently signed-in user or throws a ForbiddenException if none is signed in.
     */
    public User retrieveSignedinUser(Http.Request request) {
        String normalizedUsername = request.session().get(JatosPublix.SESSION_USERNAME)
                .orElseThrow(() -> new ForbiddenException("No user signed in"));

        User signedinUser = userDao.findByUsername(normalizedUsername);
        if (signedinUser == null) {
            throw new ForbiddenException("User " + normalizedUsername + " doesn't exist.");
        }
        return signedinUser;
    }

    /**
     * Retrieves the JatosRun object that maps to the jatos run parameter in the session.
     */
    public JatosPublix.JatosRun fetchJatosRunFromSession(Http.Session session) {
        String sessionValue = session.get("jatos_run")
                .orElseThrow(() -> new ForbiddenException("This study or component was never started in JATOS."));

        try {
            return JatosPublix.JatosRun.valueOf(sessionValue);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Malformed session parameter 'jatos_run'");
        }
    }

    public Optional<StudyResult> getLastStudyResult(Worker worker) {
        return workerDao.findLastStudyResult(worker);
    }

}
