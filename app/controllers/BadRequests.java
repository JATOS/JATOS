package controllers;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.MAWorker;
import models.workers.Worker;
import play.mvc.Controller;
import play.mvc.SimpleResult;
import services.ErrorMessages;
import exceptions.ResultException;

/**
 * Helper class with functions that generate whole error pages (not just the
 * error message) and return them wrapped in a ResultException. The
 * ResultException is then caught in Global.
 * 
 * @author madsen
 */
public class BadRequests extends Controller {

	public static ResultException badRequestStudyNotExist(Long studyId,
			UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.studyNotExist(studyId);
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(Breadcrumbs
				.getHomeBreadcrumb());
		SimpleResult result = badRequest(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestUserNotExist(String email,
			UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.userNotExist(email);
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(Breadcrumbs
				.getHomeBreadcrumb());
		SimpleResult result = badRequest(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestWorkerNotExist(Long workerId,
			UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.workerNotExist(workerId);
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(Breadcrumbs
				.getHomeBreadcrumb());
		SimpleResult result = badRequest(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestComponentNotExist(Long componentId,
			StudyModel study, UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.componentNotExist(componentId);
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		SimpleResult result = badRequest(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestComponentNotBelongToStudy(
			StudyModel study, ComponentModel component, UserModel loggedInUser,
			List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.componentNotBelongToStudy(
				study.getId(), component.getId());
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		SimpleResult result = badRequest(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException forbiddenNotMember(UserModel loggedInUser,
			StudyModel study, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.notMember(loggedInUser.getName(),
				loggedInUser.getEmail(), study.getId(), study.getTitle());
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		SimpleResult result = forbidden(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException forbiddenRemoveMAWorker(MAWorker worker,
			UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.removeMAWorker(worker.getId(), worker
				.getUser().getName(), worker.getUser().getEmail());
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getWorkerBreadcrumb(worker));
		SimpleResult result = forbidden(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestUrlViewEmpty(
			UserModel loggedInUser, StudyModel study, ComponentModel component,
			List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.urlViewEmpty(component.getId());
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				Breadcrumbs.getComponentBreadcrumb(study, component));
		SimpleResult result = forbidden(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException forbiddenStudyAtLeastOneMember(
			UserModel loggedInUser, StudyModel study, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.studyAtLeastOneMember();
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "Change Members");
		SimpleResult result = forbidden(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestMustBeLoggedInAsUser(
			UserModel user, UserModel loggedInUser, List<StudyModel> studyList) {
		String errorMsg = ErrorMessages.mustBeLoggedInAsUser(user);
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getUserBreadcrumb(user), "Change Password");
		SimpleResult result = badRequest(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

	public static ResultException badRequestComponentResultNotExist(
			Long componentResultId, StudyModel study, UserModel loggedInUser,
			List<StudyModel> studyList) {
		String errorMsg = ErrorMessages
				.componentResultNotExist(componentResultId);
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		SimpleResult result = badRequest(views.html.mecharg.home.render(
				studyList, loggedInUser, breadcrumbs, userList, workerList,
				errorMsg));
		return new ResultException(result, errorMsg);
	}

}
