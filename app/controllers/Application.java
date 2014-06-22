package controllers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import models.MAComponent;
import models.MAResult;
import play.Logger;
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
	public static final String COMPONENTS_DONE_DELIMITER = ",";

	public static Result index() {
		return ok(index.render());
	}
	
	public static Result logError() {
		String msg = request().body().asText();
		Logger.error("Client-side error: " + msg);
		return ok();
	}
	
	@Transactional
	public static Result componentData(Long id) throws Exception {
		MAComponent component = MAComponent.findById(id);
		if (component == null) {
			return badRequest(error.render("An component with id " + id
					+ " doesn't exist."));
		}
		
		// Check if it's a browser reload of the same user of the same
		// component
		boolean isReload = !setComponentDone(id);
		if (isReload) {
			return badRequest(error.render("It is not allowed to reload an "
					+ "component you've started already."));
		}
		
		// Serialize MAComponent into JSON (only the public part)
		ObjectWriter objectWriter = new ObjectMapper()
				.writerWithView(MAComponent.Public.class);
		String componentAsJson = objectWriter.writeValueAsString(component);
		return ok(componentAsJson);
	}

	@Transactional
	public static Result component(Long id) throws Exception {
		MAComponent component = MAComponent.findById(id);
		if (component == null) {
			return badRequest(error.render("An component with id " + id
					+ " doesn't exist."));
		}

		// Check if it's a browser reload of the same user of the same
		// component
		boolean result = setComponentDone(id);
		if (!result) {
			return badRequest(error.render("It is not allowed to reload an "
					+ "component you've started already."));
		}

		// Serialize MAComponent into JSON (only the public part)
		ObjectWriter objectWriter = new ObjectMapper()
				.writerWithView(MAComponent.Public.class);
		String componentJson = objectWriter.writeValueAsString(component);

		// Dynamically load view specified in the 'view' field of MAComponent 
		Content content = getViewContent(component, componentJson);
		return ok(content);
	}

	private static Content getViewContent(MAComponent component,
			String componentJson) throws ClassNotFoundException,
			NoSuchMethodException, IllegalAccessException,
			InvocationTargetException {
		Content content;
		String viewClazzName = "views.html." + component.view;
		Class<?> viewClazz = Play.current().classloader()
				.loadClass(viewClazzName);
		Method render = viewClazz.getDeclaredMethod("render", String.class,
				String.class, String.class);
		content = (play.api.templates.Html) render.invoke(viewClazz,
				component.title, componentJson, component.data);
		return content;
	}

	private static boolean setComponentDone(Long id) {
		// If an admin is logged in do nothing
		if (session(Admin.COOKIE_EMAIL) != null) {
			return true;
		}

		String componentsDone = session(COOKIE_EXPS_DONE);
		if (componentsDone == null) {
			session(COOKIE_EXPS_DONE, id.toString());
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
		session(COOKIE_EXPS_DONE, componentsDone + COMPONENTS_DONE_DELIMITER
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
