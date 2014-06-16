package controllers;

import models.MAExperiment;
import models.MAResult;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.error;
import views.html.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class Application extends Controller {

	private static final String COOKIE_EXPS_DONE = "expsDone";
	public static final String EXPERIMENTS_DONE_DELIMITER = ",";

	public static Result index() {
		return ok(index.render());
	}

	@Transactional
	public static Result experiment(Long id) throws JsonProcessingException {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequest(error.render("An experiment with id " + id
					+ " doesn't exist."));
		}

		boolean result = setExperimentDone(id);
		if (!result) {
			return badRequest(error.render("It is not allowed to reload an "
					+ "experiment you've started already."));
		}

		ObjectWriter objectWriter = new ObjectMapper()
				.writerWithView(MAExperiment.Public.class);
		String experimentJson = objectWriter.writeValueAsString(experiment);
		return ok(views.html.experiment.render(String.valueOf(id),
				experimentJson, experiment.data));
	}

	private static boolean setExperimentDone(Long id) {
		// If an admin is logged in do nothing
		if (session(Admin.COOKIE_EMAIL) != null) {
			return true;
		}
		
		String experimentsDone = session(COOKIE_EXPS_DONE);
		if (experimentsDone == null) {
			session(COOKIE_EXPS_DONE, id.toString());
			return true;
		}

		// If there are several experiment ids stored in the cookie check them
		// one by one.
		String[] experimentsDoneArray = experimentsDone
				.split(EXPERIMENTS_DONE_DELIMITER);
		for (String experimentDone : experimentsDoneArray) {
			if (experimentDone.equals(id.toString())) {
				return false;
			}
		}
		session(COOKIE_EXPS_DONE, experimentsDone + EXPERIMENTS_DONE_DELIMITER
				+ id);
		return true;
	}

	@Transactional
	public static Result submitResult(Long id) {
		JsonNode resultJson = request().body().asJson();
		if (resultJson == null) {
			return badRequest(error.render("Expecting Json data"));
		}

		String resultStr = resultJson.toString();
		MAResult result = new MAResult(resultStr, id);

		String errorMsg = result.validate();
		if (errorMsg != null) {
			return badRequest(error.render(errorMsg));
		} else {
			result.persist();
			return ok();
		}
	}

}
