package controllers;

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
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import models.workers.Worker;

import org.w3c.dom.Document;

import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.net.MediaType;

import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.OkPublixException;

public class Publix extends Controller {

	public static final String WORKER_ID = "workerId";
	public static final String COMPONENT_ID = "componentId";
	public static final String ASSIGNMENT_ID_NOT_AVAILABLE = "ASSIGNMENT_ID_NOT_AVAILABLE";

	public static Result index() {
		return ok();
	}

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result startStudy(Long studyId) throws Exception {
		// Get MTurk query parameters
		String mtWorkerId = request().getQueryString("workerId");
		String mtAssignmentId = request().getQueryString("assignmentId");
		String mtHitId = request().getQueryString("hitId");
		Logger.info("startStudy: studyId " + studyId + ", "
				+ "Parameters from MTurk: workerId " + mtWorkerId + ", "
				+ "assignmentId " + mtAssignmentId + ", " + "hitId " + mtHitId);

		StudyModel study = retrieveStudy(studyId);

		checkForMTurkPreview(studyId, mtAssignmentId);

		// Check worker
		if (mtWorkerId == null) {
			throw new BadRequestPublixException(
					workerNotInQueryParameter(mtWorkerId));
		}
		Worker worker = MTWorker.findByMTWorkerId(mtWorkerId);
		if (worker == null) {
			worker = createWorker(mtWorkerId);
		}
		if (!worker.isAllowedToStartStudy(studyId)) {
			throw new ForbiddenPublixException(workerNotAllowedStudy(
					worker.getId(), studyId));
		}
		session(WORKER_ID, String.valueOf(worker.getId()));

		createStudyResult(study, worker);

		// Start first component
		ComponentModel component = study.getFirstComponent();
		if (component == null) {
			throw new BadRequestPublixException(studyHasNoComponents(studyId));
		}
		return startComponent(studyId, component.getId());
	}

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result startComponent(Long studyId, Long componentId)
			throws Exception {
		Logger.info("startComponent: studyId " + studyId + ", " + "workerId "
				+ session(WORKER_ID));

		Worker worker = retrieveWorker();
		StudyModel study = retrieveStudy(studyId);
		ComponentModel component = retrieveComponent(study, componentId);

		// Check worker
		StudyResult studyResult = retrieveWorkersStartedStudyResult(worker,
				study);

		ComponentResult componentResult = retrieveComponentResult(component,
				studyResult);
		if (componentResult != null) {
			// Only one component of the same kind can be done in the same study
			// by the same worker. Exception: If a component is reloadable,
			// the old component result will be deleted and a new one generated.
			component = componentResult.getComponent();
			if (component.isReloadable()) {
				studyResult.removeComponentResult(componentResult);
			} else {
				// Worker tried to reload a non-reloadable component -> end
				// study with fail
				finishStudy(false, studyResult);
				throw new ForbiddenPublixException(componentNotAllowedToReload(
						study.getId(), component.getId()));
			}
		}
		finishAllComponentResults(studyResult);
		createComponentResult(studyResult, component);

		return redirect(component.getViewUrl());
	}

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result startNextComponent(Long studyId) throws Exception {
		Logger.info("startNextComponent: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID));

		Worker worker = retrieveWorker();
		StudyModel study = retrieveStudy(studyId);
		StudyResult studyResult = retrieveWorkersStartedStudyResult(worker,
				study);

		// Get next component in study
		ComponentModel currentComponent = retrieveLastComponent(studyResult);
		ComponentModel nextComponent = studyResult.getStudy().getNextComponent(
				currentComponent);
		if (nextComponent == null) {
			// No more components in study -> finish study
			return finishStudy(studyId);
		}

		return startComponent(studyId, nextComponent.getId());
	}

	/**
	 * HTTP type: Ajax GET request
	 */
	@Transactional
	public static Result getComponentData(Long studyId, Long componentId)
			throws Exception {
		Logger.info("getComponentData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		Worker worker = retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = retrieveComponent(study, componentId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyResult studyResult = retrieveWorkersStartedStudyResult(worker,
				study, MediaType.TEXT_JAVASCRIPT_UTF_8);

		// Check component result
		ComponentResult componentResult = retrieveComponentResult(component,
				studyResult);
		if (componentResult == null
				|| componentResult.getComponentState() != ComponentState.STARTED) {
			throw new ForbiddenPublixException(componentNeverStarted(studyId,
					componentId), MediaType.TEXT_JAVASCRIPT_UTF_8);
		}

		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		componentResult.merge();

		return ok(component.asJsonForPublic());
	}

	/**
	 * HTTP type: Ajax POST request
	 */
	@Transactional
	public static Result submitResultData(Long studyId, Long componentId)
			throws Exception {
		Logger.info("submitResultData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		Worker worker = retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = retrieveComponent(study, componentId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyResult studyResult = retrieveWorkersStartedStudyResult(worker,
				study, MediaType.TEXT_JAVASCRIPT_UTF_8);

		// Check component result
		ComponentResult componentResult = retrieveComponentResult(component,
				studyResult);
		if (componentResult == null
				|| componentResult.getComponentState() == ComponentState.FINISHED
				|| componentResult.getComponentState() == ComponentState.FAIL) {
			throw new ForbiddenPublixException(
					componentAlreadyFinishedOrFailed(study.getId(),
							component.getId()), MediaType.TEXT_JAVASCRIPT_UTF_8);
		}

		// Get data in format JSON, text or XML and convert to String
		String data = getDataAsString();
		if (data == null) {
			componentResult.setComponentState(ComponentState.FAIL);
			componentResult.merge();
			throw new BadRequestPublixException(submittedDataUnknownFormat(
					study.getId(), component.getId()),
					MediaType.TEXT_JAVASCRIPT_UTF_8);
		}

		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		componentResult.merge();

		return ok();
	}

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result finishStudy(Long studyId) throws Exception {
		return finishStudy(studyId, true, null);
	}

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result finishStudy(Long studyId, Boolean successful,
			String errorMsg) throws Exception {
		Logger.info("finishStudy: studyId " + studyId + ", " + "successful "
				+ successful + ", " + "workerId " + session(WORKER_ID));

		Worker worker = retrieveWorker();
		StudyModel study = retrieveStudy(studyId);
		StudyResult studyResult = retrieveWorkersStartedStudyResult(worker,
				study, MediaType.TEXT_JAVASCRIPT_UTF_8);

		String confirmationCode = finishStudy(successful, studyResult);

		if (!successful) {
			throw new OkPublixException(errorMsg);
		}
		return ok(views.html.publix.end.render(study.getId(), confirmationCode));
	}

	private static String finishStudy(Boolean successful,
			StudyResult studyResult) {
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
		session().remove(WORKER_ID);
		return confirmationCode;
	}

	/**
	 * In case the client side wants to log an error.<br>
	 * HTTP type: Ajax GET request
	 */
	public static Result logError() {
		String msg = request().body().asText();
		Logger.error("Client-side error: " + msg);
		return ok();
	}

	private static void checkForMTurkPreview(Long studyId, String mtAssignmentId)
			throws BadRequestPublixException {
		if (mtAssignmentId == null) {
			throw new BadRequestPublixException(assignmentIdNotSpecified());
		}
		if (mtAssignmentId.equals(ASSIGNMENT_ID_NOT_AVAILABLE)) {
			// It's a preview coming from Mechanical Turk -> no previews
			throw new BadRequestPublixException(noPreviewAvailable(studyId));
		}
	}

	private static Worker createWorker(String mtWorkerId) {
		Worker worker;
		if (isRequestFromMTurkSandbox()) {
			worker = new MTSandboxWorker(mtWorkerId);
		} else {
			worker = new MTWorker(mtWorkerId);
		}
		worker.persist();
		return worker;
	}

	private static StudyResult createStudyResult(StudyModel study, Worker worker) {
		StudyResult studyResult = new StudyResult(study);
		studyResult.persist();
		worker.addStudyResult(studyResult);
		worker.merge();
		return studyResult;
	}

	private static StudyResult retrieveWorkersStartedStudyResult(Worker worker,
			StudyModel study) throws ForbiddenPublixException {
		return retrieveWorkersStartedStudyResult(worker, study,
				MediaType.HTML_UTF_8);
	}

	private static StudyResult retrieveWorkersStartedStudyResult(Worker worker,
			StudyModel study, MediaType errorMediaType)
			throws ForbiddenPublixException {
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().getId() == study.getId()
					&& studyResult.getStudyState() == StudyState.STARTED) {
				// Since there is only one study result of the same study
				// allowed to be in STARTED, return the first one
				return studyResult;
			}
		}
		// Worker never started the study
		throw new ForbiddenPublixException(workerNeverStartedStudy(
				worker.getId(), study.getId()), errorMediaType);
	}

	private static ComponentResult retrieveComponentResult(
			ComponentModel component, StudyResult studyResult) {
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			if (componentResult.getComponent().getId() == component.getId()) {
				return componentResult;
			}
		}
		return null;
	}

	private static ComponentModel retrieveLastComponent(StudyResult studyResult) {
		List<ComponentResult> componentResultList = studyResult
				.getComponentResultList();
		if (componentResultList.size() > 0) {
			return componentResultList.get(componentResultList.size() - 1)
					.getComponent();
		}
		return null;
	}

	private static ComponentModel retrieveComponent(StudyModel study,
			Long componentId) throws Exception {
		return retrieveComponent(study, componentId, MediaType.HTML_UTF_8);
	}

	private static ComponentModel retrieveComponent(StudyModel study,
			Long componentId, MediaType errorMediaType) throws Exception {
		ComponentModel component = ComponentModel.findById(componentId);
		if (component == null) {
			throw new BadRequestPublixException(componentNotExist(
					study.getId(), componentId), errorMediaType);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			throw new BadRequestPublixException(componentNotBelongToStudy(
					study.getId(), componentId), errorMediaType);
		}
		return component;
	}

	private static StudyModel retrieveStudy(Long studyId) throws Exception {
		return retrieveStudy(studyId, MediaType.HTML_UTF_8);
	}

	private static StudyModel retrieveStudy(Long studyId,
			MediaType errorMediaType) throws Exception {
		StudyModel study = StudyModel.findById(studyId);
		if (study == null) {
			throw new BadRequestPublixException(studyNotExist(studyId),
					errorMediaType);
		}
		return study;
	}

	private static Worker retrieveWorker() throws Exception {
		return retrieveWorker(MediaType.HTML_UTF_8);
	}

	private static Worker retrieveWorker(MediaType errorMediaType)
			throws Exception {
		String workerIdStr = session(WORKER_ID);
		if (workerIdStr == null) {
			// No worker id in session -> study never started
			throw new ForbiddenPublixException(noWorkerIdInSession(),
					errorMediaType);
		}
		long workerId;
		try {
			workerId = Long.parseLong(workerIdStr);
		} catch (NumberFormatException e) {
			throw new BadRequestPublixException(workerNotExist(workerIdStr),
					errorMediaType);
		}

		Worker worker = Worker.findById(workerId);
		if (worker == null) {
			throw new BadRequestPublixException(workerNotExist(workerId),
					errorMediaType);
		}
		if (!(worker instanceof MTWorker)) {
			throw new BadRequestPublixException(workerNotFromMTurk(workerId),
					errorMediaType);
		}
		return worker;
	}

	private static void finishAllComponentResults(StudyResult studyResult) {
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			if (componentResult.getComponentState() != ComponentState.FINISHED
					|| componentResult.getComponentState() != ComponentState.FAIL) {
				componentResult.setComponentState(ComponentState.FINISHED);
				componentResult.merge();
			}
		}
	}

	private static ComponentResult createComponentResult(
			StudyResult studyResult, ComponentModel component) {
		ComponentResult componentResult = new ComponentResult(component);
		componentResult.persist();
		studyResult.addComponentResult(componentResult);
		studyResult.merge();
		return componentResult;
	}

	private static String getDataAsString() {
		String text = request().body().asText();
		if (text != null) {
			return text;
		}

		JsonNode json = request().body().asJson();
		if (json != null) {
			return json.toString();
		}

		Document xml = request().body().asXml();
		if (xml != null) {
			return asString(xml);
		}

		return null;
	}

	/**
	 * Convert XML-Document to String
	 */
	private static String asString(Document doc) {
		try {
			DOMSource domSource = new DOMSource(doc);
			StringWriter writer = new StringWriter();
			StreamResult result = new StreamResult(writer);
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.transform(domSource, result);
			return writer.toString();
		} catch (TransformerException e) {
			Logger.info("XML to String conversion: ", e);
			return null;
		}
	}

	/**
	 * Returns true if the request comes from the Mechanical Turk Sandbox and
	 * false otherwise.
	 */
	private static boolean isRequestFromMTurkSandbox() {
		String turkSubmitTo = request().getQueryString("turkSubmitTo");
		if (turkSubmitTo != null && turkSubmitTo.contains("sandbox")) {
			return true;
		}
		return false;
	}

	private static String noWorkerIdInSession() {
		String errorMsg = "No worker id in session. Was the study started?";
		return errorMsg;
	}

	private static String workerNotInQueryParameter(String mtWorkerId) {
		String errorMsg = "MTurk's worker is missing in the query parameters.";
		return errorMsg;
	}

	private static String workerNotExist(Long workerId) {
		return workerNotExist(String.valueOf(workerId));
	}

	private static String workerNotExist(String workerIdStr) {
		String errorMsg = "A worker with id " + workerIdStr + " doesn't exist.";
		return errorMsg;
	}

	private static String workerNotFromMTurk(Long workerId) {
		String errorMsg = "The worker with id " + workerId
				+ " isn't a MTurk worker.";
		return errorMsg;
	}

	private static String assignmentIdNotSpecified() {
		String errorMsg = "No assignment id specified in query parameters.";
		return errorMsg;
	}

	private static String noPreviewAvailable(Long studyId) {
		String errorMsg = "No preview available for study " + studyId + ".";
		return errorMsg;
	}

	private static String workerNeverStartedStudy(Long workerId, Long studyId) {
		String errorMsg = "Worker " + workerId + " never started study "
				+ studyId + ".";
		return errorMsg;
	}

	private static String studyNotExist(Long studyId) {
		String errorMsg = "An study with id " + studyId + " doesn't exist.";
		return errorMsg;
	}

	private static String studyHasNoComponents(Long studyId) {
		String errorMsg = "The study with id " + studyId
				+ " has no components.";
		return errorMsg;
	}

	private static String componentNotExist(Long studyId, Long componentId) {
		String errorMsg = "An component with id " + componentId + " of study "
				+ studyId + " doesn't exist.";
		return errorMsg;
	}

	private static String componentNotBelongToStudy(Long studyId,
			Long componentId) {
		String errorMsg = "There is no study with id " + studyId
				+ " that has a component with id " + componentId + ".";
		return errorMsg;
	}

	private static String componentNotAllowedToReload(Long studyId,
			Long componentId) {
		String errorMsg = "It's not allowed to reload this component (id: "
				+ componentId + "). Unfortunately it is neccessary to finish "
				+ "this study (id: " + studyId + ") at this point.";
		return errorMsg;
	}

	private static String componentNeverStarted(Long studyId, Long componentId) {
		String errorMsg = "Component " + componentId + " of study " + studyId
				+ " was never started.";
		return errorMsg;
	}

	private static String componentAlreadyFinishedOrFailed(Long studyId,
			Long componentId) {
		String errorMsg = "Component " + componentId + " of study " + studyId
				+ " is already finished or failed.";
		return errorMsg;
	}

	private static String workerNotAllowedStudy(Long workerId, Long studyId) {
		String errorMsg = "Worker " + workerId + " is not allowed to do "
				+ "study " + studyId + ".";
		return errorMsg;
	}

	private static String submittedDataUnknownFormat(Long studyId,
			Long componentId) {
		String errorMsg = "Unknown format of submitted data for component + "
				+ componentId + "of study " + studyId + ".";
		return errorMsg;
	}

}
