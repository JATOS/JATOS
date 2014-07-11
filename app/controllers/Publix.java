package controllers;

import models.MAComponent;
import models.MAExperiment;
import models.MAResult;
import models.MAResult.State;
import models.MAUser;
import models.MTWorker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;

public class Publix extends Controller {

	public static final String WORKER_ID = "workerId";
	public static final String COMPONENT_ID = "componentId";

	/**
	 * HTTP type: Normal GET request
	 */
	@Transactional
	public static Result startExperiment(Long experimentId, String workerId) {
		Logger.info("startExperiment: experimentId " + experimentId + ", "
				+ "workerId " + workerId);
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
		if (callerIsAdmin(experiment)) {
			return redirect(component.viewUrl);
		}

		// Check worker
		if (workerId == null) {
			badRequest(workerNotExist(workerId));
		}
		MTWorker worker = MTWorker.findById(workerId);
		if (worker == null) {
			worker = new MTWorker(workerId);
			worker.persist();
		} else if (worker.finishedExperiment(experimentId)) {
			String errorMsg = workerNotAllowedExperiment(workerId, experimentId);
			return forbidden(views.html.publix.error.render(errorMsg));
		}
		session(WORKER_ID, workerId);

		// Start first component
		boolean alreadyStarted = startComponentAndCreateResult(component,
				worker);
		if (alreadyStarted) {
			String errorMsg = componentAlreadyStarted(component.id);
			return forbidden(views.html.publix.error.render(errorMsg));
		}

		return redirect(component.viewUrl);
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
		if (callerIsAdmin(experiment)) {
			return ok();
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MTWorker worker = MTWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, experimentId);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// Start component
		boolean alreadyStarted = startComponentAndCreateResult(component,
				worker);
		if (alreadyStarted && !component.isReloadable()) {
			return forbidden(componentAlreadyStarted(componentId));
		}

		return ok();
	}

	private static boolean startComponentAndCreateResult(MAComponent component,
			MTWorker worker) {
		boolean alreadyStarted = worker.hasCurrentComponent(component);
		if (!alreadyStarted) {
			createResult(component, worker);
		}
		return alreadyStarted;
	}

	/**
	 * Create/persist result and update/persist component and worker.
	 */
	private static void createResult(MAComponent component, MTWorker worker) {
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
		if (callerIsAdmin(experiment)) {
			return ok(MAComponent.asJsonForPublic(component));
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MTWorker worker = MTWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, experimentId);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// If component not already started do so
		startComponentAndCreateResult(component, worker);

		// Put result into DATA state
		MAResult result = worker.getCurrentResult(component);
		if (result.state != State.STARTED && !component.isReloadable()) {
			return forbidden(workerNotAllowedComponent(workerId, experimentId,
					componentId));
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
		if (callerIsAdmin(experiment)) {
			return okNextComponentUrl(experiment, component);
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MTWorker worker = MTWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, experimentId);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// Get result as JSON string
		JsonNode resultJson = request().body().asJson();
		if (resultJson == null) {
			return badRequest(submitResultNotJson(experimentId, componentId));
		}
		String resultStr = resultJson.toString();

		// End component
		MAResult result = worker.getCurrentResult(component);
		if (result == null
				|| (result.state == State.DONE && !component.isReloadable())) {
			return forbidden(workerNotAllowedComponent(workerId, experimentId,
					componentId));
		}
		endComponent(result, resultStr, component, worker);

		// Conveniently send the URL of the next component (or end page)
		return okNextComponentUrl(experiment, component);
	}

	/**
	 * Put result into state DONE, persist and remove from worker
	 */
	private static void endComponent(MAResult result, String resultStr,
			MAComponent component, MTWorker worker) {
		result.result = resultStr;
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
		if (callerIsAdmin(experiment)) {
			return ok();
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MTWorker worker = MTWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, experimentId);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		// End component
		MAResult result = worker.getCurrentResult(component);
		if (result == null
				|| (result.state == State.DONE && !component.isReloadable())) {
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
		if (callerIsAdmin(experiment)) {
			return ok(views.html.publix.confirmationCode
					.render("none (admin logged in)"));
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(views.html.publix.error
					.render(experimentNeverStarted(experimentId)));
		}
		MTWorker worker = MTWorker.findById(workerId);
		if (worker == null) {
			return forbidden(views.html.publix.error
					.render(workerNotExist(workerId)));
		}

		// Get confirmation code
		String confirmationCode;
		if (worker.finishedExperiment(experimentId)) {
			confirmationCode = worker.getConfirmationCode(experimentId);
		} else {
			confirmationCode = worker.finishExperiment(experimentId);
			worker.removeCurrentComponentsForExperiment(experiment);
			worker.merge();
		}

		return ok(views.html.publix.confirmationCode.render(confirmationCode));
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

		// Check for admin
		if (callerIsAdmin(experiment)) {
			return okNextComponentUrl(experiment, component);
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MTWorker worker = MTWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, experimentId);
		if (errorMsg != null) {
			return forbidden(errorMsg);
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
			return ok(routes.Publix.endExperiment(experiment.id).url());
		}
		return ok(nextComponent.viewUrl);
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

		// Check for admin
		if (callerIsAdmin(experiment)) {
			return ok(component.viewUrl);
		}

		// Check worker
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MTWorker worker = MTWorker.findById(workerId);
		errorMsg = checkWorker(workerId, worker, experimentId);
		if (errorMsg != null) {
			return forbidden(errorMsg);
		}

		return ok(component.viewUrl);
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

	private static String checkStandard(MAExperiment experiment,
			MAComponent component, Long experimentId, Long componentId) {
		if (experiment == null) {
			return experimentNotExist(experimentId);
		}
		if (component == null) {
			return componentNotExist(componentId);
		}
		if (component.experiment.id != experimentId) {
			return componentNotBelongToExperiment(experimentId, componentId);
		}
		return null;
	}

	private static String checkWorker(String workerId, MTWorker worker,
			Long experimentId) {
		if (worker == null) {
			return workerNotExist(workerId);
		}
		if (worker.finishedExperiment(experimentId)) {
			return workerFinishedExperimentAlready(workerId, experimentId);
		}
		return null;
	}

	/**
	 * Returns true if an admin of this experiment is logged in and false
	 * otherwise.
	 */
	private static boolean callerIsAdmin(MAExperiment experiment) {
		String email = session(MAController.COOKIE_EMAIL);
		if (email != null) {
			MAUser user = MAUser.findByEmail(email);
			if (user != null && experiment.hasMember(user)) {
				return true;
			}
		}
		return false;
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

	private static String submitResultNotJson(Long experimentId,
			Long componentId) {
		String errorMsg = "Submit result for component + " + componentId
				+ "of experiment " + experimentId + ": "
				+ "Expecting data in JSON format";
		Logger.info(errorMsg);
		return errorMsg;
	}

}
