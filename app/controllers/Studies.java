package controllers;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.StudyResult;
import models.workers.MAWorker;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;
import play.mvc.SimpleResult;
import services.ErrorMessages;
import services.Persistance;
import controllers.publix.MAPublix;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Studies extends Controller {

	public static final String STUDY = "study";
	private static final String CLASS_NAME = Studies.class.getSimpleName();

	@Transactional
	public static Result index(Long studyId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandardForStudy(study, studyId, loggedInUser, studyList);

		List<StudyResult> studyResultList = getStudyResultsNotDoneByMA(study);

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		return status(httpStatus, views.html.mecharg.study.index.render(
				studyList, loggedInUser, breadcrumbs, errorMsg, study,
				studyResultList));
	}

	@Transactional
	public static Result index(Long studyId) throws ResultException {
		return index(studyId, null, Http.Status.OK);
	}

	private static List<StudyResult> getStudyResultsNotDoneByMA(StudyModel study) {
		List<StudyResult> studyResultList = StudyResult.findAllByStudy(study);
		Iterator<StudyResult> iter = studyResultList.iterator();
		while (iter.hasNext()) {
			if (iter.next().getWorker() instanceof MAWorker) {
				iter.remove();
			}
		}
		return studyResultList;
	}

	@Transactional
	public static Result create() throws ResultException {
		Logger.info(CLASS_NAME + ".create: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = Users.getLoggedInUser();

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(), "New Study");
		return ok(views.html.mecharg.study.create.render(studyList,
				loggedInUser, breadcrumbs, Form.form(StudyModel.class)));
	}

	@Transactional
	public static Result submit() throws ResultException {
		Logger.info(CLASS_NAME + ".submit: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		Form<StudyModel> form = Form.form(StudyModel.class).bindFromRequest();
		UserModel loggedInUser = Users.getLoggedInUser();
		if (form.hasErrors()) {
			List<StudyModel> studyList = StudyModel.findAll();
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getHomeBreadcrumb(), "New Study");
			SimpleResult result = badRequest(views.html.mecharg.study.create
					.render(studyList, loggedInUser, breadcrumbs, form));
			throw new ResultException(result);
		} else {
			StudyModel study = form.get();
			study.persist();
			Persistance.addMemberToStudy(study, loggedInUser);
			return redirect(routes.Studies.index(study.getId()));
		}
	}

	@Transactional
	public static Result edit(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".edit: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandardForStudy(study, studyId, loggedInUser, studyList);
		checkStudyLocked(study);

		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "Edit");
		return ok(views.html.mecharg.study.edit.render(studyList, loggedInUser,
				breadcrumbs, study, form));
	}

	@Transactional
	public static Result submitEdited(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandardForStudy(study, studyId, loggedInUser, studyList);
		checkStudyLocked(study);

		Form<StudyModel> form = Form.form(StudyModel.class).bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getHomeBreadcrumb(),
					Breadcrumbs.getStudyBreadcrumb(study), "Edit");
			SimpleResult result = badRequest(views.html.mecharg.study.edit
					.render(studyList, loggedInUser, breadcrumbs, study, form));
			throw new ResultException(result);
		}

		// Update study in DB
		DynamicForm requestData = Form.form().bindFromRequest();
		String title = requestData.get(StudyModel.TITLE);
		String description = requestData.get(StudyModel.DESCRIPTION);
		String jsonData = requestData.get(StudyModel.JSON_DATA);
		Persistance.updateStudy(study, title, description, jsonData);
		return redirect(routes.Studies.index(studyId));
	}

	/**
	 * Ajax POST request to swap the locked field.
	 */
	@Transactional
	public static Result swapLock(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".swapLock: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUserAjax();
		checkStandardForStudyAjax(study, studyId, loggedInUser);

		study.setLocked(!study.isLocked());
		study.merge();
		return ok(String.valueOf(study.isLocked()));
	}

	/**
	 * Ajax DELETE request to remove a study
	 */
	@Transactional
	public static Result remove(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUserAjax();
		checkStandardForStudyAjax(study, studyId, loggedInUser);
		checkStudyLockedAjax(study);

		Persistance.removeStudy(study);
		return ok();
	}

	/**
	 * Ajax DELETE request to remove all study results including their component
	 * results.
	 */
	@Transactional
	public static Result removeAllResults(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".removeAllResults: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUserAjax();
		checkStandardForStudyAjax(study, studyId, loggedInUser);
		checkStudyLockedAjax(study);

		Persistance.removeAllStudyResults(study);
		return ok();
	}

	@Transactional
	public static Result cloneStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".cloneStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandardForStudy(study, studyId, loggedInUser, studyList);

		StudyModel clone = new StudyModel(study);
		clone.addMember(loggedInUser);
		clone.persist();
		return redirect(routes.Studies.index(clone.getId()));
	}

	@Transactional
	public static Result changeMembers(Long studyId) throws ResultException {
		return changeMembers(studyId, null, Http.Status.OK);
	}

	@Transactional
	public static Result changeMembers(Long studyId, String errorMsg,
			int httpStatus) throws ResultException {
		Logger.info(CLASS_NAME + ".changeMembers: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandardForStudy(study, studyId, loggedInUser, studyList);

		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "Change Members");
		return status(httpStatus,
				views.html.mecharg.study.changeMembers.render(studyList,
						loggedInUser, breadcrumbs, study, userList, errorMsg));
	}

	@Transactional
	public static Result submitChangedMembers(Long studyId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".submitChangedMembers: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandardForStudy(study, studyId, loggedInUser, studyList);

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedUsers = formMap.get(StudyModel.MEMBERS);
		if (checkedUsers == null || checkedUsers.length < 1) {
			String errorMsg = ErrorMessages.studyAtLeastOneMember();
			SimpleResult result = (SimpleResult) changeMembers(studyId,
					errorMsg, Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
		study.getMemberList().clear();
		for (String email : checkedUsers) {
			UserModel user = UserModel.findByEmail(email);
			if (user != null) {
				Persistance.addMemberToStudy(study, user);
			}
		}

		return redirect(routes.Studies.index(studyId));
	}

	/**
	 * Ajax POST request to change the oder of components within an study.
	 */
	@Transactional
	public static Result changeComponentOrder(Long studyId, Long componentId,
			String direction) throws ResultException {
		Logger.info(CLASS_NAME + ".changeComponentOrder: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUserAjax();
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandardForStudyAjax(study, studyId, loggedInUser);
		checkStudyLockedAjax(study);
		Components.checkStandardForComponentsAjax(studyId, componentId, study,
				loggedInUser, component);

		if (direction.equals("up")) {
			study.componentOrderMinusOne(component);
		}
		if (direction.equals("down")) {
			study.componentOrderPlusOne(component);
		}
		study.refresh();

		return ok();
	}

	@Transactional
	public static Result tryStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".tryStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandardForStudy(study, studyId, loggedInUser, studyList);
		checkStudyLocked(study);

		session(MAPublix.MECHARG_TRY, Studies.STUDY);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startStudy(study.getId()));
	}

	@Transactional
	public static Result showMTurkSourceCode(Long studyId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".showMTurkSourceCode: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandardForStudy(study, studyId, loggedInUser, studyList);

		String hostname = request().host();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				"Mechanical Turk HIT layout source code");
		return ok(views.html.mecharg.study.mTurkSourceCode.render(studyList,
				loggedInUser, breadcrumbs, null, study, hostname));
	}

	public static void checkStudyLocked(StudyModel study)
			throws ResultException {
		if (study.isLocked()) {
			String errorMsg = ErrorMessages.studyLocked(study.getId());
			SimpleResult result = (SimpleResult) index(study.getId(), errorMsg,
					Http.Status.FORBIDDEN);
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStudyLockedAjax(StudyModel study)
			throws ResultException {
		if (study.isLocked()) {
			String errorMsg = ErrorMessages.studyLocked(study.getId());
			SimpleResult result = forbidden(errorMsg);
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStandardForStudy(StudyModel study, Long studyId,
			UserModel loggedInUser, List<StudyModel> studyList)
			throws ResultException {
		if (study == null) {
			String errorMsg = ErrorMessages.studyNotExist(studyId);
			SimpleResult result = (SimpleResult) Home.home(errorMsg,
					Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
		if (!study.hasMember(loggedInUser)) {
			String errorMsg = ErrorMessages.notMember(loggedInUser.getName(),
					loggedInUser.getEmail(), studyId, study.getTitle());
			SimpleResult result = (SimpleResult) Home.home(errorMsg,
					Http.Status.FORBIDDEN);
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStandardForStudyAjax(StudyModel study,
			Long studyId, UserModel loggedInUser) throws ResultException {
		if (study == null) {
			String errorMsg = ErrorMessages.studyNotExist(studyId);
			SimpleResult result = badRequest(errorMsg);
			throw new ResultException(result, errorMsg);
		}
		if (!study.hasMember(loggedInUser)) {
			String errorMsg = ErrorMessages.notMember(loggedInUser.getName(),
					loggedInUser.getEmail(), study.getId(), study.getTitle());
			SimpleResult result = forbidden(errorMsg);
			throw new ResultException(result, errorMsg);
		}
	}

}
