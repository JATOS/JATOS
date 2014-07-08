package controllers;

import models.MAComponent;
import models.MAExperiment;
import models.MAResult;
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

	@Transactional
	public static Result startExperiment(Long experimentId, String workerId) {
		Logger.info("startExperiment: experimentId " + experimentId + ", "
				+ "workerId " + workerId);
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MTWorker worker = MTWorker.findById(workerId);
		if (experiment == null) {
			String errorMsg = experimentNotExist(experimentId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}
		if (worker == null) {
			worker = new MTWorker(workerId);
			worker.persist();
		} else if (worker.didFinishExperiment(experimentId)) {
			String errorMsg = workerNotAllowedExperiment(workerId, experimentId);
			return forbidden(views.html.publix.error.render(errorMsg));
		}

		MAComponent component = experiment.getFirstComponent();
		if (component == null) {
			String errorMsg = experimentHasNoComponents(experimentId);
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		session(WORKER_ID, workerId);
		return redirect(component.viewUrl);
	}

	@Transactional
	public static Result endExperiment(Long experimentId) {
		Logger.info("endExperiment: experimentId " + experimentId + ", "
				+ "workerId " + session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		if (experiment == null) {
			return badRequest(views.html.publix.error
					.render(experimentNotExist(experimentId)));
		}
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(views.html.publix.error
					.render(workerNeverStartedExperiment(experimentId)));
		}
		MTWorker worker = MTWorker.findById(workerId);
		if (worker == null) {
			return forbidden(views.html.publix.error
					.render(workerNotExist(workerId)));
		}
		if (worker.didFinishExperiment(experimentId)) {
			String errorMsg = workerNotAllowedExperiment(workerId, experimentId);
			return forbidden(views.html.publix.error.render(errorMsg));
		}

		String confirmationCode = worker.finishExperiment(experimentId);
		worker.merge();
		return ok(views.html.publix.confirmationCode.render(confirmationCode));
	}

	@Transactional
	public static Result setComponentDone(Long experimentId, Long componentId) {
		Logger.info("setComponentDone: experimentId " + experimentId + ", "
				+ "componentId " + componentId + ", "
				+ "workerId " + session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		String result = checkStandard(experiment, component, experimentId,
				componentId);
		if (result != null) {
			return badRequest(result);
		}

		// If an admin is logged in just return ok and do nothing.
		if (isAdmin(experiment)) {
			return ok();
		}

		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MTWorker worker = MTWorker.findById(workerId);
		if (worker == null) {
			return forbidden(workerNotExist(workerId));
		}

		// Check if this worker did this component already
		if (component.hasWorker(worker)) {
			// Check if the component can be done several times
			if (!component.isReloadable()) {
				return forbidden(workerNotAllowedComponent(workerId,
						experimentId, componentId));
			}
		} else {
			component.addWorker(worker);
			component.merge();
		}

		return ok();
	}

	@Transactional
	public static Result getComponentData(Long experimentId, Long componentId)
			throws Exception {
		Logger.info("getComponentData: experimentId " + experimentId + ", "
				+ "componentId " + componentId + ", "
				+ "workerId " + session(WORKER_ID));
		Status result = (Status) setComponentDone(experimentId, componentId);
		if (result.getWrappedSimpleResult().header().status() == OK) {
			MAComponent component = MAComponent.findById(componentId);
			return ok(MAComponent.asJsonForPublic(component));
		} else {
			return result;
		}
	}

	@Transactional
	public static Result submitResult(Long experimentId, Long componentId) {
		Logger.info("submitResult: experimentId " + experimentId + ", "
				+ "componentId " + componentId + ", "
				+ "workerId " + session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		String result = checkStandard(experiment, component, experimentId,
				componentId);
		if (result != null) {
			return badRequest(result);
		}
		
		String workerId = session(WORKER_ID);
		if (workerId == null) {
			return forbidden(workerNotExist(workerId));
		}
		MTWorker worker = MTWorker.findById(workerId);
		if (worker == null) {
			return forbidden(workerNotExist(workerId));
		}

		JsonNode resultJson = request().body().asJson();
		if (resultJson == null) {
			return badRequest(submitResultNotJson(experimentId, componentId));
		}

		String resultStr = resultJson.toString();
		MAResult maResult = new MAResult(resultStr, component);
		addResult(component, maResult);

		// Conveniently send the URL of the next component (or end page)
		return okNextComponentUrl(experiment, component);
	}

	@Transactional
	public static Result getNextComponentUrl(Long experimentId, Long componentId) {
		Logger.info("getNextComponentUrl: experimentId " + experimentId + ", "
				+ "componentId " + componentId + ", "
				+ "workerId " + session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		
		
		String result = checkStandard(experiment, component, experimentId,
				componentId);
		if (result != null) {
			return badRequest(result);
		}

		return okNextComponentUrl(experiment, component);
	}

	private static Result okNextComponentUrl(MAExperiment experiment,
			MAComponent component) {
		MAComponent nextComponent = experiment.getNextComponent(component);
		if (nextComponent == null) {
			return ok(routes.Publix.endExperiment(experiment.id).url());
		}
		return ok(nextComponent.viewUrl);
	}

	@Transactional
	public static Result getComponentUrl(Long experimentId, Long componentId) {
		Logger.info("getComponentUrl: experimentId " + experimentId + ", "
				+ "componentId " + componentId + ", "
				+ "workerId " + session(WORKER_ID));
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		String result = checkStandard(experiment, component, experimentId,
				componentId);
		if (result != null) {
			return badRequest(result);
		}

		return ok(component.viewUrl);
	}

	public static Result logError() {
		String msg = request().body().asText();
		Logger.error("Client-side error: " + msg);
		return ok();
	}

	private static void addResult(MAComponent component, MAResult result) {
		result.persist();
		component.addResult(result);
		component.merge();
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
	
	/**
	 * Returns true if an admin of this experiment is logged in and false
	 * otherwise.
	 */
	private static boolean isAdmin(MAExperiment experiment) {
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
			errorMsg = "No worker id found";
		} else {
			errorMsg = "A worker with id " + workerId + " doesn't exist.";
		}
		Logger.info(errorMsg);
		return errorMsg;
	}

	private static String workerNeverStartedExperiment(Long experimentId) {
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

	private static String workerNotAllowedExperiment(String workerId,
			Long experimentId) {
		String errorMsg = "Worker " + workerId + " is not allowed to do "
				+ "experiment " + experimentId + ".";
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
