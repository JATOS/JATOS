package controllers;

import java.io.StringWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import models.MAComponent;
import models.MAExperiment;
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
	public static Result startExperiment(Long experimentId, String workerId,
			String assignmentId, String hitId) {
		Logger.info("startExperiment: experimentId " + experimentId + ", "
				+ "workerId " + workerId, "assignmentId " + assignmentId + ", "
				+ "hitId " + hitId);
		checkForMTurkSandbox();

		MAExperiment experiment = MAExperiment.findById(experimentId);
		if (experiment == null) {
			String errorMsg = experimentNotExist(experimentId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		MAComponent component = experiment.getFirstComponent();
		if (component == null) {
			String errorMsg = experimentHasNoComponents(experimentId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		// Check for admin
		if (adminLoggedIn(experiment)) {
			return redirect(component.getViewUrl());
		}

		// Check Mechanical Turk assignment id
		if (assignmentId == null) {
			String errorMsg = assignmentIdNotSpecified();
			return badRequest(views.html.publix.error.render(errorMsg));
		}
		if (assignmentId.equals(ASSIGNMENT_ID_NOT_AVAILABLE)) {
			// It's a preview coming from Mechanical Turk
			String errorMsg = noPreviewAvailable(experimentId);
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
		} else if (worker.finishedExperiment(experimentId)
				&& !isRequestFromMTurkSandbox()) {
			String errorMsg = workerNotAllowedExperiment(workerId, experimentId);
			return forbidden(views.html.publix.error.render(errorMsg));
		}
		session(WORKER_ID, workerId);

		// Start first component
		boolean alreadyStarted = startComponent(component, worker);
		if (alreadyStarted) {
			String errorMsg = componentAlreadyStarted(component.getId());
			return forbidden(views.html.publix.error.render(errorMsg));
		}

		return redirect(component.getViewUrl());
	}

	/**
	 * HTTP type: Ajax POST request
	 */
	@Transactional
	public static Result startComponent(Long experimentId, Long componentId) {
		Logger.info("startComponent: experimentId " + experimentId + ", "
				+ "workerId " + session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(experiment, component, experimentId,
				componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		// Check for admin
		if (adminLoggedIn(experiment)) {
			return ok();
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MAWorker worker = MAWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, experimentId);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// Start component
		boolean alreadyStarted = startComponent(component, worker);
		if (alreadyStarted && !component.isReloadable()) {
			// If someone tries to reload a not reloadable component end the
			// experiment
			endExperiment(worker, experiment, false);
			return forbidden(reloadNotAllowed(experimentId, componentId));
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
	public static Result getComponentData(Long experimentId, Long componentId)
			throws Exception {
		Logger.info("getComponentData: experimentId " + experimentId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(experiment, component, experimentId,
				componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		// Check for admin: if yes, just return JSON data
		if (adminLoggedIn(experiment)) {
			return ok(MAComponent.asJsonForPublic(component));
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MAWorker worker = MAWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, experimentId);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// If component not already started do so
		startComponent(component, worker);

		// Put result into DATA state
		MAResult result = worker.getCurrentResult(component);
		if (result.state != State.STARTED && !component.isReloadable()) {
			// If someone tries to reload a not reloadable component end the
			// experiment
			endExperiment(worker, experiment, false);
			return forbidden(reloadNotAllowed(experimentId, componentId));
		}
		result.state = State.DATA;
		result.merge();

		// return component as JSON
		return ok(MAComponent.asJsonForPublic(component));
	}

	/**
	 * HTTP type: Ajax POST request
	 */
	@Transactional
	public static Result submitResult(Long experimentId, Long componentId) {
		Logger.info("submitResult: experimentId " + experimentId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(experiment, component, experimentId,
				componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		// Check for admin: if yes, don't persist result and return
		if (adminLoggedIn(experiment)) {
			return okNextComponentUrl(experiment, component);
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MAWorker worker = MAWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, experimentId);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// Get result in format JSON, text or XML and convert to String
		String resultStr = getResultAsString();
		if (resultStr == null) {
			return badRequest(submittedResultUnknownFormat(experimentId,
					componentId));
		}

		// End component
		MAResult result = worker.getCurrentResult(component);
		if (result == null || result.state == State.DONE) {
			// If component was never started (result==null) or it's already
			// finished (state==DONE) return a HTTP 403
			return forbidden(workerNotAllowedComponent(workerId, experimentId,
					componentId));
		}
		endComponent(result, resultStr, component, worker);

		// Conveniently send the URL of the next component (or end page)
		return okNextComponentUrl(experiment, component);
	}

	private static String getResultAsString() {
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
	private static void endComponent(MAResult result, String resultStr,
			MAComponent component, MAWorker worker) {
		result.data = resultStr;
		result.state = State.DONE;
		result.merge();
		worker.removeCurrentComponent(component);
		worker.merge();
	}

	/**
	 * HTTP type: Ajax POST request
	 */
	@Transactional
	public static Result endComponent(Long experimentId, Long componentId) {
		Logger.info("endComponent: experimentId " + experimentId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(experiment, component, experimentId,
				componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		// Check for admin
		if (adminLoggedIn(experiment)) {
			return ok();
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MAWorker worker = MAWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, experimentId);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// End component
		MAResult result = worker.getCurrentResult(component);
		if (result == null || result.state == State.DONE) {
			// If component was never started (result==null) or it's already
			// finished (state==DONE) return a HTTP 403
			return forbidden(workerNotAllowedComponent(workerId, experimentId,
					componentId));
		}
		endComponent(result, null, component, worker);

		return ok();
	}

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result endExperiment(Long experimentId) {
		Logger.info("endExperiment: experimentId " + experimentId + ", "
				+ "workerId " + session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		if (experiment == null) {
			return badRequest(views.html.publix.error
					.render(experimentNotExist(experimentId)));
		}

		// Check for admin
		if (adminLoggedIn(experiment)) {
			boolean admin = true;
			return ok(views.html.publix.end.render(experimentId, null, true,
					admin));
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(views.html.publix.error
					.render(experimentNeverStarted(experimentId)));
		}
		MAWorker worker = MAWorker.findById(workerId);
		if (worker == null) {
			return forbidden(views.html.publix.error
					.render(workerNotExist(workerId)));
		}

		// Get confirmation code
		boolean successful = true;
		String confirmationCode = endExperiment(worker, experiment, successful);

		boolean admin = false;
		return ok(views.html.publix.end.render(experimentId, confirmationCode,
				successful, admin));
	}

	private static String endExperiment(MAWorker worker,
			MAExperiment experiment, boolean successful) {
		String confirmationCode;
		if (worker.finishedExperiment(experiment.getId())) {
			confirmationCode = worker.getConfirmationCode(experiment.getId());
		} else {
			confirmationCode = worker.finishExperiment(experiment.getId(),
					successful);
		}
		worker.removeCurrentComponentsForExperiment(experiment);
		worker.merge();
		return confirmationCode;
	}

	/**
	 * HTTP type: Ajax GET request
	 */
	@Transactional
	public static Result getNextComponentUrl(Long experimentId, Long componentId) {
		Logger.info("getNextComponentUrl: experimentId " + experimentId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(experiment, component, experimentId,
				componentId);
		if (errorMsg != null) {
			return badRequest(errorMsg);
		}

		return okNextComponentUrl(experiment, component);
	}

	/**
	 * Returns OK with the view URL of the next component or OK with the URL to
	 * endExperiment() if the current component is the last one of the
	 * experiment.
	 */
	private static Result okNextComponentUrl(MAExperiment experiment,
			MAComponent component) {
		MAComponent nextComponent = experiment.getNextComponent(component);
		if (nextComponent == null) {
			return ok(routes.Publix.endExperiment(experiment.getId()).url());
		}
		return ok(nextComponent.getViewUrl());
	}

	/**
	 * HTTP type: Ajax GET request
	 */
	@Transactional
	public static Result getComponentUrl(Long experimentId, Long componentId) {
		Logger.info("getComponentUrl: experimentId " + experimentId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		String errorMsg = checkStandard(experiment, component, experimentId,
				componentId);
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

	private static String checkStandard(MAExperiment experiment,
			MAComponent component, Long experimentId, Long componentId) {
		if (experiment == null) {
			return experimentNotExist(experimentId);
		}
		if (component == null) {
			return componentNotExist(componentId);
		}
		if (!component.getExperiment().getId().equals(experimentId)) {
			return componentNotBelongToExperiment(experimentId, componentId);
		}
		return null;
	}

	private static String checkWorker(String workerId, MAWorker worker,
			Long experimentId) {
		if (worker == null) {
			return workerNotExist(workerId);
		}
		if (worker.finishedExperiment(experimentId)
				&& !isRequestFromMTurkSandbox()) {
			return workerFinishedExperimentAlready(workerId, experimentId);
		}
		return null;
	}

	/**
	 * Returns true if an admin of this experiment is logged in and false
	 * otherwise.
	 */
	private static boolean adminLoggedIn(MAExperiment experiment) {
		String email = session(MAController.COOKIE_EMAIL);
		if (email != null) {
			MAUser user = MAUser.findByEmail(email);
			if (user != null && experiment.hasMember(user)) {
				return true;
			}
		}
		return false;
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

	private static String noPreviewAvailable(Long experimentId) {
		String errorMsg = "No preview available for experiment " + experimentId
				+ ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String experimentNeverStarted(Long experimentId) {
		String errorMsg = "Experiment " + experimentId + " was never started.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String experimentNotExist(Long experimentId) {
		String errorMsg = "An experiment with id " + experimentId
				+ " doesn't exist.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String experimentHasNoComponents(Long experimentId) {
		String errorMsg = "The experiment with id " + experimentId
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

	private static String componentNotBelongToExperiment(Long experimentId,
			Long componentId) {
		String errorMsg = "There is no experiment with id " + experimentId
				+ " that has a component with id " + componentId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String componentAlreadyStarted(Long componentId) {
		String errorMsg = "Component " + componentId + " was already started.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerNotAllowedExperiment(String workerId,
			Long experimentId) {
		String errorMsg = "Worker " + workerId + " is not allowed to do "
				+ "experiment " + experimentId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerFinishedExperimentAlready(String workerId,
			Long experimentId) {
		String errorMsg = "Worker " + workerId + " finished " + "experiment "
				+ experimentId + " already.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerNotAllowedComponent(String workerId,
			Long experimentId, Long componentId) {
		String errorMsg = "Worker " + workerId + " is not allowed to do "
				+ "component " + componentId + " of " + "experiment "
				+ experimentId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String reloadNotAllowed(Long experimentId, Long componentId) {
		String errorMsg = "It is not allowed to reload " + "component "
				+ componentId + " of " + "experiment " + experimentId
				+ ". The experiment is finished.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String submittedResultUnknownFormat(Long experimentId,
			Long componentId) {
		String errorMsg = "Unknown format of submitted result for component + "
				+ componentId + "of experiment " + experimentId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

}
