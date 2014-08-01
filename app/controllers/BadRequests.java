package controllers;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

public class BadRequests extends Controller {

	public static String studyNotExist(Long studyId) {
		String errorMsg = "An study with id " + studyId + " doesn't exist.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result badRequestStudyNotExist(Long studyId,
			UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = studyNotExist(studyId);
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(Breadcrumbs
				.getDashboardBreadcrumb());
		return badRequest(views.html.mecharg.dashboard.render(studyList,
				loggedInUser, breadcrumbs, userList, errorMsg));
	}

	public static String userNotExist(String email) {
		String errorMsg = "An user with email " + email + " doesn't exist.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result badRequestUserNotExist(String email,
			UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = userNotExist(email);
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(Breadcrumbs
				.getDashboardBreadcrumb());
		return badRequest(views.html.mecharg.dashboard.render(studyList,
				loggedInUser, breadcrumbs, userList, errorMsg));
	}

	public static String componentNotExist(Long componentId) {
		String errorMsg = "An component with id " + componentId
				+ " doesn't exist.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result badRequestComponentNotExist(Long componentId,
			StudyModel study, UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = componentNotExist(componentId);
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		return badRequest(views.html.mecharg.dashboard.render(studyList,
				loggedInUser, breadcrumbs, userList, errorMsg));
	}

	public static String componentNotBelongToStudy(Long studyId,
			Long componentId) {
		String errorMsg = "There is no study with id " + studyId
				+ " that has a component with id " + componentId + ".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result badRequestComponentNotBelongToStudy(StudyModel study,
			ComponentModel component, UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = componentNotBelongToStudy(study.getId(),
				component.getId());
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		return badRequest(views.html.mecharg.dashboard.render(studyList,
				loggedInUser, breadcrumbs, userList, errorMsg));
	}

	public static String notMember(String username, String email, Long studyId,
			String studyTitle) {
		String errorMsg = username + " (" + email + ") isn't member of study "
				+ studyId + " \"" + studyTitle + "\".";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result forbiddenNotMember(UserModel loggedInUser, StudyModel study,
			List<StudyModel> studyList) {
		String errorMsg = notMember(loggedInUser.getName(),
				loggedInUser.getEmail(), study.getId(), study.getTitle());
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		return forbidden(views.html.mecharg.dashboard.render(studyList, loggedInUser,
				breadcrumbs, userList, errorMsg));
	}

	public static String urlViewEmpty(Long componentId) {
		String errorMsg = "Component " + componentId + "'s URL field is empty.";
		Logger.info(errorMsg);
		return errorMsg;
	}

	public static Result badRequestUrlViewEmpty(UserModel loggedInUser,
			StudyModel study, ComponentModel component, List<StudyModel> studyList) {
		String errorMsg = urlViewEmpty(component.getId());
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				Breadcrumbs.getComponentBreadcrumb(study, component));
		return forbidden(views.html.mecharg.dashboard.render(studyList, loggedInUser,
				breadcrumbs, userList, errorMsg));
	}

}
