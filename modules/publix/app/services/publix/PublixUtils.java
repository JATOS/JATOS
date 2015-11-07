package services.publix;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import models.common.Component;
import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.Worker;

import org.w3c.dom.Document;

import play.mvc.Http.RequestBody;
import utils.common.XMLUtils;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.publix.Publix;
import daos.ComponentDao;
import daos.ComponentResultDao;
import daos.StudyDao;
import daos.StudyResultDao;
import daos.workers.WorkerDao;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.ForbiddenReloadException;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;
import exceptions.publix.UnsupportedMediaTypePublixException;

/**
 * Service class with functions that are common for all classes that extend
 * Publix and don't belong in a controller.
 *
 * @author Kristian Lange
 */
public abstract class PublixUtils<T extends Worker> {

	protected final PublixErrorMessages errorMessages;
	private final StudyDao studyDao;
	private final StudyResultDao studyResultDao;
	private final ComponentDao componentDao;
	private final ComponentResultDao componentResultDao;
	private final WorkerDao workerDao;

	public PublixUtils(PublixErrorMessages errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao) {
		this.errorMessages = errorMessages;
		this.studyDao = studyDao;
		this.studyResultDao = studyResultDao;
		this.componentDao = componentDao;
		this.componentResultDao = componentResultDao;
		this.workerDao = workerDao;
	}

	/**
	 * Like {@link #retrieveWorker(String)} but returns a concrete
	 * implementation of the abstract Worker class
	 */
	public abstract T retrieveTypedWorker(String workerIdStr)
			throws ForbiddenPublixException;

	/**
	 * Retrieves the worker with the given worker ID from the DB.
	 */
	public Worker retrieveWorker(String workerIdStr)
			throws ForbiddenPublixException {
		if (workerIdStr == null) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.NO_WORKERID_IN_SESSION);
		}
		long workerId;
		try {
			workerId = Long.parseLong(workerIdStr);
		} catch (NumberFormatException e) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotExist(workerIdStr));
		}

		Worker worker = workerDao.findById(workerId);
		if (worker == null) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotExist(workerId));
		}
		return worker;
	}

	/**
	 * Start or restart a component. It either returns a newly started component
	 * or an exception but never null.
	 */
	public ComponentResult startComponent(Component component,
			StudyResult studyResult) throws ForbiddenReloadException {
		// Deal with the last component
		ComponentResult lastComponentResult = retrieveLastComponentResult(studyResult);
		if (lastComponentResult != null) {
			if (lastComponentResult.getComponent().equals(component)) {
				// The component to be started is the same as the last one
				if (component.isReloadable()) {
					// Reload is allowed
					finishComponentResult(lastComponentResult,
							ComponentState.RELOADED);
				} else {
					// Worker tried to reload a non-reloadable component -> end
					// component and study with FAIL
					finishComponentResult(lastComponentResult,
							ComponentState.FAIL);
					String errorMsg = errorMessages
							.componentNotAllowedToReload(studyResult.getStudy()
									.getId(), component.getId());
					// exceptionalFinishStudy(studyResult, errorMsg);
					throw new ForbiddenReloadException(errorMsg);
				}
			} else {
				// The prior component is a different one than the one to be
				// started: just finish it
				finishComponentResult(lastComponentResult,
						ComponentState.FINISHED);
			}
		}
		return componentResultDao.create(studyResult, component);
	}

	private void finishComponentResult(ComponentResult componentResult,
			ComponentState state) {
		componentResult.setComponentState(state);
		componentResult.setEndDate(new Timestamp(new Date().getTime()));
		componentResultDao.update(componentResult);
	}

	/**
	 * Generates the value that will be put in the ID cookie. An ID cookie has a
	 * worker ID, study ID, study result ID, group result ID (if not exist:
	 * null), component ID, component result ID and component position.
	 */
	public String generateIdCookieValue(StudyResult studyResult,
			ComponentResult componentResult, Worker worker,
			GroupResult groupResult) {
		Study study = studyResult.getStudy();
//		Group group = groupResult.getGroup();
		Component component = componentResult.getComponent();
		Map<String, String> cookieMap = new HashMap<>();
		cookieMap.put(Publix.WORKER_ID, String.valueOf(worker.getId()));
		cookieMap.put(Publix.STUDY_ID, String.valueOf(study.getId()));
		cookieMap.put(Publix.STUDY_RESULT_ID,
				String.valueOf(studyResult.getId()));
		// TODO
//		String groupId = (group != null) ? String.valueOf(group.getId())
//				: "null";
//		cookieMap.put(Publix.GROUP_ID, groupId);
		String groupResultId = (groupResult != null) ? String
				.valueOf(groupResult.getId()) : "null";
		cookieMap.put(Publix.GROUP_RESULT_ID, groupResultId);
		cookieMap.put(Publix.COMPONENT_ID, String.valueOf(component.getId()));
		cookieMap.put(Publix.COMPONENT_RESULT_ID,
				String.valueOf(componentResult.getId()));
		cookieMap.put(Publix.COMPONENT_POSITION,
				String.valueOf(study.getComponentPosition(component)));
		return generateUrlQueryString(cookieMap);
	}

	/**
	 * Generates a query string as used in an URL. It takes a map and put its
	 * key-value-pairs into a string like in key=value&key=value&...
	 */
	private String generateUrlQueryString(Map<String, String> cookieMap) {
		StringBuilder sb = new StringBuilder();
		Iterator<Entry<String, String>> iterator = cookieMap.entrySet()
				.iterator();
		while (iterator.hasNext()) {
			Entry<String, String> entry = iterator.next();
			sb.append(entry.getKey());
			sb.append("=");
			sb.append(entry.getValue());
			if (iterator.hasNext()) {
				sb.append("&");
			}
		}
		return sb.toString();
	}

	/**
	 * Does everything to abort a study: ends the current component with state
	 * ABORTED, finishes all other Components that might still be open, deletes
	 * all result data and ends the study with state ABORTED and sets the given
	 * message as an abort message.
	 */
	public void abortStudy(String message, StudyResult studyResult) {
		// Put current ComponentResult into state ABORTED
		ComponentResult currentComponentResult = retrieveCurrentComponentResult(studyResult);
		finishComponentResult(currentComponentResult, ComponentState.ABORTED);

		// Finish the other ComponentResults
		finishAllComponentResults(studyResult);

		// Clear all data and set ABORTED for all ComponentResults
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
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
	 * Finishes a StudyResult (includes ComponentResults) and returns a
	 * confirmation code.
	 *
	 * @param successful
	 *            If true finishes all ComponentResults, generates a
	 *            confirmation code and set the StudyResult's state to FINISHED.
	 *            If false it only sets the state to FAIL.
	 * @param errorMsg
	 *            Will be set in the StudyResult. Can be null if no error
	 *            happened.
	 * @param studyResult
	 *            A StudyResult
	 * @return The confirmation code
	 */
	public String finishStudyResult(Boolean successful, String errorMsg,
			StudyResult studyResult) {
		String confirmationCode;
		if (successful) {
			finishAllComponentResults(studyResult);
			confirmationCode = studyResult.getWorker()
					.generateConfirmationCode();
			studyResult.setStudyState(StudyState.FINISHED);
		} else {
			// Don't finish ComponentResults and leave them as it
			confirmationCode = null;
			studyResult.setStudyState(StudyState.FAIL);
		}
		studyResult.setConfirmationCode(confirmationCode);
		studyResult.setErrorMsg(errorMsg);
		studyResult.setEndDate(new Timestamp(new Date().getTime()));
		// Clear study session data before finishing
		studyResult.setStudySessionData(null);
		studyResultDao.update(studyResult);
		return confirmationCode;
	}

	private void finishAllComponentResults(StudyResult studyResult) {
		studyResult
				.getComponentResultList()
				.stream()
				.filter(componentResult -> !componentDone(componentResult))
				.forEach(
						componentResult -> finishComponentResult(
								componentResult, ComponentState.FINISHED));
	}

	/**
	 * Retrieves the text from the request body and returns it as a String. If
	 * the content is in JSON or XML format it's parsed to bring the String into
	 * a nice format. If the content is neither text nor JSON or XML an
	 * UnsupportedMediaTypePublixException is thrown.
	 */
	public String getDataFromRequestBody(RequestBody requestBody)
			throws UnsupportedMediaTypePublixException {
		// Text
		String text = requestBody.asText();
		if (text != null) {
			return text;
		}

		// JSON
		JsonNode json = requestBody.asJson();
		if (json != null) {
			return json.toString();
		}

		// XML
		Document xml = requestBody.asXml();
		if (xml != null) {
			return XMLUtils.asString(xml);
		}

		// No supported format
		throw new UnsupportedMediaTypePublixException(
				PublixErrorMessages.SUBMITTED_DATA_UNKNOWN_FORMAT);
	}

	/**
	 * Finishes all StudyResults of this worker of this study that aren't
	 * 'done'. Each worker can do only one study with the same ID at the same
	 * time.
	 */
	public void finishAllPriorStudyResults(Worker worker, Study study) {
		List<StudyResult> studyResultList = worker.getStudyResultList();
		for (StudyResult studyResult : studyResultList) {
			if (study.getId().equals(studyResult.getStudy().getId())
					&& !studyDone(studyResult)) {
				// Should be max. one StudyResult to finish this way
				finishStudyResult(false,
						PublixErrorMessages.STUDY_NEVER_FINSHED, studyResult);
			}
		}
	}

	/**
	 * Gets the last StudyResult of this worker of this study. Throws an
	 * ForbiddenPublixException if the StudyResult is already 'done' or this
	 * worker never started a StudyResult of this study. It either returns a
	 * StudyResult or throws an exception but never returns null.
	 */
	public StudyResult retrieveWorkersLastStudyResult(Worker worker, Study study)
			throws ForbiddenPublixException {
		int studyResultListSize = worker.getStudyResultList().size();
		for (int i = (studyResultListSize - 1); i >= 0; i--) {
			StudyResult studyResult = worker.getStudyResultList().get(i);
			if (studyResult.getStudy().getId().equals(study.getId())) {
				if (studyDone(studyResult)) {
					throw new ForbiddenPublixException(
							errorMessages.workerFinishedStudyAlready(worker,
									study.getId()));
				} else {
					return studyResult;
				}
			}
		}
		// This worker never started a StudyResult of this study
		throw new ForbiddenPublixException(errorMessages.workerNeverDidStudy(
				worker, study.getId()));
	}

	/**
	 * Returns the last ComponentResult in the given StudyResult (not study!) or
	 * null if it doesn't exist.
	 */
	public ComponentResult retrieveLastComponentResult(StudyResult studyResult) {
		List<ComponentResult> componentResultList = studyResult
				.getComponentResultList();
		if (!componentResultList.isEmpty()) {
			return componentResultList.get(componentResultList.size() - 1);
		} else {
			return null;
		}
	}

	/**
	 * Retrieves the last ComponentResult's component (of the given StudyResult)
	 * or null if it doesn't exist.
	 */
	public Component retrieveLastComponent(StudyResult studyResult) {
		ComponentResult componentResult = retrieveLastComponentResult(studyResult);
		return (componentResult != null) ? componentResult.getComponent()
				: null;
	}

	/**
	 * Returns the last ComponentResult of this studyResult if it's not 'done'.
	 * Returns null if such ComponentResult doesn't exists.
	 */
	public ComponentResult retrieveCurrentComponentResult(
			StudyResult studyResult) {
		ComponentResult componentResult = retrieveLastComponentResult(studyResult);
		if (componentDone(componentResult)) {
			return null;
		}
		return componentResult;
	}

	/**
	 * Gets the ComponentResult from the storage or if it doesn't exist yet
	 * starts one.
	 */
	public ComponentResult retrieveStartedComponentResult(Component component,
			StudyResult studyResult) throws ForbiddenReloadException {
		ComponentResult componentResult = retrieveCurrentComponentResult(studyResult);
		// Start the component if it was never started (== null) or if it's
		// a reload of the component
		if (componentResult == null) {
			componentResult = startComponent(component, studyResult);
		}
		return componentResult;
	}

	/**
	 * Returns the first component in the given study that is active. If there
	 * is no such component it throws a NotFoundPublixException.
	 */
	public Component retrieveFirstActiveComponent(Study study)
			throws NotFoundPublixException {
		Component component = study.getFirstComponent();
		// Find first active component or null if study has no active components
		while (component != null && !component.isActive()) {
			component = study.getNextComponent(component);
		}
		if (component == null) {
			throw new NotFoundPublixException(
					errorMessages.studyHasNoActiveComponents(study.getId()));
		}
		return component;
	}

	/**
	 * Returns the next active component in the list of components that
	 * correspond to the ComponentResults of the given StudyResult. Returns null
	 * if such component doesn't exist.
	 */
	public Component retrieveNextActiveComponent(StudyResult studyResult) {
		Component currentComponent = retrieveLastComponent(studyResult);
		Component nextComponent = studyResult.getStudy().getNextComponent(
				currentComponent);
		// Find next active component or null if study has no more components
		while (nextComponent != null && !nextComponent.isActive()) {
			nextComponent = studyResult.getStudy().getNextComponent(
					nextComponent);
		}
		return nextComponent;
	}

	/**
	 * Returns the component with the given component ID that belongs to the
	 * given study.
	 *
	 * @param study
	 *            A Study
	 * @param componentId
	 *            The component's ID
	 * @return The Component
	 * @throws NotFoundPublixException
	 *             Thrown if such component doesn't exist.
	 * @throws BadRequestPublixException
	 *             Thrown if the component doesn't belong to the given study.
	 * @throws ForbiddenPublixException
	 *             Thrown if the component isn't active.
	 */
	public Component retrieveComponent(Study study, Long componentId)
			throws NotFoundPublixException, BadRequestPublixException,
			ForbiddenPublixException {
		Component component = componentDao.findById(componentId);
		if (component == null) {
			throw new NotFoundPublixException(errorMessages.componentNotExist(
					study.getId(), componentId));
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			throw new BadRequestPublixException(
					errorMessages.componentNotBelongToStudy(study.getId(),
							componentId));
		}
		if (!component.isActive()) {
			throw new ForbiddenPublixException(
					errorMessages.componentNotActive(study.getId(), componentId));
		}
		return component;
	}

	public Component retrieveComponentByPosition(Long studyId, Integer position)
			throws PublixException {
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
					errorMessages.noComponentAtPosition(study.getId(), position));
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
					errorMessages.studyNotExist(studyId));
		}
		return study;
	}

	/**
	 * Checks if this component belongs to this study and throws an
	 * BadRequestPublixException if it doesn't.
	 */
	public void checkComponentBelongsToStudy(Study study, Component component)
			throws PublixException {
		if (!component.getStudy().equals(study)) {
			throw new BadRequestPublixException(
					errorMessages.componentNotBelongToStudy(study.getId(),
							component.getId()));
		}
	}

	/**
	 * Checks if the worker finished this study already. 'Finished' includes
	 * failed and aborted.
	 */
	public boolean finishedStudyAlready(Worker worker, Study study) {
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().equals(study) && studyDone(studyResult)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the worker ever did this study independent of the study
	 * result's state.
	 */
	public boolean didStudyAlready(Worker worker, Study study) {
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().equals(study)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True if StudyResult's state is in FINISHED or ABORTED or FAIL. False
	 * otherwise.
	 */
	public boolean studyDone(StudyResult studyResult) {
		StudyState state = studyResult.getStudyState();
		return state == StudyState.FINISHED || state == StudyState.ABORTED
				|| state == StudyState.FAIL;
	}

	/**
	 * True if ComponentResult's state is in FINISHED or ABORTED or FAIL or
	 * RELOADED. False otherwise.
	 */
	public boolean componentDone(ComponentResult componentResult) {
		ComponentState state = componentResult.getComponentState();
		return ComponentState.FINISHED == state
				|| ComponentState.ABORTED == state
				|| ComponentState.FAIL == state
				|| ComponentState.RELOADED == state;
	}

}
