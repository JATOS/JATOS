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

		ObjectWriter objectWriter = new ObjectMapper()
				.writerWithView(MAExperiment.Public.class);
		String experimentJson = objectWriter.writeValueAsString(experiment);
		return ok(views.html.experiment.render(String.valueOf(id),
				experimentJson, experiment.data));
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
