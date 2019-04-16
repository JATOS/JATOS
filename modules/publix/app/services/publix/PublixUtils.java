package services.publix;

import daos.common.*;
import daos.common.worker.WorkerDao;
import exceptions.publix.*;
import general.common.StudyLogger;
import group.GroupAdministration;
import models.common.*;
import models.common.ComponentResult.ComponentState;
import models.common.StudyResult.StudyState;
import models.common.workers.Worker;
import services.publix.idcookie.IdCookieService;
import utils.common.JsonUtils;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * Service class with functions that are common for all classes that extend
 * Publix and don't belong in a controller.
 *
 * @author Kristian Lange
 */
public abstract class PublixUtils<T extends Worker> {

    private final ResultCreator resultCreator;
    private final IdCookieService idCookieService;
    private final GroupAdministration groupAdministration;
    protected final PublixErrorMessages errorMessages;
    private final StudyDao studyDao;
    private final StudyResultDao studyResultDao;
    private final ComponentDao componentDao;
    private final ComponentResultDao componentResultDao;
    private final WorkerDao workerDao;
    private final BatchDao batchDao;
    private final StudyLogger studyLogger;

    public PublixUtils(ResultCreator resultCreator,
            IdCookieService idCookieService,
            GroupAdministration groupAdministration,
            PublixErrorMessages errorMessages, StudyDao studyDao,
            StudyResultDao studyResultDao, ComponentDao componentDao,
            ComponentResultDao componentResultDao, WorkerDao workerDao,
            BatchDao batchDao, StudyLogger studyLogger) {
        this.resultCreator = resultCreator;
        this.idCookieService = idCookieService;
        this.groupAdministration = groupAdministration;
        this.errorMessages = errorMessages;
        this.studyDao = studyDao;
        this.studyResultDao = studyResultDao;
        this.componentDao = componentDao;
        this.componentResultDao = componentResultDao;
        this.workerDao = workerDao;
        this.batchDao = batchDao;
        this.studyLogger = studyLogger;
    }

    /**
     * Like {@link #retrieveWorker(Long)} but returns a concrete
     * implementation of the abstract Worker class
     */
    public abstract T retrieveTypedWorker(Long workerId)
            throws ForbiddenPublixException;

    /**
     * Retrieves the worker with the given worker ID from the DB.
     */
    public Worker retrieveWorker(Long workerId) throws ForbiddenPublixException {
        Worker worker = workerDao.findById(workerId);
        if (worker == null) {
            throw new ForbiddenPublixException(
                    PublixErrorMessages.workerNotExist(workerId));
        }
        return worker;
    }

    /**
     * Start or restart a component. It either returns a newly started component
     * or an exception but never null.
     */
    public ComponentResult startComponent(Component component, StudyResult studyResult)
            throws ForbiddenReloadException {
        // Deal with the last component
        Optional<ComponentResult> lastComponentResultOpt = studyResult.getLastComponentResult();
        if (lastComponentResultOpt.isPresent()) {
            ComponentResult lastComponentResult = lastComponentResultOpt.get();
            if (lastComponentResult.getComponent().equals(component)) {
                // The component to be started is the same as the last one
                if (component.isReloadable()) {
                    // Reload is allowed
                    finishComponentResult(lastComponentResult, ComponentState.RELOADED);
                } else {
                    // Worker tried to reload a non-reloadable component -> end
                    // component and study with FAIL
                    finishComponentResult(lastComponentResult, ComponentState.FAIL);
                    String errorMsg = PublixErrorMessages.componentNotAllowedToReload(
                            studyResult.getStudy().getId(), component.getId());
                    throw new ForbiddenReloadException(errorMsg);
                }
            } else {
                // The prior component is a different one than the one to be
                // started: just finish it
                finishComponentResult(lastComponentResult, ComponentState.FINISHED);
            }
        }
        return resultCreator.createComponentResult(studyResult, component);
    }

    private void finishComponentResult(ComponentResult componentResult, ComponentState state) {
        componentResult.setComponentState(state);
        componentResult.setEndDate(new Timestamp(new Date().getTime()));
        componentResultDao.update(componentResult);
    }

    /**
     * Does everything to abort a study: ends the current component with state
     * ABORTED, finishes all other Components that might still be open, deletes
     * all result data and ends the study with state ABORTED and sets the given
     * message as an abort message.
     */
    public void abortStudy(String message, StudyResult studyResult) {
        // Put current ComponentResult into state ABORTED
        retrieveCurrentComponentResult(studyResult).ifPresent(
                currentComponentResult -> finishComponentResult(currentComponentResult, ComponentState.ABORTED));
        // Finish the other ComponentResults
        finishAllComponentResults(studyResult);

        // Clear all data and set ABORTED for all(!) ComponentResults
        for (ComponentResult componentResult : studyResult.getComponentResultList()) {
            componentResult.setData(null);
            componentResult.setComponentState(ComponentState.ABORTED);
            componentResultDao.update(componentResult);
        }

        // Set StudyResult to state ABORTED and set message
        studyResult.setStudyState(StudyState.ABORTED);
        studyResult.setAbortMsg(message);
        studyResult.setEndDate(new Timestamp(new Date().getTime()));
        studyResult.setStudySessionData(null);
        studyResultDao.update(studyResult);
    }

    /**
     * Finishes a StudyResult (includes ComponentResults) and returns a confirmation code if it
     * was successful.
     *
     * @param successful  If true finishes all ComponentResults, generates a
     *                    confirmation code and set the StudyResult's and current ComponentResult's
     *                    state to FINISHED. If false it sets both states to FAIL and doesn't
     *                    generate a confirmation code.
     * @param errorMsg    Will be set in the StudyResult. Can be null if no error
     *                    happened.
     * @param studyResult A StudyResult
     * @return The confirmation code or null if it was unsuccessful
     */
    public String finishStudyResult(Boolean successful, String errorMsg, StudyResult studyResult) {
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
        retrieveCurrentComponentResult(studyResult)
                .ifPresent(componentResult -> componentResult.setComponentState(componentState));
        studyResult.setStudyState(studyState);

        finishAllComponentResults(studyResult);
        studyResult.setConfirmationCode(confirmationCode);
        studyResult.setErrorMsg(errorMsg);
        studyResult.setEndDate(new Timestamp(new Date().getTime()));
        // Clear study session data before finishing
        studyResult.setStudySessionData(null);
        studyResultDao.update(studyResult);
        return confirmationCode;
    }

    private void finishAllComponentResults(StudyResult studyResult) {
        studyResult.getComponentResultList().stream()
                .filter(componentResult -> !PublixHelpers.componentDone(componentResult))
                .forEach(componentResult -> finishComponentResult(componentResult,
                        ComponentState.FINISHED));
    }

    /**
     * Checks if the max number of ID cookies is reached and if yes finishes the oldest one with a
     * state FAIL. This method should only be called during start of a study.
     */
    public void finishOldestStudyResult() throws PublixException {
        if (!idCookieService.maxIdCookiesReached()) {
            return;
        }

        Long abandonedStudyResultId = idCookieService.getStudyResultIdFromOldestIdCookie();
        if (abandonedStudyResultId != null) {
            StudyResult abandonedStudyResult = studyResultDao.findById(abandonedStudyResultId);
            // If the abandoned study result isn't done, finish it.
            if (abandonedStudyResult != null && !PublixHelpers.studyDone(abandonedStudyResult)) {
                finishMemberInGroup(abandonedStudyResult);
                finishStudyResult(false, PublixErrorMessages.ABANDONED_STUDY_BY_COOKIE,
                        abandonedStudyResult);
                studyLogger.log(abandonedStudyResult.getStudy(), "Finish abandoned study",
                        abandonedStudyResult.getWorker());
            }
            idCookieService.discardIdCookie(abandonedStudyResultId);
        }
    }

    /**
     * Moves the given StudyResult in its group result into history
     */
    public void finishMemberInGroup(StudyResult studyResult) {
        GroupResult groupResult = studyResult.getActiveGroupResult();
        Study study = studyResult.getStudy();
        if (study.isGroupStudy() && groupResult != null) {
            groupAdministration.moveToHistory(studyResult);
            groupAdministration.checkAndFinishGroup(groupResult);
        }
    }

    public StudyResult retrieveStudyResult(Worker worker, Study study, Long studyResultId)
            throws ForbiddenPublixException, BadRequestPublixException {
        if (studyResultId == null) {
            throw new ForbiddenPublixException(
                    "error retrieving study result ID");
        }
        StudyResult studyResult = studyResultDao.findById(studyResultId);
        if (studyResult == null) {
            throw new BadRequestPublixException(
                    PublixErrorMessages.STUDY_RESULT_DOESN_T_EXIST);
        }
        // Check that the given worker actually did this study result
        if (!worker.getStudyResultList().contains(studyResult)) {
            throw new ForbiddenPublixException(
                    PublixErrorMessages.workerNeverDidStudy(worker, study.getId()));
        }
        // Check that this study result belongs to the given study
        if (!studyResult.getStudy().getId().equals(study.getId())) {
            throw new ForbiddenPublixException(
                    PublixErrorMessages.STUDY_RESULT_DOESN_T_BELONG_TO_THIS_STUDY);
        }
        // Check that this study result isn't finished
        if (PublixHelpers.studyDone(studyResult)) {
            throw new ForbiddenPublixException(
                    PublixErrorMessages.workerFinishedStudyAlready(worker, study.getId()));
        }
        return studyResult;

    }

    /**
     * Returns an Optional of the last ComponentResult's component (of the given StudyResult.
     */
    public Optional<Component> retrieveLastComponent(StudyResult studyResult) {
        return studyResult.getLastComponentResult().map(ComponentResult::getComponent);
    }

    /**
     * Returns an Optional of the last ComponentResult of this studyResult but only if it's not
     * 'done'.
     */
    public Optional<ComponentResult> retrieveCurrentComponentResult(StudyResult studyResult) {
        Optional<ComponentResult> last = studyResult.getLastComponentResult();
        if (last.isPresent() && !PublixHelpers.componentDone(last.get())) {
            return last;
        } else {
            return Optional.empty();
        }
    }

    /**
     * Gets the current ComponentResult from the storage or if it doesn't exist
     * yet starts one for the given component. The current ComponentResult
     * doesn't have to be of the given Component.
     */
    public ComponentResult retrieveStartedComponentResult(Component component,
            StudyResult studyResult) throws ForbiddenReloadException {
        Optional<ComponentResult> current = retrieveCurrentComponentResult(studyResult);
        // Start the component if it was never started or if it's a reload of the component
        return current.isPresent() ? current.get() : startComponent(component, studyResult);
    }

    /**
     * Returns the first component in the given study that is active. If there
     * is no such component it throws a NotFoundPublixException.
     */
    public Component retrieveFirstActiveComponent(Study study) throws NotFoundPublixException {
        Optional<Component> component = study.getFirstComponent();
        // Find first active component or null if study has no active components
        while (component.isPresent() && !component.get().isActive()) {
            component = study.getNextComponent(component.get());
        }
        if (!component.isPresent()) {
            throw new NotFoundPublixException(PublixErrorMessages
                    .studyHasNoActiveComponents(study.getId()));
        }
        return component.get();
    }

    /**
     * Returns an Optional to the next active component in the list of components that
     * correspond to the ComponentResults of the given StudyResult.
     */
    public Optional<Component> retrieveNextActiveComponent(StudyResult studyResult)
            throws InternalServerErrorPublixException {
        Component current = retrieveLastComponent(studyResult).orElseThrow(() ->
                new InternalServerErrorPublixException(
                        "Couldn't find the last running component."));
        Optional<Component> next = studyResult.getStudy().getNextComponent(current);
        // Find next active component or null if study has no more components
        while (next.isPresent() && !next.get().isActive()) {
            next = studyResult.getStudy().getNextComponent(next.get());
        }
        return next;
    }

    /**
     * Returns the component with the given component ID that belongs to the
     * given study.
     *
     * @param study       A Study
     * @param componentId The component's ID
     * @return The Component
     * @throws NotFoundPublixException   Thrown if such component doesn't exist.
     * @throws BadRequestPublixException Thrown if the component doesn't belong to the given study.
     * @throws ForbiddenPublixException  Thrown if the component isn't active.
     */
    public Component retrieveComponent(Study study, Long componentId)
            throws NotFoundPublixException, BadRequestPublixException, ForbiddenPublixException {
        Component component = componentDao.findById(componentId);
        if (component == null) {
            throw new NotFoundPublixException(PublixErrorMessages
                    .componentNotExist(study.getId(), componentId));
        }
        if (!component.getStudy().getId().equals(study.getId())) {
            throw new BadRequestPublixException(PublixErrorMessages
                    .componentNotBelongToStudy(study.getId(), componentId));
        }
        if (!component.isActive()) {
            throw new ForbiddenPublixException(PublixErrorMessages
                    .componentNotActive(study.getId(), componentId));
        }
        return component;
    }

    public Component retrieveComponentByPosition(Long studyId, Integer position)
            throws NotFoundPublixException, BadRequestPublixException {
        Study study = retrieveStudy(studyId);
        if (position == null) {
            throw new BadRequestPublixException(
                    PublixErrorMessages.COMPONENTS_POSITION_NOT_NULL);
        }
        Component component;
        try {
            component = study.getComponent(position);
        } catch (IndexOutOfBoundsException e) {
            throw new NotFoundPublixException(
                    PublixErrorMessages.noComponentAtPosition(study.getId(), position));
        }
        return component;
    }

    /**
     * Returns the study corresponding to the given study ID. It throws an
     * NotFoundPublixException if there is no such study.
     */
    public Study retrieveStudy(Long studyId) throws NotFoundPublixException {
        Study study = studyDao.findById(studyId);
        if (study == null) {
            throw new NotFoundPublixException(
                    PublixErrorMessages.studyNotExist(studyId));
        }
        return study;
    }

    /**
     * Checks if this component belongs to this study and throws an
     * BadRequestPublixException if it doesn't.
     */
    public void checkComponentBelongsToStudy(Study study, Component component)
            throws BadRequestPublixException {
        if (!component.getStudy().equals(study)) {
            throw new BadRequestPublixException(PublixErrorMessages
                    .componentNotBelongToStudy(study.getId(), component.getId()));
        }
    }

    /**
     * Throws ForbiddenPublixException if group doesn't allow messaging.
     */
    public void checkStudyIsGroupStudy(Study study) throws ForbiddenPublixException {
        if (!study.isGroupStudy()) {
            throw new ForbiddenPublixException(
                    PublixErrorMessages.studyNotGroupStudy(study.getId()));
        }
    }

    /**
     * Gets the batch with given ID from the database or if the batchId is -1
     * returns the default batch of this study. If the batch doesn't exist it throws an
     * NotFoundPublixException.
     */
    public Batch retrieveBatchByIdOrDefault(Long batchId, Study study)
            throws NotFoundPublixException {
        if (batchId == -1) {
            // The default batch is always the first one in study's batch list
            return study.getDefaultBatch();
        } else {
            return retrieveBatch(batchId);
        }
    }

    /**
     * Retrieves batch from database. If the batch doesn't exist it throws an
     * NotFoundPublixException.
     */
    public Batch retrieveBatch(Long batchId) throws NotFoundPublixException {
        Batch batch = batchDao.findById(batchId);
        if (batch == null) {
            throw new NotFoundPublixException(PublixErrorMessages.batchNotExist(batchId));
        }
        return batch;
    }

    /**
     * Sets the StudyResult's StudyState to STARTED if the study is currently in
     * state PRE and the study result moved away from the first active component (this
     * means the given componentId isn't the first component's one).
     */
    public void setPreStudyStateByComponentId(StudyResult studyResult, Study study,
            Long componentId) throws NotFoundPublixException {
        if (studyResult.getStudyState() == StudyState.PRE
                && !retrieveFirstActiveComponent(study).getId().equals(componentId)) {
            studyResult.setStudyState(StudyState.STARTED);
        }
        studyResultDao.update(studyResult);
    }

    /**
     * Gets the URL query parameters without the JATOS specific ones. Since the JATOS specific ones
     * vary from worker to worker the method is defined in the worker-specific sub-classes.
     */
    protected abstract Map<String, String> getNonJatosUrlQueryParameters();

    /**
     * Get query string parameters from the calling URL and put them into the field
     * urlQueryParameters in StudyResult as a JSON string.
     */
    public StudyResult setUrlQueryParameter(StudyResult studyResult) {
        String parameter = JsonUtils.asJson(getNonJatosUrlQueryParameters());
        studyResult.setUrlQueryParameters(parameter);
        return studyResult;
    }

}
