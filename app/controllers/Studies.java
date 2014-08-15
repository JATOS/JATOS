package controllers;

import java.util.List;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import play.mvc.SimpleResult;
import services.ErrorMessages;
import services.Persistance;
import controllers.publix.MAPublix;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Studies extends Controller {

	public static final String USER = "user";
	public static final String TITLE = "title";
	public static final String DESCRIPTION = "description";
	public static final String STUDY = "study";

	@Transactional
	public static Result index(Long studyId) throws ResultException {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandard(study, studyId, loggedInUser, studyList);

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		return ok(views.html.mecharg.study.index.render(studyList,
				loggedInUser, breadcrumbs, null, study));
	}

	@Transactional
	public static Result create() {
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(), "New Study");
		return ok(views.html.mecharg.study.create.render(studyList,
				loggedInUser, breadcrumbs, Form.form(StudyModel.class)));
	}

	@Transactional
	public static Result submit() throws ResultException {
		Form<StudyModel> form = Form.form(StudyModel.class).bindFromRequest();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		if (form.hasErrors()) {
			List<StudyModel> studyList = StudyModel.findAll();
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(), "New Study");
			SimpleResult result = badRequest(views.html.mecharg.study.create
					.render(studyList, loggedInUser, breadcrumbs, form));
			throw new ResultException(result);
		} else {
			StudyModel study = form.get();
			Persistance.addMemberToStudy(study, loggedInUser);
			return redirect(routes.Studies.index(study.getId()));
		}
	}

	@Transactional
	public static Result properties(Long studyId) throws ResultException {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandard(study, studyId, loggedInUser, studyList);

		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "Properties");
		return ok(views.html.mecharg.study.properties.render(studyList,
				loggedInUser, breadcrumbs, study, form));
	}

	@Transactional
	public static Result submitProperties(Long studyId) throws ResultException {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandard(study, studyId, loggedInUser, studyList);

		Form<StudyModel> form = Form.form(StudyModel.class).bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(),
					Breadcrumbs.getStudyBreadcrumb(study), "Properties");
			SimpleResult result = badRequest(views.html.mecharg.study.properties
					.render(studyList, loggedInUser, breadcrumbs, study, form));
			throw new ResultException(result);
		}

		// Update study in DB
		DynamicForm requestData = Form.form().bindFromRequest();
		String title = requestData.get(TITLE);
		String description = requestData.get(DESCRIPTION);
		Persistance.updateStudy(study, title, description);
		return redirect(routes.Studies.index(studyId));
	}

	@Transactional
	public static Result remove(Long studyId) throws ResultException {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandard(study, studyId, loggedInUser, studyList);

		Persistance.removeStudy(study);
		return redirect(routes.Dashboard.dashboard());
	}

	@Transactional
	public static Result changeMembers(Long studyId) throws ResultException {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandard(study, studyId, loggedInUser, studyList);

		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "Change Members");
		return ok(views.html.mecharg.study.changeMembers.render(studyList,
				loggedInUser, breadcrumbs, study, userList, null));
	}

	@Transactional
	public static Result submitChangedMembers(Long studyId)
			throws ResultException {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandard(study, studyId, loggedInUser, studyList);

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedUsers = formMap.get(USER);
		if (checkedUsers == null || checkedUsers.length < 1) {
			throw BadRequests.forbiddenStudyAtLeastOneMember(loggedInUser,
					study, studyList);
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
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
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

		ComponentModel component = ComponentModel.findById(componentId);
		if (component == null) {
			String errorMsg = ErrorMessages.componentNotExist(componentId);
			SimpleResult result = badRequest(errorMsg);
			throw new ResultException(result, errorMsg);
		}
		if (!study.hasComponent(component)) {
			String errorStr = ErrorMessages.componentNotBelongToStudy(studyId,
					componentId);
			SimpleResult result = badRequest(errorStr);
			throw new ResultException(result, errorStr);
		}

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
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
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

		session(MAPublix.MECHARG_TRY, STUDY);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startStudy(study.getId()));
	}

	@Transactional
	public static Result showMTurkSourceCode(Long studyId)
			throws ResultException {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandard(study, studyId, loggedInUser, studyList);

		String hostname = request().host();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				"Mechanical Turk HIT layout source code");
		return ok(views.html.mecharg.study.mTurkSourceCode.render(studyList,
				loggedInUser, breadcrumbs, null, study, hostname));
	}

	private static void checkStandard(StudyModel study, Long studyId,
			UserModel loggedInUser, List<StudyModel> studyList)
			throws ResultException {
		if (loggedInUser == null) {
			throw new ResultException(redirect(routes.Authentication.login()));
		}
		if (study == null) {
			throw BadRequests.badRequestStudyNotExist(studyId, loggedInUser,
					studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}
	}

}
