package controllers;

import java.util.List;

import models.MAComponent;
import models.MAExperiment;
import models.MAUser;
import play.mvc.Controller;
import play.mvc.Result;

public class MAController extends Controller {

	public static final String COOKIE_EMAIL = "email";

	public static String experimentNotExist(Long experimentId) {
		return "An experiment with id " + experimentId + " doesn't exist.";
	}

	public static Result badRequestExperimentNotExist(Long experimentId,
			MAUser user, List<MAExperiment> experimentList) {
		String errorMsg = experimentNotExist(experimentId);
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(experimentList,
				userList, errorMsg, user));
	}

	public static String userNotExist(String email) {
		return "An user with email " + email + " doesn't exist.";
	}

	public static Result badRequestUserNotExist(String email,
			MAUser loggedInUser, List<MAExperiment> experimentList) {
		String errorMsg = userNotExist(email);
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(experimentList,
				userList, errorMsg, loggedInUser));
	}

	public static String componentNotExist(Long componentId) {
		return "An component with id " + componentId + " doesn't exist.";
	}

	public static Result badRequestComponentNotExist(Long componentId,
			MAExperiment experiment, MAUser user,
			List<MAExperiment> experimentList) {
		String errorMsg = componentNotExist(componentId);
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(experimentList,
				userList, errorMsg, user));
	}

	public static String componentNotBelongToExperiment(Long experimentId,
			Long componentId) {
		return "There is no experiment with id " + experimentId
				+ " that has a component with id " + componentId + ".";
	}

	public static Result badRequestComponentNotBelongToExperiment(
			MAExperiment experiment, MAComponent component, MAUser user,
			List<MAExperiment> experimentList) {
		String errorMsg = componentNotBelongToExperiment(experiment.id,
				component.id);
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(experimentList,
				userList, errorMsg, user));
	}

	public static String notMember(String username, String email,
			Long experimentId, String experimentTitle) {
		return username + " (" + email + ") isn't member of experiment "
				+ experimentId + " \"" + experimentTitle + "\".";
	}

	public static Result forbiddenNotMember(MAUser user,
			MAExperiment experiment, List<MAExperiment> experimentList) {
		String errorMsg = notMember(user.name, user.email, experiment.id,
				experiment.title);
		List<MAUser> userList = MAUser.findAll();
		return forbidden(views.html.admin.index.render(experimentList,
				userList, errorMsg, user));
	}

	public static String urlViewEmpty(Long componentId) {
		return "Component " + componentId + "'s URL field is empty.";
	}

	public static Result badRequestUrlViewEmpty(MAUser user,
			MAExperiment experiment, MAComponent component,
			List<MAExperiment> experimentList) {
		String errorMsg = urlViewEmpty(component.id);
		List<MAUser> userList = MAUser.findAll();
		return forbidden(views.html.admin.index.render(experimentList,
				userList, errorMsg, user));
	}

}
