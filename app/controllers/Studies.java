package controllers;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.ClosedStandaloneWorker;
import models.workers.TesterWorker;
import models.workers.Worker;
import play.Logger;
import play.api.mvc.Call;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.Breadcrumbs;
import services.ErrorMessages;
import services.IOUtils;
import services.JsonUtils;
import services.Messages;
import services.PersistanceUtils;
import services.StudyBinder;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.publix.closed_standalone.ClosedStandalonePublix;
import controllers.publix.jatos.JatosPublix;
import controllers.publix.tester.TesterPublix;
import exceptions.ResultException;

/**
 * Controller for all actions regarding studies within the JATOS GUI.
 * 
 * @author Kristian Lange
 */
public class Studies extends Controller {

	public static final String COMPONENT_ORDER_DOWN = "down";
	public static final String COMPONENT_ORDER_UP = "up";
	private static final String CLASS_NAME = Studies.class.getSimpleName();

	/**
	 * Shows the index view with details regarding a study.
	 */
	@Transactional
	public static Result index(Long studyId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
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

	/**
	 * Shows a view with a form to create a new study.
	 */
	@Transactional
	public static Result create() throws ResultException {
		Logger.info(CLASS_NAME + ".create: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());

		StudyModel study = new StudyModel();
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		// It's a generic template for editing a study. We have to tell it the
		// submit action.
		Call submitAction = routes.Studies.submit();
		Breadcrumbs breadcrumbs = Breadcrumbs
				.generateForHome(Breadcrumbs.NEW_STUDY);
		return ok(views.html.jatos.study.edit.render(studyList, loggedInUser,
				breadcrumbs, null, submitAction, form, false));
	}

	/**
	 * POST request of the form to create a new study.
	 */
	@Transactional
	public static Result submit() throws ResultException {
		Logger.info(CLASS_NAME + ".submit: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());

		StudyModel study = StudyBinder.bindStudyFromRequest(request().body()
				.asFormUrlEncoded());
		List<ValidationError> errorList = study.validate();
		if (errorList != null) {
			failStudyCreate(loggedInUser, studyList, study, errorList);
		}

		PersistanceUtils.addStudy(study, loggedInUser);
		createStudyAssetsDir(loggedInUser, studyList, study);
		return redirect(routes.Studies.index(study.getId(), null));
	}

	private static void failStudyCreate(UserModel loggedInUser,
			List<StudyModel> studyList, StudyModel study,
			List<ValidationError> errorList) throws ResultException {
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		for (ValidationError error : errorList) {
			form.reject(error);
		}
		Breadcrumbs breadcrumbs = Breadcrumbs
				.generateForHome(Breadcrumbs.NEW_STUDY);
		Call submitAction = routes.Studies.submit();
		ControllerUtils
				.throwEditStudyResultException(studyList, loggedInUser, form,
						Http.Status.BAD_REQUEST, breadcrumbs, submitAction,
						false);
	}

	/**
	 * Shows a form to edit the study properties.
	 */
	@Transactional
	public static Result edit(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".edit: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
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

	/**
	 * POST request of the edit form to change the properties of a study.
	 */
	@Transactional
	public static Result submitEdited(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		StudyModel updatedStudy = StudyBinder.bindStudyFromRequest(request()
				.body().asFormUrlEncoded());
		List<ValidationError> errorList = updatedStudy.validate();
		if (errorList != null) {
			failStudyEdit(loggedInUser, studyList, updatedStudy, errorList);
		}

		String oldDirName = study.getDirName();
		PersistanceUtils.updateStudysProperties(study, updatedStudy);
		renameStudyAssetsDir(study, loggedInUser, studyList, oldDirName);
		return redirect(routes.Studies.index(studyId, null));
	}

	private static void failStudyEdit(UserModel loggedInUser,
			List<StudyModel> studyList, StudyModel study,
			List<ValidationError> errorList) throws ResultException {
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		for (ValidationError error : errorList) {
			form.reject(error);
		}
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.EDIT_PROPERTIES);
		Call submitAction = routes.Studies.submitEdited(study.getId());
		ControllerUtils.throwEditStudyResultException(studyList, loggedInUser,
				form, Http.Status.BAD_REQUEST, breadcrumbs, submitAction,
				study.isLocked());
	}

	/**
	 * Ajax POST request
	 * 
	 * Swap the locked field of a study.
	 */
	@Transactional
	public static Result swapLock(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".swapLock: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		study.setLocked(!study.isLocked());
		study.merge();
		return ok(String.valueOf(study.isLocked()));
	}

	/**
	 * Ajax DELETE request
	 * 
	 * Remove a study
	 */
	@Transactional
	public static Result remove(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		PersistanceUtils.removeStudy(study);
		removeStudyAssetsDir(study);
		return ok();
	}

	/**
	 * Ajax request 
	 * 
	 * Clones a study.
	 */
	@Transactional
	public static Result cloneStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".cloneStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		StudyModel clone = new StudyModel(study);
		cloneStudyAssetsDir(study, clone);
		PersistanceUtils.addStudy(clone, loggedInUser);
		return ok();
	}

	@Transactional
	public static Result changeMembers(Long studyId) throws ResultException {
		return changeMembers(studyId, null, Http.Status.OK);
	}

	/**
	 * Shows a view with a form to change members of a study.
	 */
	@Transactional
	public static Result changeMembers(Long studyId, String errorMsg,
			int httpStatus) throws ResultException {
		Logger.info(CLASS_NAME + ".changeMembers: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
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

	/**
	 * POST request that handles changed members of a study.
	 */
	@Transactional
	public static Result submitChangedMembers(Long studyId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".submitChangedMembers: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		String[] checkedUsers = request().body().asFormUrlEncoded()
				.get(StudyModel.MEMBERS);
		persistCheckedUsers(studyId, study, checkedUsers);
		return redirect(routes.Studies.index(studyId, null));
	}

	private static void persistCheckedUsers(Long studyId, StudyModel study,
			String[] checkedUsers) throws ResultException {
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
	}

	/**
	 * Ajax POST request
	 * 
	 * Change the oder of components within a study.
	 */
	@Transactional
	public static Result changeComponentOrder(Long studyId, Long componentId,
			String direction) throws ResultException {
		Logger.info(CLASS_NAME + ".changeComponentOrder: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		switch (direction) {
		case COMPONENT_ORDER_UP:
			study.componentOrderMinusOne(component);
			break;
		case COMPONENT_ORDER_DOWN:
			study.componentOrderPlusOne(component);
			break;
		default:
			return badRequest(ErrorMessages.studyReorderUnknownDirection(
					direction, studyId));
		}
		// The actual change in order happens within the component model. The
		// study model we just have to refresh.
		study.refresh();

		return ok();
	}

	/**
	 * Actually shows the study. Uses JatosWorker. It redirects to
	 * Publix.startStudy() action.
	 */
	@Transactional
	public static Result showStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".showStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		session(JatosPublix.JATOS_SHOW, JatosPublix.SHOW_STUDY);
		String queryStr = "?" + JatosPublix.JATOS_WORKER_ID + "="
				+ loggedInUser.getWorker().getId();
		return redirect(controllers.publix.routes.PublixInterceptor.startStudy(
				study.getId()).url()
				+ queryStr);
	}

	/**
	 * Ajax request
	 * 
	 * Creates a ClosedStandaloneWorker and the URL that can be used for this
	 * kind of run.
	 */
	@Transactional
	public static Result createClosedStandaloneRun(Long studyId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".createClosedStandaloneRun: studyId "
				+ studyId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		JsonNode json = request().body().asJson();
		if (json == null) {
			String errorMsg = ErrorMessages
					.studyCreationOfStandaloneRunFailed(studyId);
			ControllerUtils.throwStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		String comment = json.findPath(ClosedStandaloneWorker.COMMENT).asText()
				.trim();
		ClosedStandaloneWorker worker = new ClosedStandaloneWorker(comment);
		checkWorker(studyId, worker);
		worker.persist();

		String url = ControllerUtils.getReferer()
				+ controllers.publix.routes.PublixInterceptor.startStudy(
						study.getId()).url() + "?"
				+ ClosedStandalonePublix.CLOSEDSTANDALONE_WORKER_ID + "="
				+ worker.getId();
		return ok(url);
	}

	/**
	 * Ajax request
	 * 
	 * Creates a TesterWorker and returns the URL that can be used for a tester
	 * run.
	 */
	@Transactional
	public static Result createTesterRun(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".createTesterRun: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		JsonNode json = request().body().asJson();
		if (json == null) {
			String errorMsg = ErrorMessages
					.studyCreationOfTesterRunFailed(studyId);
			ControllerUtils.throwStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		String comment = json.findPath(TesterWorker.COMMENT).asText().trim();
		TesterWorker worker = new TesterWorker(comment);
		checkWorker(studyId, worker);
		worker.persist();

		String url = ControllerUtils.getReferer()
				+ controllers.publix.routes.PublixInterceptor.startStudy(
						study.getId()).url() + "?" + TesterPublix.TESTER_ID
				+ "=" + worker.getId();
		return ok(url);
	}

	private static void checkWorker(Long studyId, Worker worker)
			throws ResultException {
		List<ValidationError> errorList = worker.validate();
		if (errorList != null && !errorList.isEmpty()) {
			String errorMsg = errorList.get(0).message();
			ControllerUtils.throwStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
	}

	/**
	 * Shows a view with the source code that is intended to be copied into
	 * Mechanical Turk.
	 */
	@Transactional
	public static Result showMTurkSourceCode(Long studyId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".showMTurkSourceCode: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		URL jatosURL = ControllerUtils.getRefererUrl();
		if (jatosURL == null) {
			String errorMsg = ErrorMessages.COULDNT_GENERATE_JATOS_URL;
			ControllerUtils.throwStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.MECHANICAL_TURK_HIT_LAYOUT_SOURCE_CODE);
		return ok(views.html.jatos.study.mTurkSourceCode.render(studyList,
				loggedInUser, breadcrumbs, null, study, jatosURL));
	}

	/**
	 * Ajax request
	 * 
	 * Returns all Components of the given study as JSON.
	 */
	@Transactional
	public static Result tableDataByStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
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

	/**
	 * Shows view that lists all Workers that did the given study.
	 */
	@Transactional
	public static Result workers(Long studyId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".workers: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
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

	/**
	 * Creates a study assets dir in the file system. It's a wrapper around the
	 * corresponding IOUtils method.
	 */
	private static void createStudyAssetsDir(UserModel loggedInUser,
			List<StudyModel> studyList, StudyModel study)
			throws ResultException {
		List<ValidationError> errorList;
		try {
			IOUtils.createStudyAssetsDir(study.getDirName());
		} catch (IOException e) {
			errorList = new ArrayList<>();
			errorList.add(new ValidationError(StudyModel.DIRNAME, e
					.getMessage()));
			failStudyCreate(loggedInUser, studyList, study, errorList);
		}
	}

	/**
	 * Renames study assets dir. It's a wrapper around the corresponding IOUtils
	 * method.
	 */
	private static void renameStudyAssetsDir(StudyModel study,
			UserModel loggedInUser, List<StudyModel> studyList,
			String oldDirName) throws ResultException {
		List<ValidationError> errorList;
		try {
			IOUtils.renameStudyAssetsDir(oldDirName, study.getDirName());
		} catch (IOException e) {
			errorList = new ArrayList<>();
			errorList.add(new ValidationError(StudyModel.DIRNAME, e
					.getMessage()));
			failStudyEdit(loggedInUser, studyList, study, errorList);
		}
	}

	/**
	 * Removes study assets dir. It's a wrapper around the corresponding IOUtils
	 * method.
	 */
	private static void removeStudyAssetsDir(StudyModel study)
			throws ResultException {
		try {
			IOUtils.removeStudyAssetsDir(study.getDirName());
		} catch (IOException e) {
			String errorMsg = e.getMessage();
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Copy study assets' dir and it's content to cloned study assets' dir. It's
	 * a wrapper around the corresponding IOUtils method.
	 */
	private static void cloneStudyAssetsDir(StudyModel study, StudyModel clone)
			throws ResultException {
		try {
			String destDirName = IOUtils.cloneStudyAssetsDirectory(study
					.getDirName());
			clone.setDirName(destDirName);
		} catch (IOException e) {
			ControllerUtils.throwAjaxResultException(e.getMessage(),
					Http.Status.INTERNAL_SERVER_ERROR);
		}
	}

}
