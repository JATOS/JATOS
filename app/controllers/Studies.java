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

public class Studies extends Controller {

	public static final String USER = "user";
	public static final String TITLE = "title";
	public static final String DESCRIPTION = "description";
	public static final String AN_STUDY_SHOULD_HAVE_AT_LEAST_ONE_MEMBER = "An study should have at least one member.";

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result index(Long studyId) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		Result result = checkStandard(study, studyId, loggedInUser, studyList);
		if (result != null) {
			return result;
		}

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study));
		return ok(views.html.mecharg.study.index.render(studyList, loggedInUser,
				breadcrumbs, null, study));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result create() {
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(), "New Study");
		return ok(views.html.mecharg.study.create.render(studyList, loggedInUser,
				breadcrumbs, Form.form(StudyModel.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submit() {
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
			return badRequest(views.html.mecharg.study.create.render(studyList,
					loggedInUser, breadcrumbs, form));
		} else {
			StudyModel study = form.get();
			study.addMember(loggedInUser);
			study.persist();
			return redirect(routes.Studies.index(study.getId()));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result properties(Long studyId) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		Result result = checkStandard(study, studyId, loggedInUser, studyList);
		if (result != null) {
			return result;
		}

		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "Properties");
		return ok(views.html.mecharg.study.properties.render(studyList, loggedInUser,
				breadcrumbs, study, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitProperties(Long studyId) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		Result result = checkStandard(study, studyId, loggedInUser, studyList);
		if (result != null) {
			return result;
		}

		Form<StudyModel> form = Form.form(StudyModel.class).bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(),
					Breadcrumbs.getStudyBreadcrumb(study), "Properties");
			return badRequest(views.html.mecharg.study.properties.render(studyList,
					loggedInUser, breadcrumbs, study, form));
		}

		// Update study in DB
		DynamicForm requestData = Form.form().bindFromRequest();
		String title = requestData.get(TITLE);
		String description = requestData.get(DESCRIPTION);
		study.update(title, description);
		study.merge();
		return redirect(routes.Studies.index(studyId));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result remove(Long studyId) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		Result result = checkStandard(study, studyId, loggedInUser, studyList);
		if (result != null) {
			return result;
		}

		study.remove();
		return redirect(routes.Dashboard.dashboard());
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result changeMembers(Long studyId) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		Result result = checkStandard(study, studyId, loggedInUser, studyList);
		if (result != null) {
			return result;
		}

		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "Change Members");
		return ok(views.html.mecharg.study.changeMembers.render(studyList,
				loggedInUser, breadcrumbs, study, userList, null));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitChangedMembers(Long studyId) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		Result result = checkStandard(study, studyId, loggedInUser, studyList);
		if (result != null) {
			return result;
		}

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedUsers = formMap.get(USER);
		if (checkedUsers == null || checkedUsers.length < 1) {
			String errorMsg = AN_STUDY_SHOULD_HAVE_AT_LEAST_ONE_MEMBER;
			List<UserModel> userList = UserModel.findAll();
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(),
					Breadcrumbs.getStudyBreadcrumb(study), "Change Members");
			return badRequest(views.html.mecharg.study.changeMembers.render(
					studyList, loggedInUser, breadcrumbs, study, userList,
					errorMsg));
		}
		study.getMemberList().clear();
		for (String email : checkedUsers) {
			UserModel user = UserModel.findByEmail(email);
			if (user != null) {
				study.addMember(user);
				study.merge();
			}
		}

		return redirect(routes.Studies.index(studyId));
	}

	/**
	 * Ajax POST request to change the oder of components within an study.
	 */
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result changeComponentOrder(Long studyId, Long componentId,
			String direction) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		if (study == null) {
			return badRequest(BadRequests.studyNotExist(studyId));
		}
		if (!study.hasMember(loggedInUser)) {
			return forbidden(BadRequests.notMember(loggedInUser.getName(),
					loggedInUser.getEmail(), study.getId(), study.getTitle()));
		}

		ComponentModel component = ComponentModel.findById(componentId);
		if (component == null) {
			return badRequest(BadRequests.componentNotExist(componentId));
		}
		if (!study.hasComponent(component)) {
			badRequest(BadRequests.componentNotBelongToStudy(studyId, componentId));
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
	@Security.Authenticated(Secured.class)
	public static Result showMTurkSourceCode(Long studyId) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		Result result = checkStandard(study, studyId, loggedInUser, studyList);
		if (result != null) {
			return result;
		}

		String hostname = request().host();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				"Mechanical Turk HIT layout source code");
		return ok(views.html.mecharg.study.mTurkSourceCode.render(studyList,
				loggedInUser, breadcrumbs, null, study, hostname));
	}

	private static Result checkStandard(StudyModel study, Long studyId,
			UserModel loggedInUser, List<StudyModel> studyList) {
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		if (study == null) {
			return BadRequests.badRequestStudyNotExist(studyId, loggedInUser, studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			return BadRequests.forbiddenNotMember(loggedInUser, study, studyList);
		}
		return null;
	}

}
