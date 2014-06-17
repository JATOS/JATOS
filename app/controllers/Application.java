package controllers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import models.MAExperiment;
import models.MAResult;
import play.api.Play;
import play.db.jpa.Transactional;
import play.mvc.Content;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.error;
import views.html.index;

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
	public static Result experiment(Long id) throws Exception {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequest(error.render("An experiment with id " + id
					+ " doesn't exist."));
		}

		// Check if it's a browser reload of the same user of the same
		// experiment
		boolean result = setExperimentDone(id);
		if (!result) {
			return badRequest(error.render("It is not allowed to reload an "
					+ "experiment you've started already."));
		}

		// Serialize MAExperiment into JSON (only the public part)
		ObjectWriter objectWriter = new ObjectMapper()
				.writerWithView(MAExperiment.Public.class);
		String experimentJson = objectWriter.writeValueAsString(experiment);

		// Dynamically load view specified in the 'view' field of MAExperiment 
		Content content = getViewContent(id, experiment, experimentJson);
		return ok(content);
	}

	private static Content getViewContent(Long id, MAExperiment experiment,
			String experimentJson) throws ClassNotFoundException,
			NoSuchMethodException, IllegalAccessException,
			InvocationTargetException {
		Content content;
		String viewClazzName = "views.html." + experiment.view;
		Class<?> viewClazz = Play.current().classloader()
				.loadClass(viewClazzName);
		Method render = viewClazz.getDeclaredMethod("render", String.class,
				String.class, String.class);
		content = (play.api.templates.Html) render.invoke(viewClazz,
				String.valueOf(id), experimentJson, experiment.data);
		return content;
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
