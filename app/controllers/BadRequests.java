package controllers;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.mvc.Controller;
import play.mvc.SimpleResult;
import exceptions.ResultException;

public class BadRequests extends Controller {

	public static String studyNotExist(Long studyId) {
		String errorMsg = "An study with id " + studyId + " doesn't exist.";
		return errorMsg;
	}

	public static ResultException badRequestStudyNotExist(Long studyId,
			UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = studyNotExist(studyId);
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(Breadcrumbs
				.getDashboardBreadcrumb());
		SimpleResult result = badRequest(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static String userNotExist(String email) {
		String errorMsg = "An user with email " + email + " doesn't exist.";
		return errorMsg;
	}

	public static ResultException badRequestUserNotExist(String email,
			UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = userNotExist(email);
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(Breadcrumbs
				.getDashboardBreadcrumb());
		SimpleResult result = badRequest(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static String componentNotExist(Long componentId) {
		String errorMsg = "An component with id " + componentId
				+ " doesn't exist.";
		return errorMsg;
	}

	public static ResultException badRequestComponentNotExist(
			Long componentId, StudyModel study, UserModel loggedInUser,
			List<StudyModel> studyList) {
		String errorMsg = componentNotExist(componentId);
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		SimpleResult result = badRequest(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static String componentNotBelongToStudy(Long studyId,
			Long componentId) {
		String errorMsg = "There is no study with id " + studyId
				+ " that has a component with id " + componentId + ".";
		return errorMsg;
	}

	public static ResultException badRequestComponentNotBelongToStudy(
			StudyModel study, ComponentModel component, UserModel loggedInUser,
			List<StudyModel> studyList) {
		String errorMsg = componentNotBelongToStudy(study.getId(),
				component.getId());
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		SimpleResult result = badRequest(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static String notMember(String username, String email, Long studyId,
			String studyTitle) {
		String errorMsg = username + " (" + email + ") isn't member of study "
				+ studyId + " \"" + studyTitle + "\".";
		return errorMsg;
	}

	public static ResultException forbiddenNotMember(UserModel loggedInUser,
			StudyModel study, List<StudyModel> studyList) {
		String errorMsg = notMember(loggedInUser.getName(),
				loggedInUser.getEmail(), study.getId(), study.getTitle());
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		SimpleResult result = forbidden(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static String urlViewEmpty(Long componentId) {
		String errorMsg = "Component " + componentId + "'s URL field is empty.";
		return errorMsg;
	}

	public static ResultException badRequestUrlViewEmpty(
			UserModel loggedInUser, StudyModel study, ComponentModel component,
			List<StudyModel> studyList) {
		String errorMsg = urlViewEmpty(component.getId());
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				Breadcrumbs.getComponentBreadcrumb(study, component));
		SimpleResult result = forbidden(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static String studyAtLeastOneMember() {
		String errorMsg = "An study should have at least one member.";
		return errorMsg;
	}

	public static ResultException badRequestStudyAtLeastOneMember(
			UserModel loggedInUser, StudyModel study, List<StudyModel> studyList) {
		String errorMsg = studyAtLeastOneMember();
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "Change Members");
		SimpleResult result = forbidden(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static String mustBeLoggedInAsUser(UserModel user) {
		return "You must be logged in as " + user.toString()
				+ " to update this user.";
	}

	public static ResultException badRequestMustBeLoggedInAsUser(
			UserModel user, UserModel loggedInUser,
			List<StudyModel> studyList) {
		String errorMsg = mustBeLoggedInAsUser(user);
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getUserBreadcrumb(user), "Change Password");
		SimpleResult result = badRequest(views.html.mecharg.dashboard
				.render(studyList, loggedInUser, breadcrumbs, userList,
						errorMsg));
		return new ResultException(result, errorMsg);
	}

}
