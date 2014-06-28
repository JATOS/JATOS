package controllers;

import java.util.List;

import models.MAComponent;
import models.MAExperiment;
import models.MAUser;
import play.mvc.Controller;
import play.mvc.Result;

public class MAController extends Controller {

	public static final String COOKIE_EMAIL = "email";

	public static Result badRequestExperimentNotExist(Long experimentId,
			MAUser user, List<MAExperiment> experimentList) {
		String errorMsg = "An experiment with id " + experimentId
				+ " doesn't exist.";
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(experimentList,
				userList, errorMsg, user));
	}

	public static Result badRequestUserNotExist(String email,
			MAUser loggedInUser, List<MAExperiment> experimentList) {
		String errorMsg = "An user with email " + email + " doesn't exist.";
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(experimentList,
				userList, errorMsg, loggedInUser));
	}

	public static Result badRequestComponentNotExist(Long componentId,
			MAExperiment experiment, MAUser user,
			List<MAExperiment> experimentList) {
		String errorMsg = "An component with id " + componentId
				+ " doesn't exist.";
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(experimentList,
				userList, errorMsg, user));
	}

	public static Result badRequestComponentNotBelongToExperiment(
			MAExperiment experiment, MAComponent component, MAUser user,
			List<MAExperiment> experimentList) {
		String errorMsg = "There is no experiment with id " + experiment.id
				+ " that has a component with id " + component.id + ".";
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(experimentList,
				userList, errorMsg, user));
	}

	public static Result forbiddenNotMember(MAUser user,
			MAExperiment experiment, List<MAExperiment> experimentList) {
		String errorMsg = user.name + " (" + user.email
				+ ") isn't member of experiment " + experiment.id + " \""
				+ experiment.title + "\".";
		List<MAUser> userList = MAUser.findAll();
		return forbidden(views.html.admin.index.render(experimentList,
				userList, errorMsg, user));
	}

}
