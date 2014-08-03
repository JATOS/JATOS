package controllers;

import java.io.StringWriter;

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

public class Publix extends Controller {

	public static final String WORKER_ID = "workerId";
	public static final String COMPONENT_ID = "componentId";
	public static final String ASSIGNMENT_ID_NOT_AVAILABLE = "ASSIGNMENT_ID_NOT_AVAILABLE";

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result startStudy(Long studyId, String mtWorkerId,
			String mtAssignmentId, String mtHitId) {
		Logger.info("startStudy: studyId " + studyId + ", "
				+ "Parameters from MTurk: workerId " + mtWorkerId + ", "
				+ "assignmentId " + mtAssignmentId + ", " + "hitId " + mtHitId);

		StudyModel study = StudyModel.findById(studyId);
		if (study == null) {
			String errorMsg = studyNotExist(studyId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		// Check MTurk assignment id and if it's a preview
		if (mtAssignmentId == null) {
			String errorMsg = assignmentIdNotSpecified();
			return badRequest(views.html.publix.error.render(errorMsg));
		}
		if (mtAssignmentId.equals(ASSIGNMENT_ID_NOT_AVAILABLE)) {
			// It's a preview coming from Mechanical Turk -> no previews
			String errorMsg = noPreviewAvailable(studyId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		// Check worker
		if (mtWorkerId == null) {
			return badRequest(workerNotExist(mtWorkerId));
		}
		Worker worker = MTWorker.findByMTWorkerId(mtWorkerId);
		if (worker == null) {
			worker = createWorker(mtWorkerId);
		}
		if (!worker.isAllowedToStartStudy(studyId)) {
			String errorMsg = workerNotAllowedStudy(worker.getId(), studyId);
			return forbidden(views.html.publix.error.render(errorMsg));
		}
		session(WORKER_ID, String.valueOf(worker.getId()));

		createStudyResult(study, worker);

		// Start first component
		ComponentModel component = study.getFirstComponent();
		if (component == null) {
			String errorMsg = studyHasNoComponents(studyId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}
		return startComponent(studyId, component.getId());
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

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result startComponent(Long studyId, Long componentId) {
		Logger.info("startComponent: studyId " + studyId + ", " + "workerId "
				+ session(WORKER_ID));

		// Get worker id
		String workerIdStr = session(WORKER_ID);
		if (workerIdStr == null) {
			// No worker id in session -> study never started
			String errorMsg = noWorkerIdInSession();
			return forbidden(views.html.publix.error.render(errorMsg));
		}
		long workerId;
		try {
			workerId = Long.parseLong(workerIdStr);
		} catch (NumberFormatException e) {
			String errorMsg = workerNotExist(workerIdStr);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		// Standard check
		Worker worker = Worker.findById(workerId);
		StudyModel study = StudyModel.findById(studyId);
		ComponentModel component = ComponentModel.findById(componentId);
		String errorMsg = checkStandard(worker, study, component, workerId,
				studyId, componentId);
		if (errorMsg != null) {
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		// Check worker
		StudyResult studyResult = worker.getStartedStudyResult(studyId);
		if (studyResult == null) {
			// Worker never started the study
			errorMsg = workerNeverStartedStudy(workerId, studyId);
			return forbidden(views.html.publix.error.render(errorMsg));
		}
		ComponentResult componentResult = studyResult
				.getComponentResult(componentId);
		if (componentResult != null) {
			// Only one component of the same kind can be done in the same study
			// by the same worker. Exception: If a component is reloadable,
			// the old component result will be deleted and a new one generated.
			component = componentResult.getComponent();
			if (component.isReloadable()) {
				studyResult.removeComponentResult(componentResult);
			} else {
				// Worker tried to reload a non-reloadable component -> end
				// study
				errorMsg = componentNotAllowedToReload(studyId, componentId);
				// End study with fail
				return finishStudy(workerId, false, errorMsg);
			}
		}
		finishAllComponentResults(studyResult);
		createComponentResult(studyResult, component);

		return redirect(component.getViewUrl());
	}

	private static void finishAllComponentResults(StudyResult studyResult) {
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			if (componentResult.getState() != ComponentState.FINISHED
					|| componentResult.getState() != ComponentState.FAIL) {
				componentResult.setState(ComponentState.FINISHED);
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

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result startNextComponent(Long studyId) {
		Logger.info("startNextComponent: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID));

		// Get worker id
		String workerIdStr = session(WORKER_ID);
		if (workerIdStr == null) {
			// No worker id in session -> study never started
			String errorMsg = noWorkerIdInSession();
			return forbidden(views.html.publix.error.render(errorMsg));
		}
		long workerId;
		try {
			workerId = Long.parseLong(workerIdStr);
		} catch (NumberFormatException e) {
			String errorMsg = workerNotExist(workerIdStr);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		// Get worker
		Worker worker = Worker.findById(workerId);
		if (worker == null) {
			String errorMsg = workerNotExist(workerId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		// Get study result
		StudyResult studyResult = worker.getStartedStudyResult(studyId);
		if (studyResult == null) {
			// Worker never started the study
			String errorMsg = workerNeverStartedStudy(workerId, studyId);
			return forbidden(views.html.publix.error.render(errorMsg));
		}

		// Get next component in study
		ComponentModel currentComponent = studyResult.getLastComponentResult()
				.getComponent();
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

		// Get worker id
		String workerIdStr = session(WORKER_ID);
		if (workerIdStr == null) {
			// No worker id in session -> study never started
			String errorMsg = noWorkerIdInSession();
			return forbidden(errorMsg);
		}
		long workerId;
		try {
			workerId = Long.parseLong(workerIdStr);
		} catch (NumberFormatException e) {
			String errorMsg = workerNotExist(workerIdStr);
			return badRequest(errorMsg);
		}

		// Standard check
		Worker worker = Worker.findById(workerId);
		StudyModel study = StudyModel.findById(studyId);
		ComponentModel component = ComponentModel.findById(componentId);
		String errorMsg = checkStandard(worker, study, component, workerId,
				studyId, componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		// Check worker
		StudyResult studyResult = worker.getStartedStudyResult(studyId);
		if (studyResult == null) {
			// Worker never started the study
			errorMsg = workerNeverStartedStudy(workerId, studyId);
			return forbidden(errorMsg);
		}

		// Check component result
		ComponentResult componentResult = studyResult
				.getComponentResult(componentId);
		if (componentResult == null
				|| componentResult.getState() != ComponentState.STARTED) {
			errorMsg = componentNeverStarted(studyId, componentId);
			return forbidden(errorMsg);
		}

		componentResult.setState(ComponentState.DATA_RETRIEVED);
		componentResult.merge();

		return ok(component.asJsonForPublic());
	}

	/**
	 * HTTP type: Ajax POST request
	 */
	@Transactional
	public static Result submitResultData(Long studyId, Long componentId) {
		Logger.info("submitResultData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		// Get worker id
		String workerIdStr = session(WORKER_ID);
		if (workerIdStr == null) {
			// No worker id in session -> study never started
			String errorMsg = noWorkerIdInSession();
			return forbidden(errorMsg);
		}
		long workerId;
		try {
			workerId = Long.parseLong(workerIdStr);
		} catch (NumberFormatException e) {
			String errorMsg = workerNotExist(workerIdStr);
			return badRequest(errorMsg);
		}

		// Standard check
		Worker worker = Worker.findById(workerId);
		StudyModel study = StudyModel.findById(studyId);
		ComponentModel component = ComponentModel.findById(componentId);
		String errorMsg = checkStandard(worker, study, component, workerId,
				studyId, componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		// Check worker
		StudyResult studyResult = worker.getStartedStudyResult(studyId);
		if (studyResult == null) {
			// Worker never started the study
			errorMsg = workerNeverStartedStudy(workerId, studyId);
			return forbidden(errorMsg);
		}

		// Check component result
		ComponentResult componentResult = studyResult
				.getComponentResult(componentId);
		if (componentResult == null
				|| componentResult.getState() == ComponentState.FINISHED
				|| componentResult.getState() == ComponentState.FAIL) {
			errorMsg = componentAlreadyFinishedOrFailed(studyId, componentId);
			return forbidden(errorMsg);
		}

		// Get data in format JSON, text or XML and convert to String
		String data = getDataAsString();
		if (data == null) {
			componentResult.setState(ComponentState.FAIL);
			componentResult.merge();
			return badRequest(submittedDataUnknownFormat(studyId, componentId));
		}

		componentResult.setState(ComponentState.DATA_RETRIEVED);
		componentResult.merge();

		return ok();
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
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result finishStudy(Long studyId) {
		return finishStudy(studyId, true, null);
	}

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result finishStudy(Long studyId, Boolean successful,
			String errorMsg) {
		Logger.info("finishStudy: studyId " + studyId + ", " + "successful "
				+ successful + ", " + "workerId " + session(WORKER_ID));

		// Get worker id
		String workerIdStr = session(WORKER_ID);
		if (workerIdStr == null) {
			// No worker id in session -> study never started
			errorMsg = noWorkerIdInSession();
			return forbidden(views.html.publix.error.render(errorMsg));
		}
		long workerId;
		try {
			workerId = Long.parseLong(workerIdStr);
		} catch (NumberFormatException e) {
			errorMsg = workerNotExist(workerIdStr);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		Worker worker = Worker.findById(workerId);
		if (worker == null) {
			errorMsg = workerNotExist(workerId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		StudyModel study = StudyModel.findById(studyId);
		if (study == null) {
			errorMsg = studyNotExist(studyId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		// Get study result
		StudyResult studyResult = worker.getStartedStudyResult(studyId);
		if (studyResult == null) {
			// Worker never started the study
			errorMsg = workerNeverStartedStudy(workerId, studyId);
			return forbidden(views.html.publix.error.render(errorMsg));
		}

		// Finish study
		finishAllComponentResults(studyResult);
		String confirmationCode;
		if (successful) {
			confirmationCode = studyResult.generateConfirmationCode();
			studyResult.setState(StudyState.FINISHED);
		} else {
			confirmationCode = "fail";
			studyResult.setState(StudyState.FAIL);
		}
		studyResult.merge();
		session().remove(WORKER_ID);

		return ok(views.html.publix.end.render(studyId, confirmationCode,
				successful, errorMsg));
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

	private static String checkStandard(Worker worker, StudyModel study,
			ComponentModel component, Long workerId, Long studyId,
			Long componentId) {
		if (worker == null) {
			return workerNotExist(workerId);
		}
		if (!(worker instanceof MTWorker)) {
			return workerNotFromMTurk(workerId);
		}
		if (study == null) {
			return studyNotExist(studyId);
		}
		if (component == null) {
			return componentNotExist(studyId, componentId);
		}
		if (!component.getStudy().getId().equals(studyId)) {
			return componentNotBelongToStudy(studyId, componentId);
		}
		return null;
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
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerNotExist(Long workerId) {
		return workerNotExist(String.valueOf(workerId));
	}

	private static String workerNotExist(String workerId) {
		String errorMsg = "A worker with id " + workerId + " doesn't exist.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerNotFromMTurk(Long workerId) {
		String errorMsg = "The worker with id " + workerId
				+ " isn't a MTurk worker.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String assignmentIdNotSpecified() {
		String errorMsg = "No assignment id specified in query parameters.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String noPreviewAvailable(Long studyId) {
		String errorMsg = "No preview available for study " + studyId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerNeverStartedStudy(Long workerId, Long studyId) {
		String errorMsg = "Worker " + workerId + " never started study "
				+ studyId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String studyNotExist(Long studyId) {
		String errorMsg = "An study with id " + studyId + " doesn't exist.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String studyHasNoComponents(Long studyId) {
		String errorMsg = "The study with id " + studyId
				+ " has no components.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String componentNotExist(Long studyId, Long componentId) {
		String errorMsg = "An component with id " + componentId + " of study "
				+ studyId + " doesn't exist.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String componentNotBelongToStudy(Long studyId,
			Long componentId) {
		String errorMsg = "There is no study with id " + studyId
				+ " that has a component with id " + componentId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String componentNotAllowedToReload(Long studyId,
			Long componentId) {
		String errorMsg = "It's not allowed to reload this component (id: "
				+ componentId + "). Unfortunately it is neccessary to finish "
				+ "this study (id: " + studyId + ") at this point.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String componentNeverStarted(Long studyId, Long componentId) {
		String errorMsg = "Component " + componentId + " of study " + studyId
				+ " was never started.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String componentAlreadyFinishedOrFailed(Long studyId,
			Long componentId) {
		String errorMsg = "Component " + componentId + " of study " + studyId
				+ " is already finished or failed.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerNotAllowedStudy(Long workerId, Long studyId) {
		String errorMsg = "Worker " + workerId + " is not allowed to do "
				+ "study " + studyId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String submittedDataUnknownFormat(Long studyId,
			Long componentId) {
		String errorMsg = "Unknown format of submitted data for component + "
				+ componentId + "of study " + studyId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

}
