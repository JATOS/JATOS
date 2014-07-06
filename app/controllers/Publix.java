package controllers;

import models.MAComponent;
import models.MAExperiment;
import models.MAResult;
import models.MTWorker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class Publix extends Controller {

	public static final String WORKER_ID = "workerId";
	public static final String COMPONENT_ID = "componentId";
	public static final String COOKIE_COMPONENTS_DONE = "componentsDone";
	public static final String COMPONENTS_DONE_DELIMITER = ",";

	public static Result logError() {
		String msg = request().body().asText();
		Logger.error("Client-side error: " + msg);
		return ok();
	}

	@Transactional
	public static Result startExperiment(Long experimentId, String workerId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MTWorker worker = MTWorker.findById(workerId);
		if (experiment == null) {
			return badRequestComponentNotExist(experimentId);
		}
		if (worker == null) {
			worker = new MTWorker(workerId);
			worker.persist();
		} else if (worker.didFinishExperiment(experimentId)) {
			return badRequest("Worker did this experiment already");
		}

		MAComponent component = experiment.getFirstComponent();
		if (component == null) {
			return badRequestExperimentHasNoComponents(experimentId);
		}
		if (!component.hasWorker(worker)) {
			component.addWorker(worker);
			component.merge();
		} else if (!component.isReloadable()) {
			return badRequest("Worker did this component already");
		}
		
		session(WORKER_ID, workerId);
		return redirect(component.viewUrl);
	}

	@Transactional
	public static Result endExperiment(Long experimentId) {
		String workerId = session(WORKER_ID);
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MTWorker worker = MTWorker.findById(workerId);
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId);
		}
		if (worker == null) {
			return badRequestWorkerNotExist(workerId);
		}
		if (worker.didFinishExperiment(experimentId)) {
			return badRequest("Worker did this experiment already");
		}

		String confirmationCode = worker.finishExperiment(experimentId);
		worker.merge();
		return ok(views.html.publix.confirmationCode.render(confirmationCode));
	}

	@Transactional
	public static Result getComponentData(Long experimentId, Long componentId)
			throws Exception {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(experiment, component, experimentId,
				componentId);
		if (result != null) {
			return result;
		}

		// Check if it's a browser reload of the same user of the same
		// component
		boolean isReload = !setComponentDone(componentId);
		if (isReload) {
			return badRequest(views.html.publix.error
					.render("It is not allowed to reload an "
							+ "component you've started already."));
		}

		// Serialize MAComponent into JSON (only the public part)
		ObjectWriter objectWriter = new ObjectMapper()
				.writerWithView(MAComponent.Public.class);
		String componentAsJson = objectWriter.writeValueAsString(component);
		return ok(componentAsJson);
	}

	private static boolean setComponentDone(Long componentId) {
		// If an admin is logged in do nothing
		if (session(MAController.COOKIE_EMAIL) != null) {
			return true;
		}

		String componentsDone = session(COOKIE_COMPONENTS_DONE);
		if (componentsDone == null) {
			session(COOKIE_COMPONENTS_DONE, componentId.toString());
			return true;
		}

		// If there are several component ids stored in the cookie check them
		// one by one.
		String[] componentsDoneArray = componentsDone
				.split(COMPONENTS_DONE_DELIMITER);
		for (String componentDone : componentsDoneArray) {
			if (componentDone.equals(componentId.toString())) {
				return false;
			}
		}
		session(COOKIE_COMPONENTS_DONE, componentsDone
				+ COMPONENTS_DONE_DELIMITER + componentId);
		return true;
	}

	@Transactional
	public static Result submitResult(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(experiment, component, experimentId,
				componentId);
		if (result != null) {
			return result;
		}

		JsonNode resultJson = request().body().asJson();
		if (resultJson == null) {
			return badRequest(views.html.publix.error
					.render("Expecting data in JSON format"));
		}

		String resultStr = resultJson.toString();
		MAResult maResult = new MAResult(resultStr, component);
		addResult(component, maResult);

		// Conveniently send the URL of the next component (or end page)
		return okNextComponentUrl(experiment, component);
	}

	@Transactional
	public static Result getNextComponentUrl(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(experiment, component, experimentId,
				componentId);
		if (result != null) {
			return result;
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
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(experiment, component, experimentId,
				componentId);
		if (result != null) {
			return result;
		}

		return ok(component.viewUrl);
	}
	
	private static void addResult(MAComponent component, MAResult result) {
		result.persist();
		component.addResult(result);
		component.merge();
	}

	private static Result checkStandard(MAExperiment experiment,
			MAComponent component, Long experimentId, Long componentId) {
		if (experiment == null) {
			return badRequest("The experiment doesn't exist.");
		}
		if (component == null) {
			return badRequestComponentNotExist(componentId);
		}
		if (component.experiment.id != experimentId) {
			return badRequestComponentNotBelongToExperiment(experimentId,
					componentId);
		}
		return null;
	}
	
	private static Result badRequestWorkerNotExist(String workerId) {
		String errorMsg = "A worker with id " + workerId + " doesn't exist.";
		return badRequest(views.html.publix.error.render(errorMsg));
	}

	private static Result badRequestExperimentNotExist(Long experimentId) {
		String errorMsg = "An experiment with id " + experimentId
				+ " doesn't exist.";
		return badRequest(views.html.publix.error.render(errorMsg));
	}

	private static Result badRequestExperimentHasNoComponents(Long experimentId) {
		String errorMsg = "The experiment with id " + experimentId
				+ " has no components.";
		return badRequest(views.html.publix.error.render(errorMsg));
	}

	private static Result badRequestComponentNotExist(Long componentId) {
		String errorMsg = "An component with id " + componentId
				+ " doesn't exist.";
		return badRequest(views.html.publix.error.render(errorMsg));
	}

	private static Result badRequestComponentNotBelongToExperiment(
			Long experimentId, Long componentId) {
		String errorMsg = "There is no experiment with id " + experimentId
				+ " that has a component with id " + componentId + ".";
		return badRequest(views.html.publix.error.render(errorMsg));
	}

}
