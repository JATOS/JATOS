package controllers;

import java.util.Random;
import java.util.UUID;

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

	private static final String COOKIE_COMPONENTS_DONE = "componentsDone";
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
			return badRequest("The experiment doesn't exist.");
		}
//		if (worker != null || experiment.hasWorker(worker)) {
//			return badRequest("Worker did this experiment already");
//		}

		if (worker == null) {
			worker = new MTWorker(workerId);
			worker.persist();
		}
		session("workerId", workerId);
//		experiment.addWorker(worker);
		experiment.merge();
		return redirect("/experiments/1/elis_confidence_inst.html");
	}
	
	@Transactional
	public static Result endExperiment(Long experimentId) { 
		String workerId = session("workerId");
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MTWorker worker = MTWorker.findById(workerId);
		if (experiment == null) {
			return badRequest("The experiment doesn't exist.");
		}
		if (worker == null) {
			return badRequest("The worker doesn't exist.");
		}
//		if (!experiment.hasWorker(worker)) {
//			return badRequest("Worker never started this experiment.");
//		}
		
		String confirmationCode = UUID.randomUUID().toString();
//		worker.putConfirmationCode(experiment.id, confirmationCode);
		worker.merge();
		
		return ok(views.html.publix.confirmationCode.render(confirmationCode));
	}

	@Transactional
	public static Result getComponentData(Long experimentId, Long componentId)
			throws Exception {
		MAComponent component = MAComponent.findById(componentId);
		if (component == null) {
			return badRequestComponentNotExist(componentId);
		}
		if (component.experiment.id != experimentId) {
			return badRequestComponentNotBelongToExperiment(experimentId,
					componentId);
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

	private static boolean setComponentDone(Long id) {
		// If an admin is logged in do nothing
		if (session(MAController.COOKIE_EMAIL) != null) {
			return true;
		}

		String componentsDone = session(COOKIE_COMPONENTS_DONE);
		if (componentsDone == null) {
			session(COOKIE_COMPONENTS_DONE, id.toString());
			return true;
		}

		// If there are several component ids stored in the cookie check them
		// one by one.
		String[] componentsDoneArray = componentsDone
				.split(COMPONENTS_DONE_DELIMITER);
		for (String componentDone : componentsDoneArray) {
			if (componentDone.equals(id.toString())) {
				return false;
			}
		}
		session(COOKIE_COMPONENTS_DONE, componentsDone
				+ COMPONENTS_DONE_DELIMITER + id);
		return true;
	}

	@Transactional
	public static Result submitResult(Long experimentId, Long componentId) {
		MAComponent component = MAComponent.findById(componentId);
		if (component == null) {
			return badRequestComponentNotExist(componentId);
		}
		if (component.experiment.id != experimentId) {
			return badRequestComponentNotBelongToExperiment(experimentId,
					componentId);
		}

		JsonNode resultJson = request().body().asJson();
		if (resultJson == null) {
			return badRequest(views.html.publix.error
					.render("Expecting data in JSON format"));
		}

		String resultStr = resultJson.toString();
		MAResult result = new MAResult(resultStr, componentId);

		String errorMsg = result.validate();
		if (errorMsg != null) {
			return badRequest(views.html.publix.error.render(errorMsg));
		}

		result.persist();
		return ok();
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
