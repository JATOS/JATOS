package controllers;

import java.io.StringWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import models.MAComponent;
import models.MAStudy;
import models.MAResult;
import models.MAResult.State;
import models.MAUser;
import models.MAWorker;

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
	public static Result startStudy(Long studyId, String workerId,
			String assignmentId, String hitId) {
		Logger.info("startStudy: studyId " + studyId + ", " + "workerId "
				+ workerId, "assignmentId " + assignmentId + ", " + "hitId "
				+ hitId);
		checkForMTurkSandbox();

		MAStudy study = MAStudy.findById(studyId);
		if (study == null) {
			String errorMsg = studyNotExist(studyId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		MAComponent component = study.getFirstComponent();
		if (component == null) {
			String errorMsg = studyHasNoComponents(studyId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		// If we don't come from MTurk but an MAUser is logged in go on anyway
		// and use user's email as workerId.
		MAUser user = maUserLoggedIn(study);
		if (user != null && workerId == null) {
			workerId = user.getEmail();
			assignmentId = "MechArg";
		}

		// Check Mechanical Turk assignment id
		if (assignmentId == null) {
			String errorMsg = assignmentIdNotSpecified();
			return badRequest(views.html.publix.error.render(errorMsg));
		}
		if (assignmentId.equals(ASSIGNMENT_ID_NOT_AVAILABLE)) {
			// It's a preview coming from Mechanical Turk
			String errorMsg = noPreviewAvailable(studyId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		// Check worker
		if (workerId == null) {
			return badRequest(workerNotExist(workerId));
		}
		MAWorker worker = MAWorker.findById(workerId);
		if (worker == null) {
			worker = new MAWorker(workerId);
			worker.persist();
		} else if (worker.finishedStudy(studyId)
				&& !isRequestFromMTurkSandbox() && user == null) {
			String errorMsg = workerNotAllowedStudy(workerId, studyId);
			return forbidden(views.html.publix.error.render(errorMsg));
		}
		session(WORKER_ID, workerId);

		// Start first component
		boolean alreadyStarted = startComponent(component, worker);
		if (alreadyStarted && user == null) {
			String errorMsg = componentAlreadyStarted(component.getId());
			return forbidden(views.html.publix.error.render(errorMsg));
		}

		return redirect(component.getViewUrl());
	}

	/**
	 * HTTP type: Ajax POST request
	 */
	@Transactional
	public static Result startComponent(Long studyId, Long componentId) {
		Logger.info("startComponent: studyId " + studyId + ", " + "workerId "
				+ session(WORKER_ID));
		MAStudy study = MAStudy.findById(studyId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(study, component, studyId, componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		// Check for admin
		// if (maUserLoggedIn(study)) {
		// return ok();
		// }

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MAWorker worker = MAWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, study);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// Start component. If someone tries to reload a not reloadable
		// component end the study, except a MAUser of this study is logged in.
		MAUser user = maUserLoggedIn(study);
		boolean alreadyStarted = startComponent(component, worker);
		if (alreadyStarted && !component.isReloadable() && user == null) {
			endStudy(worker, study, false);
			return forbidden(reloadNotAllowed(studyId, componentId));
		}

		return ok();
	}

	private static boolean startComponent(MAComponent component, MAWorker worker) {
		boolean alreadyStarted = worker.hasCurrentComponent(component);
		if (!alreadyStarted) {
			createResult(component, worker);
		}
		return alreadyStarted;
	}

	/**
	 * Create/persist result and update/persist component and worker.
	 */
	private static void createResult(MAComponent component, MAWorker worker) {
		MAResult result = new MAResult(component, worker);
		result.persist();
		component.addResult(result);
		component.merge();
		worker.addCurrentComponent(component, result);
		worker.addResult(result);
		worker.merge();
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
		MAStudy study = MAStudy.findById(studyId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(study, component, studyId, componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		// Check for admin: if yes, just return JSON data
		// if (maUserLoggedIn(study)) {
		// return ok(MAComponent.asJsonForPublic(component));
		// }

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MAWorker worker = MAWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, study);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// If component not already started do so
		startComponent(component, worker);

		// Put result into DATA state
		MAResult result = worker.getCurrentResult(component);
		if (result.getState() != State.STARTED && !component.isReloadable()) {
			// If someone tries to reload a not reloadable component end the
			// study
			endStudy(worker, study, false);
			return forbidden(reloadNotAllowed(studyId, componentId));
		}
		result.setState(State.DATA);
		result.merge();

		// return component as JSON
		return ok(MAComponent.asJsonForPublic(component));
	}

	/**
	 * HTTP type: Ajax POST request
	 */
	@Transactional
	public static Result submitResult(Long studyId, Long componentId) {
		Logger.info("submitResult: studyId " + studyId + ", " + "componentId "
				+ componentId + ", " + "workerId " + session(WORKER_ID));
		MAStudy study = MAStudy.findById(studyId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(study, component, studyId, componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		// Check for admin: if yes, don't persist result and return
		// if (maUserLoggedIn(study)) {
		// return okNextComponentUrl(study, component);
		// }

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MAWorker worker = MAWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, study);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// Get data in format JSON, text or XML and convert to String
		String data = getDataAsString();
		if (data == null) {
			return badRequest(submittedDataUnknownFormat(studyId, componentId));
		}

		// End component
		MAResult result = worker.getCurrentResult(component);
		if (result == null || result.getState() == State.DONE) {
			// If component was never started (result==null) or it's already
			// finished (state==DONE) return a HTTP 403
			return forbidden(workerNotAllowedComponent(workerId, studyId,
					componentId));
		}
		endComponent(result, data, component, worker);

		// Conveniently send the URL of the next component (or end page)
		return okNextComponentUrl(study, component);
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
	 * Put result into state DONE, persist and remove from worker
	 */
	private static void endComponent(MAResult result, String data,
			MAComponent component, MAWorker worker) {
		result.setData(data);
		result.setState(State.DONE);
		result.merge();
		worker.removeCurrentComponent(component);
		worker.merge();
	}

	/**
	 * HTTP type: Ajax POST request
	 */
	@Transactional
	public static Result endComponent(Long studyId, Long componentId) {
		Logger.info("endComponent: studyId " + studyId + ", " + "componentId "
				+ componentId + ", " + "workerId " + session(WORKER_ID));
		MAStudy study = MAStudy.findById(studyId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(study, component, studyId, componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		// Check for admin
		// if (maUserLoggedIn(study)) {
		// return ok();
		// }

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MAWorker worker = MAWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, study);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// End component
		MAResult result = worker.getCurrentResult(component);
		if (result == null || result.getState() == State.DONE) {
			// If component was never started (result==null) or it's already
			// finished (state==DONE) return a HTTP 403
			return forbidden(workerNotAllowedComponent(workerId, studyId,
					componentId));
		}
		endComponent(result, null, component, worker);

		return ok();
	}

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result endStudy(Long studyId) {
		Logger.info("endStudy: studyId " + studyId + ", " + "workerId "
				+ session(WORKER_ID));
		MAStudy study = MAStudy.findById(studyId);
		if (study == null) {
			return badRequest(views.html.publix.error
					.render(studyNotExist(studyId)));
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(views.html.publix.error
					.render(studyNeverStarted(studyId)));
		}
		MAWorker worker = MAWorker.findById(workerId);
		if (worker == null) {
			return forbidden(views.html.publix.error
					.render(workerNotExist(workerId)));
		}

		// Check if MAUser of this study is logged in
		MAUser user = maUserLoggedIn(study);
		boolean userLoggedIn = (user != null);
		
		// Get confirmation code
		boolean successful = true;
		String confirmationCode = endStudy(worker, study, successful);

		return ok(views.html.publix.end.render(studyId, confirmationCode,
				successful, userLoggedIn));
	}

	private static String endStudy(MAWorker worker, MAStudy study,
			boolean successful) {
		String confirmationCode;
		if (worker.finishedStudy(study.getId())) {
			confirmationCode = worker.getConfirmationCode(study.getId());
		} else {
			confirmationCode = worker.finishStudy(study.getId(), successful);
		}
		worker.removeCurrentComponentsForStudy(study);
		worker.merge();
		return confirmationCode;
	}

	/**
	 * HTTP type: Ajax GET request
	 */
	@Transactional
	public static Result getNextComponentUrl(Long studyId, Long componentId) {
		Logger.info("getNextComponentUrl: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		MAStudy study = MAStudy.findById(studyId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(study, component, studyId, componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		return okNextComponentUrl(study, component);
	}

	/**
	 * Returns OK with the view URL of the next component or OK with the URL to
	 * endStudy() if the current component is the last one of the study.
	 */
	private static Result okNextComponentUrl(MAStudy study,
			MAComponent component) {
		MAComponent nextComponent = study.getNextComponent(component);
		if (nextComponent == null) {
			return ok(routes.Publix.endStudy(study.getId()).url());
		}
		return ok(nextComponent.getViewUrl());
	}

	/**
	 * HTTP type: Ajax GET request
	 */
	@Transactional
	public static Result getComponentUrl(Long studyId, Long componentId) {
		Logger.info("getComponentUrl: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		MAStudy study = MAStudy.findById(studyId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(study, component, studyId, componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		return ok(component.getViewUrl());
	}

	/**
	 * In case the client side wants to log an error. HTTP type: Ajax GET
	 * request
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

	private static String checkStandard(MAStudy study, MAComponent component,
			Long studyId, Long componentId) {
		if (study == null) {
			return studyNotExist(studyId);
		}
		if (component == null) {
			return componentNotExist(componentId);
		}
		if (!component.getStudy().getId().equals(studyId)) {
			return componentNotBelongToStudy(studyId, componentId);
		}
		return null;
	}

	/**
	 * Checks worker: A worker isn't allowed to redo an already finished study.
	 * Exceptions: worker comes from MTurk sandbox or worker is an MAUser of
	 * this study. In the parameters the workerId is needed additionally to the
	 * worker because the worker might be null (e.g. not in the DB).
	 */
	private static String checkWorker(String workerId, MAWorker worker,
			MAStudy study) {
		if (worker == null) {
			return workerNotExist(workerId);
		}

		MAUser user = maUserLoggedIn(study);
		if (worker.finishedStudy(study.getId()) && !isRequestFromMTurkSandbox()
				&& user == null) {
			return workerFinishedStudyAlready(workerId, study.getId());
		}
		return null;
	}

	/**
	 * Returns true if an admin of this study is logged in and false otherwise.
	 */
	private static MAUser maUserLoggedIn(MAStudy study) {
		String email = session(MAController.COOKIE_EMAIL);
		if (email != null) {
			MAUser user = MAUser.findByEmail(email);
			if (user != null && study.hasMember(user)) {
				return user;
			}
		}
		return null;
	}

	/**
	 * Checks if the request comes from Mechanical Turk sandbox and if yes sets
	 * a session variable.
	 */
	private static void checkForMTurkSandbox() {
		String turkSubmitTo = request().getQueryString("turkSubmitTo");
		if (turkSubmitTo != null && turkSubmitTo.contains("sandbox")) {
			session("mturk", "sandbox");
		}
	}

	/**
	 * Returns true if the original request comes from the Mechanical Turk
	 * sandbox and false otherwise.
	 */
	private static boolean isRequestFromMTurkSandbox() {
		return session("mturk").equals("sandbox");
	}

	private static String workerNotExist(String workerId) {
		String errorMsg;
		if (workerId == null) {
			errorMsg = "No worker id specified.";
		} else {
			errorMsg = "A worker with id " + workerId + " doesn't exist.";
		}
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

	private static String studyNeverStarted(Long studyId) {
		String errorMsg = "Study " + studyId + " was never started.";
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

	private static String componentNotExist(Long componentId) {
		String errorMsg = "An component with id " + componentId
				+ " doesn't exist.";
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

	private static String componentAlreadyStarted(Long componentId) {
		String errorMsg = "Component " + componentId + " was already started.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerNotAllowedStudy(String workerId, Long studyId) {
		String errorMsg = "Worker " + workerId + " is not allowed to do "
				+ "study " + studyId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerFinishedStudyAlready(String workerId,
			Long studyId) {
		String errorMsg = "Worker " + workerId + " finished " + "study "
				+ studyId + " already.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerNotAllowedComponent(String workerId,
			Long studyId, Long componentId) {
		String errorMsg = "Worker " + workerId + " is not allowed to do "
				+ "component " + componentId + " of " + "study " + studyId
				+ ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String reloadNotAllowed(Long studyId, Long componentId) {
		String errorMsg = "It is not allowed to reload " + "component "
				+ componentId + " of " + "study " + studyId
				+ ". The study is finished.";
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
