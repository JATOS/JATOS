package controllers.publix;

import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import models.ComponentModel;
import models.ComponentResult;
import models.ComponentResult.ComponentState;
import models.StudyModel;
import models.StudyResult;
import models.StudyResult.StudyState;
import models.workers.Worker;

import org.w3c.dom.Document;

import persistance.ComponentDao;
import persistance.ComponentResultDao;
import persistance.StudyDao;
import persistance.StudyResultDao;
import persistance.workers.WorkerDao;
import play.Logger;
import play.mvc.Http.RequestBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;

import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.ForbiddenReloadException;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;
import exceptions.publix.UnsupportedMediaTypePublixException;

/**
 * Utilility class with functions that are common for all classes that extend
 * Publix and don't belong in a controller.
 * 
 * @author Kristian Lange
 */
@Singleton
public abstract class PublixUtils<T extends Worker> {

	private static final String CLASS_NAME = PublixUtils.class.getSimpleName();

	protected final PublixErrorMessages<T> errorMessages;
	private final StudyDao studyDao;
	private final StudyResultDao studyResultDao;
	private final ComponentDao componentDao;
	private final ComponentResultDao componentResultDao;
	private final WorkerDao workerDao;

	public PublixUtils(PublixErrorMessages<T> errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao) {
		this.errorMessages = errorMessages;
		this.studyDao = studyDao;
		this.studyResultDao = studyResultDao;
		this.componentDao = componentDao;
		this.componentResultDao = componentResultDao;
		this.workerDao = workerDao;
	}

	public abstract void checkWorkerAllowedToStartStudy(T worker,
			StudyModel study) throws ForbiddenPublixException;

	public abstract void checkWorkerAllowedToDoStudy(T worker, StudyModel study)
			throws ForbiddenPublixException;

	public abstract T retrieveTypedWorker(String workerIdStr)
			throws PublixException;

	public Worker retrieveWorker(String workerIdStr) throws PublixException {
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
	public ComponentResult startComponent(ComponentModel component,
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
	 * Sets cookie with studyId and componentId so the component script has them
	 * too.
	 */
	public String getIdCookieValue(StudyResult studyResult,
			ComponentResult componentResult, Worker worker) {
		StudyModel study = studyResult.getStudy();
		ComponentModel component = componentResult.getComponent();
		Map<String, String> cookieMap = new HashMap<String, String>();
		cookieMap.put(Publix.WORKER_ID, String.valueOf(worker.getId()));
		cookieMap.put(Publix.STUDY_ID, String.valueOf(study.getId()));
		cookieMap.put(Publix.STUDY_RESULT_ID,
				String.valueOf(studyResult.getId()));
		cookieMap.put(Publix.COMPONENT_ID, String.valueOf(component.getId()));
		cookieMap.put(Publix.COMPONENT_RESULT_ID,
				String.valueOf(componentResult.getId()));
		cookieMap.put(Publix.COMPONENT_POSITION,
				String.valueOf(study.getComponentPosition(component)));
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
	 * Discard cookie with studyId and componentId.
	 */
	public void discardIdCookie() {
		Publix.response().discardCookie(Publix.ID_COOKIE_NAME);
	}

	public void abortStudy(String message, StudyResult studyResult) {
		// Put current ComponentResult into state ABORTED
		ComponentResult currentComponentResult = retrieveCurrentComponentResult(studyResult);
		finishComponentResult(currentComponentResult, ComponentState.ABORTED);

		// Finish the other ComponentResults
		finishAllComponentResults(studyResult);

		// Clear all data from all ComponentResults of this StudyResult.
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			componentResult.setData(null);
			componentResultDao.update(componentResult);
		}

		// Set StudyResult to state ABORTED and set message
		studyResult.setStudyState(StudyState.ABORTED);
		studyResult.setAbortMsg(message);
		studyResult.setEndDate(new Timestamp(new Date().getTime()));
		studyResultDao.update(studyResult);
	}

	public String finishStudy(Boolean successful, String errorMsg,
			StudyResult studyResult) {
		String confirmationCode;
		if (successful) {
			finishAllComponentResults(studyResult);
			confirmationCode = studyResult.getWorker()
					.generateConfirmationCode();
			studyResult.setStudyState(StudyState.FINISHED);
		} else {
			// Don't finish components and leave them as it
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

	public void finishAllComponentResults(StudyResult studyResult) {
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			if (!componentDone(componentResult)) {
				finishComponentResult(componentResult, ComponentState.FINISHED);
			}
		}
	}

	public String getRequestBodyAsString(RequestBody requestBody) {
		String text = requestBody.asText();
		if (text != null) {
			return text;
		}

		JsonNode json = requestBody.asJson();
		if (json != null) {
			return json.toString();
		}

		Document xml = requestBody.asXml();
		if (xml != null) {
			return asString(xml);
		}

		return null;
	}

	/**
	 * Convert XML-Document to String
	 */
	public String asString(Document doc) {
		try {
			DOMSource domSource = new DOMSource(doc);
			StringWriter writer = new StringWriter();
			StreamResult result = new StreamResult(writer);
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.transform(domSource, result);
			return writer.toString();
		} catch (TransformerException e) {
			Logger.info(CLASS_NAME + ".asString: XML to String conversion: ", e);
			return null;
		}
	}

	/**
	 * Finishes all StudyResults of this worker of this study that aren't in
	 * state FINISHED. Each worker can do only one study with the same ID and
	 * the same time.
	 */
	public void finishAllPriorStudyResults(T worker, StudyModel study) {
		List<StudyResult> studyResultList = worker.getStudyResultList();
		for (StudyResult studyResult : studyResultList) {
			if (studyResult.getStudy().getId() == study.getId()
					&& !studyDone(studyResult)) {
				finishStudy(false, PublixErrorMessages.STUDY_NEVER_FINSHED,
						studyResult);
			}
		}
	}

	/**
	 * Gets the last StudyResult of this worker of this study. Throws an
	 * ForbiddenPublixException if the StudyResult is already 'done' or this
	 * worker never started a StudyResult of this study. It either returns a
	 * StudyResult or throws an exception but never returns null.
	 */
	public StudyResult retrieveWorkersLastStudyResult(T worker, StudyModel study)
			throws ForbiddenPublixException {
		int studyResultListSize = worker.getStudyResultList().size();
		for (int i = (studyResultListSize - 1); i >= 0; i--) {
			StudyResult studyResult = worker.getStudyResultList().get(i);
			if (studyResult.getStudy().getId() == study.getId()) {
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

	public ComponentResult retrieveLastComponentResult(StudyResult studyResult) {
		List<ComponentResult> componentResultList = studyResult
				.getComponentResultList();
		if (!componentResultList.isEmpty()) {
			return componentResultList.get(componentResultList.size() - 1);
		}
		return null;
	}

	/**
	 * Returns the last ComponentResult of this studyResult if it's not
	 * FINISHED, FAILED, ABORTED or RELOADED. Returns null if it doesn't exists.
	 */
	public ComponentResult retrieveCurrentComponentResult(
			StudyResult studyResult) {
		ComponentResult componentResult = retrieveLastComponentResult(studyResult);
		if (!componentDone(componentResult)) {
			return componentResult;
		}
		return null;
	}

	/**
	 * Gets the ComponentResult from the storage or if it doesn't exist yet
	 * starts one.
	 */
	public ComponentResult retrieveStartedComponentResult(
			ComponentModel component, StudyResult studyResult)
			throws ForbiddenReloadException {
		ComponentResult componentResult = retrieveCurrentComponentResult(studyResult);
		// Start the component if it was never started (== null) or if it's
		// a reload of the component
		if (componentResult == null) {
			componentResult = startComponent(component, studyResult);
		}
		return componentResult;
	}

	public ComponentModel retrieveLastComponent(StudyResult studyResult) {
		List<ComponentResult> componentResultList = studyResult
				.getComponentResultList();
		if (componentResultList.size() > 0) {
			return componentResultList.get(componentResultList.size() - 1)
					.getComponent();
		}
		return null;
	}

	public ComponentModel retrieveFirstActiveComponent(StudyModel study)
			throws NotFoundPublixException {
		ComponentModel component = study.getFirstComponent();
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

	public ComponentModel retrieveNextActiveComponent(StudyResult studyResult) {
		ComponentModel currentComponent = retrieveLastComponent(studyResult);
		ComponentModel nextComponent = studyResult.getStudy().getNextComponent(
				currentComponent);
		// Find next active component or null if study has no more components
		while (nextComponent != null && !nextComponent.isActive()) {
			nextComponent = studyResult.getStudy().getNextComponent(
					nextComponent);
		}
		return nextComponent;
	}

	public ComponentModel retrieveComponent(StudyModel study, Long componentId)
			throws NotFoundPublixException, BadRequestPublixException,
			ForbiddenPublixException {
		ComponentModel component = componentDao.findById(componentId);
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

	public ComponentModel retrieveComponentByPosition(Long studyId,
			Integer position) throws PublixException {
		StudyModel study = retrieveStudy(studyId);
		if (position == null) {
			throw new BadRequestPublixException(
					PublixErrorMessages.COMPONENTS_POSITION_NOT_NULL);
		}
		ComponentModel component;
		try {
			component = study.getComponent(position);
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundPublixException(
					errorMessages.noComponentAtPosition(study.getId(), position));
		}
		return component;
	}

	public StudyModel retrieveStudy(Long studyId)
			throws NotFoundPublixException {
		StudyModel study = studyDao.findById(studyId);
		if (study == null) {
			throw new NotFoundPublixException(
					errorMessages.studyNotExist(studyId));
		}
		return study;
	}

	public String getDataFromRequestBody(RequestBody requestBody)
			throws UnsupportedMediaTypePublixException {
		String data = getRequestBodyAsString(requestBody);
		if (data == null) {
			throw new UnsupportedMediaTypePublixException(
					PublixErrorMessages.SUBMITTED_DATA_UNKNOWN_FORMAT);
		}
		return data;
	}

	public void checkComponentBelongsToStudy(StudyModel study,
			ComponentModel component) throws PublixException {
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
	public boolean finishedStudyAlready(T worker, StudyModel study) {
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
	public boolean didStudyAlready(T worker, StudyModel study) {
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().getId() == study.getId()) {
				return true;
			}
		}
		return false;
	}

	public boolean studyDone(StudyResult studyResult) {
		StudyState state = studyResult.getStudyState();
		return state == StudyState.FINISHED || state == StudyState.ABORTED
				|| state == StudyState.FAIL;
	}

	public boolean componentDone(ComponentResult componentResult) {
		ComponentState state = componentResult.getComponentState();
		return state == ComponentState.FINISHED
				|| state == ComponentState.ABORTED
				|| state == ComponentState.FAIL
				|| state == ComponentState.RELOADED;
	}

}
