package controllers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.PersonalSingleWorker;
import models.workers.MTWorker;
import models.workers.PersonalMultipleWorker;
import models.workers.Worker;
import persistance.ComponentDao;
import persistance.StudyDao;
import persistance.StudyResultDao;
import persistance.UserDao;
import play.Logger;
import play.api.mvc.Call;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import publix.controllers.jatos.JatosPublix;
import publix.controllers.personal_multiple.PersonalMultiplePublix;
import publix.controllers.personal_single.PersonalSinglePublix;
import services.Breadcrumbs;
import services.ComponentService;
import services.JatosGuiExceptionThrower;
import services.MessagesStrings;
import services.StudyService;
import services.UserService;
import services.WorkerService;
import utils.ControllerUtils;
import utils.IOUtils;
import utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import common.RequestScopeMessaging;
import controllers.actionannotations.AuthenticationAction.Authenticated;
import controllers.actionannotations.JatosGuiAction.JatosGui;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.JatosGuiException;

/**
 * Controller for all actions regarding studies within the JATOS GUI.
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class Studies extends Controller {

	private static final String CLASS_NAME = Studies.class.getSimpleName();

	private final JsonUtils jsonUtils;
	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final ComponentService componentService;
	private final UserService userService;
	private final WorkerService workerService;
	private final UserDao userDao;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;
	private final StudyResultDao studyResultDao;

	@Inject
	Studies(UserDao userDao, JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, ComponentService componentService,
			UserService userService, WorkerService workerService,
			StudyDao studyDao, ComponentDao componentDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao) {
		this.userDao = userDao;
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.componentService = componentService;
		this.userService = userService;
		this.workerService = workerService;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
		this.jsonUtils = jsonUtils;
		this.studyResultDao = studyResultDao;
	}

	/**
	 * Shows the index view with details regarding a study.
	 */
	@Transactional
	public Result index(Long studyId, int httpStatus) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		Set<Worker> workerSet = workerService.retrieveWorkers(study);
		String breadcrumbs = Breadcrumbs.generateForStudy(study);
		String baseUrl = ControllerUtils.getReferer();
		int studyResultCount = studyResultDao.countByStudy(study);
		return status(httpStatus, views.html.gui.study.index.render(
				loggedInUser, breadcrumbs, study, workerSet, baseUrl,
				studyResultCount));
	}

	@Transactional
	public Result index(Long studyId) throws JatosGuiException {
		return index(studyId, Http.Status.OK);
	}

	/**
	 * Shows a view with a form to create a new study.
	 */
	@Transactional
	public Result create() {
		Logger.info(CLASS_NAME + ".create: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		StudyModel study = new StudyModel();
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		// It's a generic template for editing a study. We have to tell it the
		// submit action.
		Call submitAction = controllers.routes.Studies.submit();
		String breadcrumbs = Breadcrumbs.generateForHome(Breadcrumbs.NEW_STUDY);
		return ok(views.html.gui.study.edit.render(loggedInUser, breadcrumbs,
				submitAction, form, false));
	}

	/**
	 * POST request of the form to create a new study.
	 */
	@Transactional
	public Result submit() {
		Logger.info(CLASS_NAME + ".submit: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		StudyModel study = studyService.bindStudyFromRequest(request().body()
				.asFormUrlEncoded());
		List<ValidationError> errorList = study.validate();
		if (errorList != null) {
			return failStudyCreate(loggedInUser, study, errorList);
		}

		studyDao.create(study, loggedInUser);

		try {
			IOUtils.createStudyAssetsDir(study.getDirName());
		} catch (IOException e) {
			errorList = new ArrayList<>();
			errorList.add(new ValidationError(StudyModel.DIRNAME, e
					.getMessage()));
			return failStudyCreate(loggedInUser, study, errorList);
		}

		return redirect(controllers.routes.Studies.index(study.getId()));
	}

	private Result failStudyCreate(UserModel loggedInUser, StudyModel study,
			List<ValidationError> errorList) {
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		String breadcrumbs = Breadcrumbs.generateForHome(Breadcrumbs.NEW_STUDY);
		Call submitAction = controllers.routes.Studies.submit();
		return showEditStudyAfterError(loggedInUser, form, errorList,
				Http.Status.BAD_REQUEST, breadcrumbs, submitAction, false);
	}

	private Result showEditStudyAfterError(UserModel loggedInUser,
			Form<StudyModel> form, List<ValidationError> errorList,
			int httpStatus, String breadcrumbs, Call submitAction,
			boolean studyIsLocked) {
		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			if (errorList != null) {
				errorList.forEach(form::reject);
			}
			return status(httpStatus, views.html.gui.study.edit.render(
					loggedInUser, breadcrumbs, submitAction, form,
					studyIsLocked));
		}
	}

	/**
	 * Shows a form to edit the study properties.
	 */
	@Transactional
	public Result edit(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".edit: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		if (study.isLocked()) {
			RequestScopeMessaging.warning(MessagesStrings.STUDY_IS_LOCKED);
		}
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		Call submitAction = controllers.routes.Studies.submitEdited(study
				.getId());
		String breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.EDIT_PROPERTIES);
		return ok(views.html.gui.study.edit.render(loggedInUser, breadcrumbs,
				submitAction, form, study.isLocked()));
	}

	/**
	 * POST request of the edit form to change the properties of a study.
	 */
	@Transactional
	public Result submitEdited(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			Call call = controllers.routes.Home.home();
			jatosGuiExceptionThrower.throwRedirect(e, call);
		}
		try {
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException e) {
			Call call = controllers.routes.Studies.index(studyId);
			jatosGuiExceptionThrower.throwRedirect(e, call);
		}

		StudyModel updatedStudy = studyService.bindStudyFromRequest(request()
				.body().asFormUrlEncoded());
		List<ValidationError> errorList = updatedStudy.validate();
		if (errorList != null) {
			updatedStudy.setId(studyId);
			updatedStudy.setUuid(study.getUuid());
			return failStudyEdit(loggedInUser, updatedStudy,
					errorList);
		}

		studyService.updateStudy(study, updatedStudy);
		try {
			studyService.renameStudyAssetsDir(study, updatedStudy.getDirName());
		} catch (IOException e) {
			errorList = new ArrayList<>();
			errorList.add(new ValidationError(StudyModel.DIRNAME, e
					.getMessage()));
			return failStudyEdit(loggedInUser, study, errorList);
		}
		return redirect(controllers.routes.Studies.index(studyId));
	}

	private Result failStudyEdit(UserModel loggedInUser, StudyModel study,
			List<ValidationError> errorList) {
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		String breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.EDIT_PROPERTIES);
		Call submitAction = controllers.routes.Studies.submitEdited(study
				.getId());
		return showEditStudyAfterError(loggedInUser, form, errorList,
				Http.Status.BAD_REQUEST, breadcrumbs, submitAction,
				study.isLocked());
	}

	/**
	 * Ajax POST request
	 * 
	 * Swap the locked field of a study.
	 */
	@Transactional
	public Result swapLock(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".swapLock: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		study.setLocked(!study.isLocked());
		studyDao.update(study);
		return ok(String.valueOf(study.isLocked()));
	}

	/**
	 * Ajax DELETE request
	 * 
	 * Remove a study
	 */
	@Transactional
	public Result remove(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".remove: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		studyDao.remove(study);
		try {
			IOUtils.removeStudyAssetsDir(study.getDirName());
		} catch (IOException e) {
			String errorMsg = e.getMessage();
			return internalServerError(errorMsg);
		}
		return ok().as("text/html");
	}

	/**
	 * Ajax request
	 * 
	 * Clones a study.
	 */
	@Transactional
	public Result cloneStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".cloneStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		try {
			studyService.cloneStudy(study, loggedInUser);
		} catch (IOException e) {
			jatosGuiExceptionThrower.throwAjax(e.getMessage(),
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok().as("text/html");
	}

	@Transactional
	public Result changeMembers(Long studyId) throws JatosGuiException {
		return changeMembers(studyId, Http.Status.OK);
	}

	/**
	 * Shows a view with a form to change members of a study.
	 */
	@Transactional
	public Result changeMembers(Long studyId, int httpStatus)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".changeMembers: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		List<UserModel> userList = userDao.findAll();
		String breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.CHANGE_MEMBERS);
		return status(httpStatus, views.html.gui.study.changeMembers.render(
				loggedInUser, breadcrumbs, study, userList));
	}

	/**
	 * POST request that handles changed members of a study.
	 */
	@Transactional
	public Result submitChangedMembers(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitChangedMembers: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			Call call = controllers.routes.Home.home();
			jatosGuiExceptionThrower.throwRedirect(e, call);
		}

		String[] checkedUsers = request().body().asFormUrlEncoded()
				.get(StudyModel.MEMBERS);
		try {
			studyService.exchangeMembers(study, checkedUsers);
		} catch (BadRequestException e) {
			RequestScopeMessaging.error(e.getMessage());
			Result result = changeMembers(study.getId(),
					Http.Status.BAD_REQUEST);
			throw new JatosGuiException(result, e.getMessage());
		}
		return redirect(controllers.routes.Studies.index(studyId));
	}

	/**
	 * Ajax POST request
	 * 
	 * Change the oder of components within a study.
	 */
	@Transactional
	public Result changeComponentOrder(Long studyId, Long componentId,
			String newPosition) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".changeComponentOrder: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
			componentService.checkStandardForComponents(studyId, componentId,
					component);
			studyService.changeComponentPosition(newPosition, study, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok().as("text/html");
	}

	/**
	 * Actually shows the study. Uses JatosWorker. It redirects to
	 * Publix.startStudy() action.
	 */
	@Transactional
	public Result showStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".showStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		String queryStr = "?" + JatosPublix.JATOS_WORKER_ID + "="
				+ loggedInUser.getWorker().getId();
		return redirect(publix.controllers.routes.PublixInterceptor.startStudy(
				study.getId()).url()
				+ queryStr);
	}

	/**
	 * Ajax request
	 * 
	 * Creates a PersonalSingleWorker and the URL that can be used for this kind
	 * of run.
	 */
	@Transactional
	public Result createPersonalSingleRun(Long studyId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".createPersonalSingleRun: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		JsonNode json = request().body().asJson();
		if (json == null) {
			String errorMsg = MessagesStrings
					.studyCreationOfPersonalSingleRunFailed(studyId);
			return badRequest(errorMsg);
		}
		String comment = json.findPath(PersonalSingleWorker.COMMENT).asText()
				.trim();
		PersonalSingleWorker worker;
		try {
			worker = workerService.createPersonalSingleWorker(comment);
		} catch (BadRequestException e) {
			return badRequest(e.getMessage());
		}

		String url = ControllerUtils.getReferer()
				+ publix.controllers.routes.PublixInterceptor.startStudy(
						study.getId()).url() + "?"
				+ PersonalSinglePublix.PERSONALSINGLE_WORKER_ID + "="
				+ worker.getId();
		return ok(url);
	}

	/**
	 * Ajax request
	 * 
	 * Creates a PersonalMultipleWorker and returns the URL that can be used for a personal
	 * multiple run.
	 */
	@Transactional
	public Result createPersonalMultipleRun(Long studyId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".createPersonalMultipleRun: studyId "
				+ studyId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		JsonNode json = request().body().asJson();
		if (json == null) {
			String errorMsg = MessagesStrings
					.studyCreationOfPersonalMultipleRunFailed(studyId);
			return badRequest(errorMsg);
		}
		String comment = json.findPath(PersonalMultipleWorker.COMMENT).asText().trim();
		PersonalMultipleWorker worker;
		try {
			worker = workerService.createPersonalMultipleWorker(comment);
		} catch (BadRequestException e) {
			return badRequest(e.getMessage());
		}

		String url = ControllerUtils.getReferer()
				+ publix.controllers.routes.PublixInterceptor.startStudy(
						study.getId()).url() + "?"
				+ PersonalMultiplePublix.PERSONAL_MULTIPLE_ID + "=" + worker.getId();
		return ok(url);
	}

	/**
	 * Shows a view with the source code that is intended to be copied into
	 * Mechanical Turk.
	 */
	@Transactional
	public Result showMTurkSourceCode(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".showMTurkSourceCode: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		URL jatosURL = null;
		try {
			jatosURL = ControllerUtils.getRefererUrl();
		} catch (MalformedURLException e) {
			String errorMsg = MessagesStrings.COULDNT_GENERATE_JATOS_URL;
			jatosGuiExceptionThrower.throwStudyIndex(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		if (!study.hasAllowedWorker(MTWorker.WORKER_TYPE)) {
			RequestScopeMessaging
					.warning(MessagesStrings.MTWORKER_ALLOWANCE_MISSING);
		}
		String breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.MECHANICAL_TURK_HIT_LAYOUT_SOURCE_CODE);
		return ok(views.html.gui.study.mTurkSourceCode.render(loggedInUser,
				breadcrumbs, study, jatosURL));
	}

	/**
	 * Ajax request
	 * 
	 * Returns all Components of the given study as JSON.
	 */
	@Transactional
	public Result tableDataByStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		JsonNode dataAsJson = jsonUtils.allComponentsForUI(study.getComponentList());
		return ok(dataAsJson);
	}

	/**
	 * Shows view that lists all Workers that did the given study.
	 */
	@Transactional
	public Result workers(Long studyId, String errorMsg, int httpStatus)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".workers: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		RequestScopeMessaging.error(errorMsg);
		String breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.WORKERS);
		return status(httpStatus, views.html.gui.study.studysWorkers.render(
				loggedInUser, breadcrumbs, study));
	}

	@Transactional
	public Result workers(Long studyId, String errorMsg)
			throws JatosGuiException {
		return workers(studyId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public Result workers(Long studyId) throws JatosGuiException {
		return workers(studyId, null, Http.Status.OK);
	}

	private void checkStandardForStudy(Long studyId, StudyModel study,
			UserModel loggedInUser) throws JatosGuiException {
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}
	}

}
