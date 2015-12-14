package controllers.gui;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import models.gui.StudyProperties;
import play.Logger;
import play.api.mvc.Call;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.ComponentService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.StudyService;
import services.gui.UserService;
import services.gui.WorkerService;
import utils.common.ControllerUtils;
import utils.common.IOUtils;
import utils.common.JsonUtils;

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
	private final BreadcrumbsService breadcrumbsService;
	private final UserDao userDao;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;
	private final StudyResultDao studyResultDao;
	private final ComponentResultDao componentResultDao;
	private final IOUtils ioUtils;

	@Inject
	Studies(UserDao userDao, JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, ComponentService componentService,
			UserService userService, WorkerService workerService,
			BreadcrumbsService breadcrumbsService, StudyDao studyDao,
			ComponentDao componentDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao,
			ComponentResultDao componentResultDao, IOUtils ioUtils) {
		this.userDao = userDao;
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.componentService = componentService;
		this.userService = userService;
		this.workerService = workerService;
		this.breadcrumbsService = breadcrumbsService;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
		this.jsonUtils = jsonUtils;
		this.studyResultDao = studyResultDao;
		this.componentResultDao = componentResultDao;
		this.ioUtils = ioUtils;
	}

	/**
	 * Shows the index view with details regarding a study.
	 */
	@Transactional
	public Result index(Long studyId, int httpStatus) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		Set<Worker> workerSet = workerService.retrieveWorkers(study);
		String breadcrumbs = breadcrumbsService.generateForStudy(study);
		String baseUrl = ControllerUtils.getReferer();
		int studyResultCount = studyResultDao.countByStudy(study);
		return status(httpStatus,
				views.html.gui.study.index.render(loggedInUser, breadcrumbs,
						study, workerSet, baseUrl, studyResultCount));
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
		User loggedInUser = userService.retrieveLoggedInUser();
		Form<StudyProperties> form = Form.form(StudyProperties.class)
				.fill(new StudyProperties());
		// It's a generic template for editing a study. We have to tell it the
		// submit action.
		Call submitAction = controllers.gui.routes.Studies.submit();
		String breadcrumbs = breadcrumbsService
				.generateForHome(BreadcrumbsService.NEW_STUDY);
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
		User loggedInUser = userService.retrieveLoggedInUser();

		Form<StudyProperties> form = Form.form(StudyProperties.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return failStudyCreate(loggedInUser, form);
		}
		StudyProperties studyProperties = form.get();

		try {
			ioUtils.createStudyAssetsDir(studyProperties.getDirName());
		} catch (IOException e) {
			form.reject(new ValidationError(StudyProperties.DIRNAME,
					e.getMessage()));
			return failStudyCreate(loggedInUser, form);
		}

		Study study = studyService.createStudy(loggedInUser, studyProperties);
		return redirect(controllers.gui.routes.Studies.index(study.getId()));
	}

	private Result failStudyCreate(User loggedInUser,
			Form<StudyProperties> form) {
		String breadcrumbs = breadcrumbsService
				.generateForHome(BreadcrumbsService.NEW_STUDY);
		Call submitAction = controllers.gui.routes.Studies.submit();
		return status(Http.Status.BAD_REQUEST, views.html.gui.study.edit
				.render(loggedInUser, breadcrumbs, submitAction, form, false));
	}
	
	/**
	 * Ajax GET request that gets the study properties as JSON.
	 */
	@Transactional
	public Result properties(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".properties: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);
		
		StudyProperties studyProperties = studyService.bindToProperties(study);
		return ok(JsonUtils.asJsonNode(studyProperties));
	}

	/**
	 * Ajax POST request of the edit form to change the properties of a study.
	 */
	@Transactional
	public Result submitEdited(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		Form<StudyProperties> form = Form.form(StudyProperties.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}
		StudyProperties studyProperties = form.get();
		try {
			studyService.renameStudyAssetsDir(study,
					studyProperties.getDirName());
		} catch (IOException e) {
			form.reject(new ValidationError(StudyProperties.DIRNAME,
					e.getMessage()));
			return badRequest(form.errorsAsJson());
		}

		studyService.updateStudy(study, studyProperties);
		return ok();
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
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
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
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		studyDao.remove(study);
		try {
			ioUtils.removeStudyAssetsDir(study.getDirName());
		} catch (IOException e) {
			String errorMsg = e.getMessage();
			return internalServerError(errorMsg);
		}
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
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		try {
			Study clone = studyService.clone(study);
			studyService.createStudy(loggedInUser, clone);
		} catch (IOException e) {
			jatosGuiExceptionThrower.throwAjax(e.getMessage(),
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok();
	}

	@Transactional
	public Result changeUsers(Long studyId) throws JatosGuiException {
		return changeUsers(studyId, Http.Status.OK);
	}

	/**
	 * Shows a view with a form to change users of a study.
	 */
	@Transactional
	public Result changeUsers(Long studyId, int httpStatus)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".changeUsers: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		List<User> userList = userDao.findAll();
		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.CHANGE_USERS);
		return status(httpStatus, views.html.gui.study.changeUsers
				.render(loggedInUser, breadcrumbs, study, userList));
	}

	/**
	 * POST request that handles changed users of a study.
	 */
	@Transactional
	public Result submitChangedUsers(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitChangedUser: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			Call call = controllers.gui.routes.Home.home();
			jatosGuiExceptionThrower.throwRedirect(e, call);
		}

		String[] checkedUsers = request().body().asFormUrlEncoded()
				.get(Study.USERS);
		try {
			studyService.exchangeUsers(study, checkedUsers);
		} catch (BadRequestException e) {
			RequestScopeMessaging.error(e.getMessage());
			Result result = changeUsers(study.getId(), Http.Status.BAD_REQUEST);
			throw new JatosGuiException(result, e.getMessage());
		}
		return redirect(controllers.gui.routes.Studies.index(studyId));
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
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
			componentService.checkStandardForComponents(studyId, componentId,
					component);
			studyService.changeComponentPosition(newPosition, study, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
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
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		session("jatos_run", "full_study");
		String startStudyUrl = "/publix/" + study.getId() + "/start?"
				+ "jatosWorkerId" + "=" + loggedInUser.getWorker().getId();
		return redirect(startStudyUrl);
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
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
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

		String url = ControllerUtils.getReferer() + "/publix/" + study.getId()
				+ "/start?" + "personalSingleWorkerId" + "=" + worker.getId();
		return ok(url);
	}

	/**
	 * Ajax request
	 * 
	 * Creates a PersonalMultipleWorker and returns the URL that can be used for
	 * a personal multiple run.
	 */
	@Transactional
	public Result createPersonalMultipleRun(Long studyId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".createPersonalMultipleRun: studyId "
				+ studyId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
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
		String comment = json.findPath(PersonalMultipleWorker.COMMENT).asText()
				.trim();
		PersonalMultipleWorker worker;
		try {
			worker = workerService.createPersonalMultipleWorker(comment);
		} catch (BadRequestException e) {
			return badRequest(e.getMessage());
		}

		String url = ControllerUtils.getReferer() + "/publix/" + study.getId()
				+ "/start?" + "personalMultipleId" + "=" + worker.getId();
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
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		URL jatosURL = null;
		try {
			jatosURL = ControllerUtils.getRefererUrl();
		} catch (MalformedURLException e) {
			String errorMsg = MessagesStrings.COULDNT_GENERATE_JATOS_URL;
			jatosGuiExceptionThrower.throwStudyIndex(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.MECHANICAL_TURK_HIT_LAYOUT_SOURCE_CODE);
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
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		List<Component> componentList = study.getComponentList();
		List<Integer> resultCountList = new ArrayList<>();
		componentList.forEach(component -> resultCountList
				.add(componentResultDao.countByComponent(component)));
		JsonNode dataAsJson = jsonUtils
				.allComponentsForUI(study.getComponentList(), resultCountList);
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
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		RequestScopeMessaging.error(errorMsg);
		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.WORKERS);
		return status(httpStatus, views.html.gui.study.studysWorkers
				.render(loggedInUser, breadcrumbs, study));
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

	private void checkStandardForStudy(Long studyId, Study study,
			User loggedInUser) throws JatosGuiException {
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}
	}

}
