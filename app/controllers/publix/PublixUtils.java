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
import models.StudyModel;
import models.results.ComponentResult;
import models.results.ComponentResult.ComponentState;
import models.results.StudyResult;
import models.results.StudyResult.StudyState;
import models.workers.Worker;

import org.w3c.dom.Document;

import play.Logger;
import play.mvc.Http.RequestBody;
import services.PersistanceUtils;

import com.fasterxml.jackson.databind.JsonNode;

import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.ForbiddenReloadException;
import exceptions.NotFoundPublixException;
import exceptions.PublixException;
import exceptions.UnsupportedMediaTypePublixException;

/**
 * Utilility class with functions that are common for all classes that extend
 * Publix and don't belong in a controller.
 * 
 * @author Kristian Lange
 */
public abstract class PublixUtils<T extends Worker> {

	private static final String CLASS_NAME = PublixUtils.class.getSimpleName();

	private PublixErrorMessages<T> errorMessages;

	public PublixUtils(PublixErrorMessages<T> errorMessages) {
		this.errorMessages = errorMessages;
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
					PublixErrorMessages.workerNotExist(workerIdStr));
		}

		Worker worker = Worker.findById(workerId);
		if (worker == null) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.workerNotExist(workerId));
		}
		return worker;
	}

	/**
	 * Start or restart a component
	 */
	public ComponentResult startComponent(ComponentModel component,
			StudyResult studyResult) throws ForbiddenPublixException,
			ForbiddenReloadException {
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
					String errorMsg = PublixErrorMessages
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
		return PersistanceUtils.createComponentResult(studyResult, component);
	}

	private void finishComponentResult(ComponentResult componentResult,
			ComponentState state) {
		componentResult.setComponentState(state);
		componentResult.setEndDate(new Timestamp(new Date().getTime()));
		componentResult.merge();
	}

	/**
	 * Sets cookie with studyId and componentId so the component script has them
	 * too.
	 */
	public static void setIdCookie(StudyResult studyResult,
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
		Publix.response().setCookie(Publix.ID_COOKIE_NAME, sb.toString());
	}

	/**
	 * Discard cookie with studyId and componentId.
	 */
	public static void discardIdCookie() {
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
			componentResult.merge();
		}

		// Set StudyResult to state ABORTED and set message
		studyResult.setStudyState(StudyState.ABORTED);
		studyResult.setAbortMsg(message);
		studyResult.setEndDate(new Timestamp(new Date().getTime()));
		studyResult.merge();
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
		studyResult.merge();
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

	public static String getRequestBodyAsString(RequestBody requestBody) {
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
	public static String asString(Document doc) {
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
	 * Get the last StudyResult of this worker of this study.
	 */
	public StudyResult retrieveWorkersLastStudyResult(T worker, StudyModel study)
			throws ForbiddenPublixException {
		StudyResult studyResult;
		int studyResultListSize = worker.getStudyResultList().size();
		for (int i = (studyResultListSize - 1); i >= 0; i--) {
			studyResult = worker.getStudyResultList().get(i);
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
	 * FINISHED, FAILED, ABORTED or RELOADED. Returns null it it doesn't exists.
	 */
	public ComponentResult retrieveCurrentComponentResult(
			StudyResult studyResult) {
		ComponentResult componentResult = retrieveLastComponentResult(studyResult);
		if (!componentDone(componentResult)) {
			return componentResult;
		}
		return null;
	}

	public ComponentResult retrieveStartedComponentResult(
			ComponentModel component, StudyResult studyResult,
			ComponentState maxAllowedComponentState)
			throws ForbiddenPublixException, ForbiddenReloadException {
		ComponentResult componentResult = retrieveCurrentComponentResult(studyResult);
		// Start the component if it was never started (== null) or if it's
		// a restart of the component (The states of a componentResult are
		// ordered, e.g. it's forbidden to put DATA_RETRIEVED after
		// RESULTDATA_POSTED.)
		if (componentResult == null
				|| componentResult.getComponentState().ordinal() > maxAllowedComponentState
						.ordinal()) {
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
					PublixErrorMessages.studyHasNoActiveComponents(study.getId()));
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
		ComponentModel component = ComponentModel.findById(componentId);
		if (component == null) {
			throw new NotFoundPublixException(PublixErrorMessages.componentNotExist(
					study.getId(), componentId));
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			throw new BadRequestPublixException(
					PublixErrorMessages.componentNotBelongToStudy(study.getId(),
							componentId));
		}
		if (!component.isActive()) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.componentNotActive(study.getId(), componentId));
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
					PublixErrorMessages.noComponentAtPosition(study.getId(), position));
		}
		return component;
	}

	public StudyModel retrieveStudy(Long studyId)
			throws NotFoundPublixException {
		StudyModel study = StudyModel.findById(studyId);
		if (study == null) {
			throw new NotFoundPublixException(
					PublixErrorMessages.studyNotExist(studyId));
		}
		return study;
	}

	public String getDataFromRequestBody(RequestBody requestBody,
			ComponentModel component)
			throws UnsupportedMediaTypePublixException {
		String data = getRequestBodyAsString(requestBody);
		if (data == null) {
			throw new UnsupportedMediaTypePublixException(
					PublixErrorMessages.submittedDataUnknownFormat(component
							.getStudy().getId(), component.getId()));
		}
		return data;
	}

	public void checkComponentBelongsToStudy(StudyModel study,
			ComponentModel component) throws PublixException {
		if (!component.getStudy().equals(study)) {
			throw new BadRequestPublixException(
					PublixErrorMessages.componentNotBelongToStudy(study.getId(),
							component.getId()));
		}
	}

	/**
	 * Checks if the worker did this study already.
	 */
	public boolean didStudyAlready(T worker, StudyModel study) {
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().equals(study) && studyDone(studyResult)) {
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
