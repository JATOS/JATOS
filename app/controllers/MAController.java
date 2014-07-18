package controllers;

import java.util.List;

import models.MAComponent;
import models.MAStudy;
import models.MAUser;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

public class MAController extends Controller {

	public static final String COOKIE_EMAIL = "email";

	public static String studyNotExist(Long studyId) {
		String errorMsg = "An study with id " + studyId
				+ " doesn't exist.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result badRequestStudyNotExist(Long studyId,
			MAUser user, List<MAStudy> studyList) {
		String errorMsg = studyNotExist(studyId);
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(studyList,
				userList, errorMsg, user));
	}

	public static String userNotExist(String email) {
		String errorMsg = "An user with email " + email + " doesn't exist.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result badRequestUserNotExist(String email,
			MAUser loggedInUser, List<MAStudy> studyList) {
		String errorMsg = userNotExist(email);
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(studyList,
				userList, errorMsg, loggedInUser));
	}

	public static String componentNotExist(Long componentId) {
		String errorMsg = "An component with id " + componentId + " doesn't exist.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result badRequestComponentNotExist(Long componentId,
			MAStudy study, MAUser user,
			List<MAStudy> studyList) {
		String errorMsg = componentNotExist(componentId);
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(studyList,
				userList, errorMsg, user));
	}

	public static String componentNotBelongToStudy(Long studyId,
			Long componentId) {
		String errorMsg = "There is no study with id " + studyId
				+ " that has a component with id " + componentId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result badRequestComponentNotBelongToStudy(
			MAStudy study, MAComponent component, MAUser user,
			List<MAStudy> studyList) {
		String errorMsg = componentNotBelongToStudy(study.getId(),
				component.getId());
		List<MAUser> userList = MAUser.findAll();
		return badRequest(views.html.admin.index.render(studyList,
				userList, errorMsg, user));
	}

	public static String notMember(String username, String email,
			Long studyId, String studyTitle) {
		String errorMsg = username + " (" + email + ") isn't member of study "
				+ studyId + " \"" + studyTitle + "\".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result forbiddenNotMember(MAUser user,
			MAStudy study, List<MAStudy> studyList) {
		String errorMsg = notMember(user.getName(), user.getEmail(), study.getId(),
				study.getTitle());
		List<MAUser> userList = MAUser.findAll();
		return forbidden(views.html.admin.index.render(studyList,
				userList, errorMsg, user));
	}

	public static String urlViewEmpty(Long componentId) {
		String errorMsg = "Component " + componentId + "'s URL field is empty.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result badRequestUrlViewEmpty(MAUser user,
			MAStudy study, MAComponent component,
			List<MAStudy> studyList) {
		String errorMsg = urlViewEmpty(component.getId());
		List<MAUser> userList = MAUser.findAll();
		return forbidden(views.html.admin.index.render(studyList,
				userList, errorMsg, user));
	}

}
