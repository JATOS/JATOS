package controllers;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.Worker;
import play.Logger;
import play.api.mvc.Call;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;
import services.Breadcrumbs;
import services.ErrorMessages;
import services.IOUtils;
import services.JsonUtils;
import services.Messages;
import services.PersistanceUtils;
import controllers.publix.JatosPublix;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Studies extends Controller {

	private static final String CLASS_NAME = Studies.class.getSimpleName();

	@Transactional
	public static Result index(Long studyId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		Set<Worker> workerSet = ControllerUtils.retrieveWorkers(study);
		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study);
		return status(httpStatus, views.html.jatos.study.index.render(
				studyList, loggedInUser, breadcrumbs, messages, study,
				workerSet));
	}

	@Transactional
	public static Result index(Long studyId, String errorMsg)
			throws ResultException {
		return index(studyId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public static Result index(Long studyId) throws ResultException {
		return index(studyId, null, Http.Status.OK);
	}

	@Transactional
	public static Result create() throws ResultException {
		Logger.info(CLASS_NAME + ".create: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());

		Form<StudyModel> form = Form.form(StudyModel.class);
		Call submitAction = routes.Studies.submit();
		Breadcrumbs breadcrumbs = Breadcrumbs
				.generateForHome(Breadcrumbs.NEW_STUDY);
		return ok(views.html.jatos.study.edit.render(studyList, loggedInUser,
				breadcrumbs, null, submitAction, form, false));
	}

	@Transactional
	public static Result submit() throws ResultException {
		Logger.info(CLASS_NAME + ".submit: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		Form<StudyModel> form = Form.form(StudyModel.class).bindFromRequest();
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		if (form.hasErrors()) {
			Breadcrumbs breadcrumbs = Breadcrumbs
					.generateForHome(Breadcrumbs.NEW_STUDY);
			Call submitAction = routes.Studies.submit();
			ControllerUtils.throwEditStudyResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, false);
		}

		// Persist in DB
		StudyModel study = form.get();
		PersistanceUtils.addStudy(study, loggedInUser);

		// Create study's dir
		try {
			IOUtils.createStudyDir(study.getDirName());
		} catch (IOException e) {
			form.reject(e.getMessage());
			Breadcrumbs breadcrumbs = Breadcrumbs
					.generateForHome(Breadcrumbs.NEW_STUDY);
			Call submitAction = routes.Studies.submit();
			ControllerUtils.throwEditStudyResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, false);
		}
		return redirect(routes.Studies.index(study.getId(), null));
	}

	@Transactional
	public static Result edit(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".edit: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		Messages messages = new Messages();
		if (study.isLocked()) {
			messages.warning(ErrorMessages.STUDY_IS_LOCKED);
		}
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		Call submitAction = routes.Studies.submitEdited(study.getId());
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.EDIT_PROPERTIES);
		return ok(views.html.jatos.study.edit.render(studyList, loggedInUser,
				breadcrumbs, messages, submitAction, form, study.isLocked()));
	}

	@Transactional
	public static Result submitEdited(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		Form<StudyModel> form = Form.form(StudyModel.class).bindFromRequest();
		if (form.hasErrors()) {
			Call submitAction = routes.Studies.submitEdited(study.getId());
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
					Breadcrumbs.EDIT_PROPERTIES);
			ControllerUtils.throwEditStudyResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, study.isLocked());
		}

		// Update study in DB
		StudyModel updatedStudy = form.get();
		// Get old dirName before study is updated
		String oldDirName = study.getDirName();
		PersistanceUtils.updateStudysProperties(study, updatedStudy);

		// Rename study dir
		try {
			IOUtils.renameStudyDir(oldDirName, study.getDirName());
		} catch (IOException e) {
			form.reject(new ValidationError(StudyModel.DIRNAME, e.getMessage()));
			Call submitAction = routes.Studies.submitEdited(study.getId());
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
					Breadcrumbs.EDIT_PROPERTIES);
			ControllerUtils.throwEditStudyResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, study.isLocked());
		}
		return redirect(routes.Studies.index(studyId, null));
	}

	/**
	 * Ajax POST request to swap the locked field.
	 */
	@Transactional
	public static Result swapLock(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".swapLock: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

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
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		PersistanceUtils.removeStudy(study);

		// Remove study's dir
		try {
			IOUtils.removeStudyDirectory(study.getDirName());
		} catch (IOException e) {
			String errorMsg = e.getMessage();
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok();
	}

	/**
	 * Ajax POST request
	 */
	@Transactional
	public static Result cloneStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".cloneStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		StudyModel clone = new StudyModel(study);
		// Copy study's dir and it's content to cloned study's dir
		try {
			String destDirName = IOUtils.cloneStudyDirectory(study.getDirName());
			clone.setDirName(destDirName);
		} catch (IOException e) {
			ControllerUtils.throwAjaxResultException(e.getMessage(),
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		PersistanceUtils.addStudy(clone, loggedInUser);
		return ok();
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
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		List<UserModel> userList = UserModel.findAll();
		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.CHANGE_MEMBERS);
		return status(httpStatus,
				views.html.jatos.study.changeMembers.render(studyList,
						loggedInUser, breadcrumbs, messages, study, userList));
	}

	@Transactional
	public static Result submitChangedMembers(Long studyId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".submitChangedMembers: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedUsers = formMap.get(StudyModel.MEMBERS);
		if (checkedUsers == null || checkedUsers.length < 1) {
			String errorMsg = ErrorMessages.STUDY_AT_LEAST_ONE_MEMBER;
			ControllerUtils.throwChangeMemberOfStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		study.getMemberList().clear();
		for (String email : checkedUsers) {
			UserModel user = UserModel.findByEmail(email);
			if (user != null) {
				PersistanceUtils.addMemberToStudy(study, user);
			}
		}
		return redirect(routes.Studies.index(studyId, null));
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
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
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
	public static Result showStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".showStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		session(JatosPublix.JATOS_SHOW, JatosPublix.SHOW_STUDY);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startStudy(study.getId()));
	}

	@Transactional
	public static Result showMTurkSourceCode(Long studyId) throws Exception {
		Logger.info(CLASS_NAME + ".showMTurkSourceCode: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		String[] referer = request().headers().get("Referer");
		URL jatosURL = new URL(referer[0]);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.MECHANICAL_TURK_HIT_LAYOUT_SOURCE_CODE);
		return ok(views.html.jatos.study.mTurkSourceCode.render(studyList,
				loggedInUser, breadcrumbs, null, study, jatosURL));
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result tableDataByStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		String dataAsJson = null;
		try {
			dataAsJson = JsonUtils.allComponentsForUI(study.getComponentList());
		} catch (IOException e) {
			String errorMsg = ErrorMessages.PROBLEM_GENERATING_JSON_DATA;
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

	@Transactional
	public static Result workers(Long studyId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".workers: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.WORKERS);
		return status(httpStatus, views.html.jatos.study.studysWorkers.render(
				studyList, loggedInUser, breadcrumbs, messages, study));
	}

	@Transactional
	public static Result workers(Long studyId, String errorMsg)
			throws ResultException {
		return workers(studyId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public static Result workers(Long studyId) throws ResultException {
		return workers(studyId, null, Http.Status.OK);
	}

}
