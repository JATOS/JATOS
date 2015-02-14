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
import models.workers.ClosedStandaloneWorker;
import models.workers.TesterWorker;
import models.workers.Worker;
import persistance.IComponentDao;
import persistance.IStudyDao;
import persistance.IStudyResultDao;
import persistance.IUserDao;
import persistance.workers.IWorkerDao;
import play.Logger;
import play.api.mvc.Call;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.Breadcrumbs;
import services.ComponentService;
import services.ErrorMessages;
import services.JatosGuiExceptionThrower;
import services.Messages;
import services.StudyService;
import services.UserService;
import services.WorkerService;
import utils.IOUtils;
import utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import common.JatosGuiAction;
import controllers.publix.closed_standalone.ClosedStandalonePublix;
import controllers.publix.jatos.JatosPublix;
import controllers.publix.tester.TesterPublix;
import exceptions.JatosGuiException;

/**
 * Controller for all actions regarding studies within the JATOS GUI.
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
@Singleton
public class Studies extends Controller {

	public static final String COMPONENT_POSITION_DOWN = "down";
	public static final String COMPONENT_POSITION_UP = "up";
	private static final String CLASS_NAME = Studies.class.getSimpleName();

	private final Messages messages;
	private final JsonUtils jsonUtils;
	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final ComponentService componentService;
	private final UserService userService;
	private final WorkerService workerService;
	private final IUserDao userDao;
	private final IStudyDao studyDao;
	private final IComponentDao componentDao;
	private final IStudyResultDao studyResultDao;
	private final IWorkerDao workerDao;

	@Inject
	Studies(Messages messages, IUserDao userDao,
			JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, ComponentService componentService,
			UserService userService, WorkerService workerService,
			IStudyDao studyDao, IComponentDao componentDao,
			JsonUtils jsonUtils, IStudyResultDao studyResultDao, IWorkerDao workerDao) {
		this.messages = messages;
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
		this.workerDao = workerDao;
	}

	/**
	 * Shows the index view with details regarding a study.
	 */
	@Transactional
	public Result index(Long studyId, String errorMsg, int httpStatus)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		Set<Worker> workerSet = workerService.retrieveWorkers(study);
		messages.error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study);
		String baseUrl = ControllerUtils.getReferer();
		int studyResultCount = studyResultDao.countByStudy(study);
		return status(httpStatus, views.html.jatos.study.index.render(
				studyList, loggedInUser, breadcrumbs, messages, study,
				workerSet, baseUrl, studyResultCount));
	}

	@Transactional
	public Result index(Long studyId, String errorMsg) throws JatosGuiException {
		return index(studyId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public Result index(Long studyId) throws JatosGuiException {
		return index(studyId, null, Http.Status.OK);
	}

	/**
	 * Shows a view with a form to create a new study.
	 */
	@Transactional
	public Result create() throws JatosGuiException {
		Logger.info(CLASS_NAME + ".create: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
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
	public Result submit() throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submit: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());

		StudyModel study = studyService.bindStudyFromRequest(request().body()
				.asFormUrlEncoded());
		List<ValidationError> errorList = study.validate();
		if (errorList != null) {
			failStudyCreate(loggedInUser, studyList, study, errorList);
		}

		studyDao.create(study, loggedInUser);
		createStudyAssetsDir(loggedInUser, studyList, study);
		return redirect(routes.Studies.index(study.getId(), null));
	}

	private void failStudyCreate(UserModel loggedInUser,
			List<StudyModel> studyList, StudyModel study,
			List<ValidationError> errorList) throws JatosGuiException {
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		Breadcrumbs breadcrumbs = Breadcrumbs
				.generateForHome(Breadcrumbs.NEW_STUDY);
		Call submitAction = routes.Studies.submit();
		jatosGuiExceptionThrower.throwEditStudy(studyList, loggedInUser, form,
				errorList, Http.Status.BAD_REQUEST, breadcrumbs, submitAction,
				false);
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
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

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
	public Result submitEdited(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		studyService.checkStandardForStudy(study, studyId, loggedInUser);
		studyService.checkStudyLocked(study);

		StudyModel updatedStudy = studyService.bindStudyFromRequest(request()
				.body().asFormUrlEncoded());
		List<ValidationError> errorList = updatedStudy.validate();
		if (errorList != null) {
			failStudyEdit(loggedInUser, studyList, updatedStudy, errorList);
		}

		String oldDirName = study.getDirName();
		studyDao.updateProperties(study, updatedStudy);
		renameStudyAssetsDir(study, loggedInUser, studyList, oldDirName);
		return redirect(routes.Studies.index(studyId, null));
	}

	private void failStudyEdit(UserModel loggedInUser,
			List<StudyModel> studyList, StudyModel study,
			List<ValidationError> errorList) throws JatosGuiException {
		Form<StudyModel> form = Form.form(StudyModel.class).fill(study);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.EDIT_PROPERTIES);
		Call submitAction = routes.Studies.submitEdited(study.getId());
		jatosGuiExceptionThrower.throwEditStudy(studyList, loggedInUser, form,
				errorList, Http.Status.BAD_REQUEST, breadcrumbs, submitAction,
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
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

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
		studyService.checkStandardForStudy(study, studyId, loggedInUser);
		studyService.checkStudyLocked(study);

		studyDao.remove(study);
		removeStudyAssetsDir(study);
		return ok();
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
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		StudyModel clone = new StudyModel(study);
		cloneStudyAssetsDir(study, clone);
		studyDao.create(clone, loggedInUser);
		return ok();
	}

	@Transactional
	public Result changeMembers(Long studyId) throws JatosGuiException {
		return changeMembers(studyId, null, Http.Status.OK);
	}

	/**
	 * Shows a view with a form to change members of a study.
	 */
	@Transactional
	public Result changeMembers(Long studyId, String errorMsg, int httpStatus)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".changeMembers: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		List<UserModel> userList = userDao.findAll();
		messages.error(errorMsg);
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
	public Result submitChangedMembers(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitChangedMembers: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		String[] checkedUsers = request().body().asFormUrlEncoded()
				.get(StudyModel.MEMBERS);
		persistCheckedUsers(studyId, study, checkedUsers);
		return redirect(routes.Studies.index(studyId, null));
	}

	private void persistCheckedUsers(Long studyId, StudyModel study,
			String[] checkedUsers) throws JatosGuiException {
		if (checkedUsers == null || checkedUsers.length < 1) {
			String errorMsg = ErrorMessages.STUDY_AT_LEAST_ONE_MEMBER;
			jatosGuiExceptionThrower.throwChangeMemberOfStudies(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		study.getMemberList().clear();
		for (String email : checkedUsers) {
			UserModel user = userDao.findByEmail(email);
			if (user != null) {
				studyDao.addMember(study, user);
			}
		}
	}

	/**
	 * Ajax POST request
	 * 
	 * Change the oder of components within a study.
	 */
	@Transactional
	public Result changeComponentOrder(Long studyId, Long componentId,
			String direction) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".changeComponentOrder: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		studyService.checkStandardForStudy(study, studyId, loggedInUser);
		studyService.checkStudyLocked(study);
		componentService.checkStandardForComponents(studyId, componentId,
				study, loggedInUser, component);

		switch (direction) {
		case COMPONENT_POSITION_UP:
			studyService.componentPositionMinusOne(study, component);
			break;
		case COMPONENT_POSITION_DOWN:
			studyService.componentPositionPlusOne(study, component);
			break;
		default:
			return badRequest(ErrorMessages.studyReorderUnknownDirection(
					direction, studyId));
		}
		// The actual change in order happens within the component model. The
		// study model we just have to refresh.
		studyDao.refresh(study);

		return ok();
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
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

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
	public Result createClosedStandaloneRun(Long studyId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".createClosedStandaloneRun: studyId "
				+ studyId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		JsonNode json = request().body().asJson();
		if (json == null) {
			String errorMsg = ErrorMessages
					.studyCreationOfStandaloneRunFailed(studyId);
			jatosGuiExceptionThrower.throwStudies(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		String comment = json.findPath(ClosedStandaloneWorker.COMMENT).asText()
				.trim();
		ClosedStandaloneWorker worker = new ClosedStandaloneWorker(comment);
		checkWorker(studyId, worker);
		workerDao.create(worker);

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
	public Result createTesterRun(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".createTesterRun: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		JsonNode json = request().body().asJson();
		if (json == null) {
			String errorMsg = ErrorMessages
					.studyCreationOfTesterRunFailed(studyId);
			jatosGuiExceptionThrower.throwStudies(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		String comment = json.findPath(TesterWorker.COMMENT).asText().trim();
		TesterWorker worker = new TesterWorker(comment);
		checkWorker(studyId, worker);
		workerDao.create(worker);

		String url = ControllerUtils.getReferer()
				+ controllers.publix.routes.PublixInterceptor.startStudy(
						study.getId()).url() + "?" + TesterPublix.TESTER_ID
				+ "=" + worker.getId();
		return ok(url);
	}

	private void checkWorker(Long studyId, Worker worker)
			throws JatosGuiException {
		List<ValidationError> errorList = worker.validate();
		if (errorList != null && !errorList.isEmpty()) {
			String errorMsg = errorList.get(0).message();
			jatosGuiExceptionThrower.throwStudies(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
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
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		URL jatosURL = null;
		try {
			jatosURL = ControllerUtils.getRefererUrl();
		} catch (MalformedURLException e) {
			String errorMsg = ErrorMessages.COULDNT_GENERATE_JATOS_URL;
			jatosGuiExceptionThrower.throwStudies(errorMsg,
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
	public Result tableDataByStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		studyService.checkStandardForStudy(study, studyId, loggedInUser);
		String dataAsJson = null;
		try {
			dataAsJson = jsonUtils.allComponentsForUI(study.getComponentList());
		} catch (IOException e) {
			String errorMsg = ErrorMessages.PROBLEM_GENERATING_JSON_DATA;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
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
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		messages.error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.WORKERS);
		return status(httpStatus, views.html.jatos.study.studysWorkers.render(
				studyList, loggedInUser, breadcrumbs, messages, study));
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

	/**
	 * Creates a study assets dir in the file system. It's a wrapper around the
	 * corresponding IOUtils method.
	 */
	private void createStudyAssetsDir(UserModel loggedInUser,
			List<StudyModel> studyList, StudyModel study)
			throws JatosGuiException {
		try {
			IOUtils.createStudyAssetsDir(study.getDirName());
		} catch (IOException e) {
			List<ValidationError> errorList = new ArrayList<>();
			errorList.add(new ValidationError(StudyModel.DIRNAME, e
					.getMessage()));
			failStudyCreate(loggedInUser, studyList, study, errorList);
		}
	}

	/**
	 * Renames study assets dir. It's a wrapper around the corresponding IOUtils
	 * method.
	 */
	private void renameStudyAssetsDir(StudyModel study, UserModel loggedInUser,
			List<StudyModel> studyList, String oldDirName)
			throws JatosGuiException {
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
	private void removeStudyAssetsDir(StudyModel study)
			throws JatosGuiException {
		try {
			IOUtils.removeStudyAssetsDir(study.getDirName());
		} catch (IOException e) {
			String errorMsg = e.getMessage();
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Copy study assets' dir and it's content to cloned study assets' dir. It's
	 * a wrapper around the corresponding IOUtils method.
	 */
	private void cloneStudyAssetsDir(StudyModel study, StudyModel clone)
			throws JatosGuiException {
		try {
			String destDirName = IOUtils.cloneStudyAssetsDirectory(study
					.getDirName());
			clone.setDirName(destDirName);
		} catch (IOException e) {
			jatosGuiExceptionThrower.throwAjax(e.getMessage(),
					Http.Status.INTERNAL_SERVER_ERROR);
		}
	}

}
