package controllers;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.mvc.Controller;
import play.mvc.SimpleResult;
import services.ErrorMessages;
import exceptions.ResultException;

public class BadRequests extends Controller {

	public static ResultException badRequestStudyNotExist(Long studyId,
			UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.studyNotExist(studyId);
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(Breadcrumbs
				.getDashboardBreadcrumb());
		SimpleResult result = badRequest(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestUserNotExist(String email,
			UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.userNotExist(email);
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(Breadcrumbs
				.getDashboardBreadcrumb());
		SimpleResult result = badRequest(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestComponentNotExist(
			Long componentId, StudyModel study, UserModel loggedInUser,
			List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.componentNotExist(componentId);
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		SimpleResult result = badRequest(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestComponentNotBelongToStudy(
			StudyModel study, ComponentModel component, UserModel loggedInUser,
			List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.componentNotBelongToStudy(study.getId(),
				component.getId());
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		SimpleResult result = badRequest(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException forbiddenNotMember(UserModel loggedInUser,
			StudyModel study, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.notMember(loggedInUser.getName(),
				loggedInUser.getEmail(), study.getId(), study.getTitle());
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		SimpleResult result = forbidden(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestUrlViewEmpty(
			UserModel loggedInUser, StudyModel study, ComponentModel component,
			List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.urlViewEmpty(component.getId());
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				Breadcrumbs.getComponentBreadcrumb(study, component));
		SimpleResult result = forbidden(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException forbiddenStudyAtLeastOneMember(
			UserModel loggedInUser, StudyModel study, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.studyAtLeastOneMember();
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "Change Members");
		SimpleResult result = forbidden(views.html.mecharg.dashboard.render(
				studyList, loggedInUser, breadcrumbs, userList, errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestMustBeLoggedInAsUser(
			UserModel user, UserModel loggedInUser,
			List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.mustBeLoggedInAsUser(user);
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
