package controllers;

import java.util.List;
import java.util.Map;

import models.MAComponent;
import models.MAStudy;
import models.MAUser;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Result;
import play.mvc.Security;

public class Studies extends MAController {

	public static final String USER = "user";
	public static final String TITLE = "title";
	public static final String DESCRIPTION = "description";
	public static final String AN_STUDY_SHOULD_HAVE_AT_LEAST_ONE_MEMBER = "An study should have at least one member.";

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result index(Long studyId) {
		MAStudy study = MAStudy.findById(studyId);
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		Result result = checkStandard(study, studyId, user,
				studyList);
		if (result != null) {
			return result;
		}

		return ok(views.html.admin.study.index.render(studyList,
				user, null, study));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result create() {
		List<MAStudy> studyList = MAStudy.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}
		return ok(views.html.admin.study.create.render(studyList,
				loggedInUser, Form.form(MAStudy.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submit() {
		Form<MAStudy> form = Form.form(MAStudy.class)
				.bindFromRequest();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}
		if (form.hasErrors()) {
			List<MAStudy> studyList = MAStudy.findAll();
			return badRequest(views.html.admin.study.create.render(
					studyList, loggedInUser, form));
		} else {
			MAStudy study = form.get();
			study.addMember(loggedInUser);
			study.persist();
			return redirect(routes.Studies.index(study.getId()));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result update(Long studyId) {
		MAStudy study = MAStudy.findById(studyId);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		Result result = checkStandard(study, studyId, loggedInUser,
				studyList);
		if (result != null) {
			return result;
		}

		Form<MAStudy> form = Form.form(MAStudy.class)
				.fill(study);
		return ok(views.html.admin.study.update.render(studyList,
				study, loggedInUser, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdated(Long studyId) {
		MAStudy study = MAStudy.findById(studyId);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		Result result = checkStandard(study, studyId, loggedInUser,
				studyList);
		if (result != null) {
			return result;
		}

		Form<MAStudy> form = Form.form(MAStudy.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(views.html.admin.study.update.render(
					studyList, study, loggedInUser, form));
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
		MAStudy study = MAStudy.findById(studyId);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		Result result = checkStandard(study, studyId, loggedInUser,
				studyList);
		if (result != null) {
			return result;
		}

		study.remove();
		return redirect(routes.Admin.index());
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result updateMembers(Long studyId) {
		MAStudy study = MAStudy.findById(studyId);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		Result result = checkStandard(study, studyId, loggedInUser,
				studyList);
		if (result != null) {
			return result;
		}

		List<MAUser> userList = MAUser.findAll();
		return ok(views.html.admin.study.updateMembers.render(
				studyList, study, userList, loggedInUser, null));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdatedMembers(Long studyId) {
		MAStudy study = MAStudy.findById(studyId);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		Result result = checkStandard(study, studyId, loggedInUser,
				studyList);
		if (result != null) {
			return result;
		}

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedUsers = formMap.get(USER);
		if (checkedUsers == null || checkedUsers.length < 1) {
			String errorMsg = AN_STUDY_SHOULD_HAVE_AT_LEAST_ONE_MEMBER;
			List<MAUser> userList = MAUser.findAll();
			return badRequest(views.html.admin.study.updateMembers.render(
					studyList, study, userList, loggedInUser,
					errorMsg));
		}
		study.getMemberList().clear();
		for (String email : checkedUsers) {
			MAUser user = MAUser.findByEmail(email);
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
	public static Result changeComponentOrder(Long studyId,
			Long componentId, String direction) {
		MAStudy study = MAStudy.findById(studyId);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}
		if (study == null) {
			return badRequest(studyNotExist(studyId));
		}
		if (!study.hasMember(loggedInUser)) {
			return forbidden(notMember(loggedInUser.getName(),
					loggedInUser.getEmail(), study.getId(),
					study.getTitle()));
		}

		MAComponent component = MAComponent.findById(componentId);
		if (component == null) {
			return badRequest(componentNotExist(componentId));
		}
		if (!study.hasComponent(component)) {
			badRequest(componentNotBelongToStudy(studyId, componentId));
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
		MAStudy study = MAStudy.findById(studyId);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		Result result = checkStandard(study, studyId, loggedInUser,
				studyList);
		if (result != null) {
			return result;
		}

		String hostname = request().host();
		return ok(views.html.admin.study.mTurkSourceCode.render(
				studyList, loggedInUser, null, study, hostname));
	}

	private static Result checkStandard(MAStudy study,
			Long studyId, MAUser loggedInUser,
			List<MAStudy> studyList) {
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}
		if (study == null) {
			return badRequestStudyNotExist(studyId, loggedInUser,
					studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			return forbiddenNotMember(loggedInUser, study, studyList);
		}
		return null;
	}

}
