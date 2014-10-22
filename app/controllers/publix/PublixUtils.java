package controllers.publix;

import java.io.StringWriter;
import java.util.List;
import java.util.ListIterator;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import models.results.ComponentResult.ComponentState;
import models.results.StudyResult;
import models.results.StudyResult.StudyState;
import models.workers.Worker;

import org.w3c.dom.Document;

import play.Logger;
import play.db.jpa.JPA;
import play.mvc.Http.Request;
import play.mvc.Http.RequestBody;
import services.ErrorMessages;
import services.PersistanceUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.net.MediaType;

import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
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

	public ComponentResult startComponent(ComponentModel component,
			StudyResult studyResult) throws ForbiddenPublixException {
		return startComponent(component, studyResult, MediaType.HTML_UTF_8);
	}

	/**
	 * Start or restart a component
	 */
	public ComponentResult startComponent(ComponentModel component,
			StudyResult studyResult, MediaType errorMediaType)
			throws ForbiddenPublixException {
		// Only one component of the same kind can be done in the same study
		// by the same worker. Exception: If a component is reloadable,
		// the old component result will be deleted and a new one generated.
		ComponentResult componentResult = retrieveOpenComponentResult(
				component, studyResult);
		if (componentResult != null) {
			if (component.isReloadable()) {
				// Persistance.removeComponentResult(componentResult);
				componentResult.setComponentState(ComponentState.RELOADED);
				componentResult.merge();
			} else {
				// Worker tried to reload a non-reloadable component -> end
				// study and component with fail
				componentResult.setComponentState(ComponentState.FAIL);
				componentResult.merge();
				exceptionalFinishStudy(studyResult);
				throw new ForbiddenPublixException(
						ErrorMessages.componentNotAllowedToReload(studyResult
								.getStudy().getId(), component.getId()));
			}
		}
		// Only one ComponentResult can be open (not in state FINISHED or FAIL
		// at the same time. To start a new ComponentResult, finish all other
		// ones. This is probably redundant but it good to know that at this
		// point all componentResults are finished.
		finishAllComponentResults(studyResult);
		return PersistanceUtils.createComponentResult(studyResult, component);
	}

	public String finishStudy(Boolean successful, StudyResult studyResult) {
		finishAllComponentResults(studyResult);
		String confirmationCode;
		if (successful) {
			confirmationCode = studyResult.generateConfirmationCode();
			studyResult.setStudyState(StudyState.FINISHED);
		} else {
			confirmationCode = "fail";
			studyResult.setStudyState(StudyState.FAIL);
		}
		studyResult.merge();
		return confirmationCode;
	}

	public void exceptionalFinishStudy(StudyResult studyResult) {
		finishStudy(false, studyResult);
		// Since an exception triggers a transaction rollback we have
		// to commit the transaction manually.
		if (JPA.em().getTransaction().isActive()) {
			JPA.em().flush();
			JPA.em().getTransaction().commit();
		}
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

	public abstract T retrieveWorker() throws PublixException;

	public abstract T retrieveWorker(MediaType errorMediaType)
			throws PublixException;

	public StudyResult retrieveWorkersStartedStudyResult(T worker,
			StudyModel study) throws ForbiddenPublixException {
		return retrieveWorkersStartedStudyResult(worker, study,
				MediaType.HTML_UTF_8);
	}

	public StudyResult retrieveWorkersStartedStudyResult(T worker,
			StudyModel study, MediaType errorMediaType)
			throws ForbiddenPublixException {
		// Iterate reversely through the worker's study result list and
		// take the first one with the right study ID and that is in state
		// STARTED or DATA_RETRIEVED.
		ListIterator<StudyResult> li = worker.getStudyResultList()
				.listIterator(worker.getStudyResultList().size());
		while (li.hasPrevious()) {
			StudyResult studyResultTemp = li.previous();
			StudyState studyState = studyResultTemp.getStudyState();
			if (studyResultTemp.getStudy().getId() == study.getId()
					&& (studyState == StudyState.STARTED || studyState == StudyState.DATA_RETRIEVED)) {
				return studyResultTemp;
			}
		}

		// Worker never started the study
		throw new ForbiddenPublixException(
				errorMessages.workerNeverStartedStudy(worker, study.getId()),
				errorMediaType);
	}

	public StudyResult retrieveWorkersLastStudyResult(T worker, StudyModel study)
			throws ForbiddenPublixException {
		return retrieveWorkersLastStudyResult(worker, study,
				MediaType.HTML_UTF_8);
	}

	public StudyResult retrieveWorkersLastStudyResult(T worker,
			StudyModel study, MediaType errorMediaType)
			throws ForbiddenPublixException {
		StudyResult studyResult;
		int studyResultListSize = worker.getStudyResultList().size();
		for (int i = (studyResultListSize - 1); i >= 0; i--) {
			studyResult = worker.getStudyResultList().get(i);
			if (studyResult.getStudy().getId() == study.getId()) {
				return studyResult;
			}
		}
		throw new ForbiddenPublixException(errorMessages.workerNeverDidStudy(
				worker, study.getId()), errorMediaType);
	}

	/**
	 * Get the last open (not FINISHED and not in state FAILED) componentResult
	 * of this component in this studyResult.
	 */
	public ComponentResult retrieveOpenComponentResult(
			ComponentModel component, StudyResult studyResult) {
		// Iterate reversely through the list of componentResults (the open one
		// should be the last of this component)
		int componentResultListSize = studyResult.getComponentResultList()
				.size();
		ComponentResult componentResult;
		for (int i = (componentResultListSize - 1); i >= 0; i--) {
			componentResult = studyResult.getComponentResultList().get(i);
			ComponentState state = componentResult.getComponentState();
			if (componentResult.getComponent().getId() == component.getId()
					&& !(state == ComponentState.FINISHED
							|| state == ComponentState.FAIL || state == ComponentState.RELOADED)) {
				return componentResult;
			}
		}
		return null;
	}

	public ComponentResult retrieveStartedComponentResult(
			ComponentModel component, StudyResult studyResult,
			ComponentState maxAllowedComponentState)
			throws ForbiddenPublixException {
		return retrieveStartedComponentResult(component, studyResult,
				maxAllowedComponentState, MediaType.HTML_UTF_8);
	}

	public ComponentResult retrieveStartedComponentResult(
			ComponentModel component, StudyResult studyResult,
			ComponentState maxAllowedComponentState, MediaType errorMediaType)
			throws ForbiddenPublixException {
		ComponentResult componentResult = retrieveOpenComponentResult(
				component, studyResult);
		if (componentResult == null) {
			// If component was never started, conveniently start it
			componentResult = startComponent(component, studyResult,
					errorMediaType);
		}
		// The states of a componentResult are ordered, e.g. it's forbidden to
		// put DATA_RETRIEVED after RESULTDATA_POSTED.
		if (componentResult.getComponentState().ordinal() > maxAllowedComponentState
				.ordinal()) {
			// Restart component
			componentResult = startComponent(component, studyResult,
					errorMediaType);
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
		return retrieveComponent(study, componentId, MediaType.HTML_UTF_8);
	}

	public ComponentModel retrieveComponent(StudyModel study, Long componentId,
			MediaType errorMediaType) throws NotFoundPublixException,
			BadRequestPublixException, ForbiddenPublixException {
		ComponentModel component = ComponentModel.findById(componentId);
		if (component == null) {
			throw new NotFoundPublixException(ErrorMessages.componentNotExist(
					study.getId(), componentId), errorMediaType);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			throw new BadRequestPublixException(
					ErrorMessages.componentNotBelongToStudy(study.getId(),
							componentId), errorMediaType);
		}
		if (!component.isActive()) {
			throw new ForbiddenPublixException(
					ErrorMessages
							.componentNotActive(study.getId(), componentId),
					errorMediaType);
		}
		return component;
	}

	public ComponentModel retrieveComponentByPosition(Long studyId,
			Integer position) throws PublixException {
		return retrieveComponentByPosition(studyId, position,
				MediaType.HTML_UTF_8);
	}

	public ComponentModel retrieveComponentByPosition(Long studyId,
			Integer position, MediaType errorMediaType) throws PublixException {
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
					ErrorMessages
							.noComponentAtPosition(study.getId(), position),
					errorMediaType);
		}
		return component;
	}

	public StudyModel retrieveStudy(Long studyId)
			throws NotFoundPublixException {
		return retrieveStudy(studyId, MediaType.HTML_UTF_8);
	}

	public StudyModel retrieveStudy(Long studyId, MediaType errorMediaType)
			throws NotFoundPublixException {
		StudyModel study = StudyModel.findById(studyId);
		if (study == null) {
			throw new NotFoundPublixException(
					ErrorMessages.studyNotExist(studyId), errorMediaType);
		}
		return study;
	}

	public String getDataFromRequestBody(RequestBody requestBody,
			ComponentModel component)
			throws UnsupportedMediaTypePublixException {
		return getDataFromRequestBody(requestBody, component,
				MediaType.HTML_UTF_8);
	}

	public String getDataFromRequestBody(RequestBody requestBody,
			ComponentModel component, MediaType errorMediaType)
			throws UnsupportedMediaTypePublixException {
		String data = getRequestBodyAsString(requestBody);
		if (data == null) {
			throw new UnsupportedMediaTypePublixException(
					ErrorMessages.submittedDataUnknownFormat(component
							.getStudy().getId(), component.getId()),
					errorMediaType);
		}
		return data;
	}

	/**
	 * Generates an URL with protocol HTTP, request's hostname, given urlPath,
	 * and requests query string.
	 */
	public static String getUrlWithRequestQueryString(Request request,
			String urlPath) {
		String requestUrlPath = request.uri();
		String requestHostName = request.host();
		int queryBegin = requestUrlPath.lastIndexOf("?");
		if (queryBegin > 0) {
			String queryString = requestUrlPath.substring(queryBegin + 1);
			urlPath = urlPath + "?" + queryString;
		}
		return "http://" + requestHostName + urlPath;
	}
	
	public void checkMembership(StudyModel study, UserModel loggedInUser)
			throws ForbiddenPublixException {
		checkMembership(study, loggedInUser, MediaType.HTML_UTF_8);
	}

	public void checkMembership(StudyModel study, UserModel loggedInUser,
			MediaType errorMediaType) throws ForbiddenPublixException {
		if (!study.hasMember(loggedInUser)) {
			throw new ForbiddenPublixException(ErrorMessages.notMember(
					loggedInUser.getName(), loggedInUser.getEmail(),
					study.getId(), study.getTitle()), errorMediaType);
		}
	}

	public String retrieveMechArgShow() throws ForbiddenPublixException {
		return retrieveMechArgShow(MediaType.HTML_UTF_8);
	}

	public String retrieveMechArgShow(MediaType mediaType)
			throws ForbiddenPublixException {
		String mechArgShow = Publix.session(MAPublix.MECHARG_SHOW);
		if (mechArgShow == null) {
			throw new ForbiddenPublixException(
					ErrorMessages.STUDY_OR_COMPONENT_NEVER_STARTED_FROM_MECHARG,
					mediaType);
		}
		return mechArgShow;
	}

}
