package controllers.publix;

import java.io.StringWriter;
import java.util.List;

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
import services.ErrorMessages;
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

	private ErrorMessages<T> errorMessages;

	public PublixUtils(ErrorMessages<T> errorMessages) {
		this.errorMessages = errorMessages;
	}

	public abstract T retrieveWorker() throws PublixException;

	public abstract void checkWorkerAllowedToDoStudy(T worker, StudyModel study)
			throws ForbiddenPublixException;

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
					lastComponentResult
							.setComponentState(ComponentState.RELOADED);
					lastComponentResult.merge();
				} else {
					// Worker tried to reload a non-reloadable component -> end
					// component and study with FAIL
					lastComponentResult.setComponentState(ComponentState.FAIL);
					lastComponentResult.merge();
					String errorMsg = ErrorMessages
							.componentNotAllowedToReload(studyResult.getStudy()
									.getId(), component.getId());
					// exceptionalFinishStudy(studyResult, errorMsg);
					throw new ForbiddenReloadException(errorMsg);
				}
			} else {
				lastComponentResult.setComponentState(ComponentState.FINISHED);
				lastComponentResult.merge();
			}
		}
		return PersistanceUtils.createComponentResult(studyResult, component);
	}

	/**
	 * Sets cookie with studyId and componentId so the component script has them
	 * too.
	 */
	public static void setIdCookie(StudyModel study, ComponentModel component) {
		String cookieStr = Publix.STUDY_ID + "="
				+ String.valueOf(study.getId()) + "&" + Publix.COMPONENT_ID
				+ "=" + String.valueOf(component.getId()) + "&"
				+ Publix.POSITION + "="
				+ String.valueOf(study.getComponentPosition(component));
		Publix.response().setCookie(Publix.ID_COOKIE_NAME, cookieStr);
	}

	/**
	 * Discard cookie with studyId and componentId.
	 */
	public static void discardIdCookie() {
		Publix.response().discardCookie(Publix.ID_COOKIE_NAME);
	}

	public String finishStudy(Boolean successful, String errorMsg,
			StudyResult studyResult) {
		finishAllComponentResults(studyResult);
		String confirmationCode;
		if (successful) {
			confirmationCode = studyResult.getWorker()
					.generateConfirmationCode();
			studyResult.setStudyState(StudyState.FINISHED);
		} else {
			confirmationCode = "fail";
			studyResult.setStudyState(StudyState.FAIL);
		}
		studyResult.setConfirmationCode(confirmationCode);
		studyResult.setErrorMsg(errorMsg);
		studyResult.merge();
		return confirmationCode;
	}

	public void finishAllComponentResults(StudyResult studyResult) {
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			ComponentState state = componentResult.getComponentState();
			if (!(state == ComponentState.FINISHED
					|| state == ComponentState.FAIL || state == ComponentState.RELOADED)) {
				componentResult.setComponentState(ComponentState.FINISHED);
				componentResult.merge();
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
					&& studyResult.getStudyState() != StudyState.FINISHED) {
				finishStudy(false, ErrorMessages.STUDY_NEVER_FINSHED,
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
				if (studyResult.getStudyState() == StudyState.FINISHED
						|| studyResult.getStudyState() == StudyState.FAIL) {
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
	 * Get the open (not FINISHED, FAILED, or RELOADED) componentResult of this
	 * studyResult.
	 */
	public ComponentResult retrieveOpenComponentResult(StudyResult studyResult) {
		ComponentResult componentResult = retrieveLastComponentResult(studyResult);
		ComponentState state = componentResult.getComponentState();
		if (!(state == ComponentState.FINISHED || state == ComponentState.FAIL || state == ComponentState.RELOADED)) {
			return componentResult;
		}
		return null;
	}

	public ComponentResult retrieveStartedComponentResult(
			ComponentModel component, StudyResult studyResult,
			ComponentState maxAllowedComponentState)
			throws ForbiddenPublixException, ForbiddenReloadException {
		ComponentResult componentResult = retrieveOpenComponentResult(studyResult);
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
					ErrorMessages.studyHasNoActiveComponents(study.getId()));
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
			throw new NotFoundPublixException(ErrorMessages.componentNotExist(
					study.getId(), componentId));
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			throw new BadRequestPublixException(
					ErrorMessages.componentNotBelongToStudy(study.getId(),
							componentId));
		}
		if (!component.isActive()) {
			throw new ForbiddenPublixException(
					ErrorMessages.componentNotActive(study.getId(), componentId));
		}
		return component;
	}

	public ComponentModel retrieveComponentByPosition(Long studyId,
			Integer position) throws PublixException {
		StudyModel study = retrieveStudy(studyId);
		if (position == null) {
			throw new BadRequestPublixException(
					ErrorMessages.COMPONENTS_POSITION_NOT_NULL);
		}
		ComponentModel component;
		try {
			component = study.getComponent(position);
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundPublixException(
					ErrorMessages.noComponentAtPosition(study.getId(), position));
		}
		return component;
	}

	public StudyModel retrieveStudy(Long studyId)
			throws NotFoundPublixException {
		StudyModel study = StudyModel.findById(studyId);
		if (study == null) {
			throw new NotFoundPublixException(
					ErrorMessages.studyNotExist(studyId));
		}
		return study;
	}

	public String getDataFromRequestBody(RequestBody requestBody,
			ComponentModel component)
			throws UnsupportedMediaTypePublixException {
		String data = getRequestBodyAsString(requestBody);
		if (data == null) {
			throw new UnsupportedMediaTypePublixException(
					ErrorMessages.submittedDataUnknownFormat(component
							.getStudy().getId(), component.getId()));
		}
		return data;
	}

	public String retrieveMechArgShowCookie() throws ForbiddenPublixException {
		String mechArgShow = Publix.session(MAPublix.MECHARG_SHOW);
		if (mechArgShow == null) {
			throw new ForbiddenPublixException(
					ErrorMessages.STUDY_OR_COMPONENT_NEVER_STARTED_FROM_MECHARG);
		}
		return mechArgShow;
	}

	public void checkComponentBelongsToStudy(StudyModel study,
			ComponentModel component) throws PublixException {
		if (!component.getStudy().equals(study)) {
			throw new BadRequestPublixException(
					ErrorMessages.componentNotBelongToStudy(study.getId(),
							component.getId()));
		}
	}

}
