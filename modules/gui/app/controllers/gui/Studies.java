package controllers.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import models.gui.StudyProperties;
import play.Logger;
import play.Logger.ALogger;
import play.data.Form;
import play.data.FormFactory;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.Checker;
import services.gui.JatosGuiExceptionThrower;
import services.gui.StudyService;
import services.gui.UserService;
import services.gui.WorkerService;
import utils.common.HttpUtils;
import utils.common.IOUtils;
import utils.common.JsonUtils;

/**
 * Controller for all actions regarding studies within the JATOS GUI.
 * 
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class Studies extends Controller {

	private static final ALogger LOGGER = Logger.of(Studies.class);

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final Checker checker;
	private final StudyService studyService;
	private final UserService userService;
	private final WorkerService workerService;
	private final BreadcrumbsService breadcrumbsService;
	private final UserDao userDao;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;
	private final StudyResultDao studyResultDao;
	private final ComponentResultDao componentResultDao;
	private final JsonUtils jsonUtils;
	private final IOUtils ioUtils;
	private final FormFactory formFactory;

	@Inject
	Studies(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
			StudyService studyService, UserService userService,
			WorkerService workerService, BreadcrumbsService breadcrumbsService,
			StudyDao studyDao, ComponentDao componentDao,
			StudyResultDao studyResultDao, UserDao userDao,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			IOUtils ioUtils, FormFactory formFactory) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.checker = checker;
		this.studyService = studyService;
		this.userService = userService;
		this.workerService = workerService;
		this.breadcrumbsService = breadcrumbsService;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
		this.studyResultDao = studyResultDao;
		this.componentResultDao = componentResultDao;
		this.userDao = userDao;
		this.jsonUtils = jsonUtils;
		this.ioUtils = ioUtils;
		this.formFactory = formFactory;
	}

	/**
	 * Shows the study view with details of a study components and so on.
	 */
	@Transactional
	@Authenticated
	public Result study(Long studyId, int httpStatus) throws JatosGuiException {
		LOGGER.info(".study: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		Set<Worker> workerSet = workerService.retrieveWorkers(study);
		String breadcrumbs = breadcrumbsService.generateForStudy(study);
		int studyResultCount = studyResultDao.countByStudy(study);
		return status(httpStatus,
				views.html.gui.study.study.render(loggedInUser, breadcrumbs,
						HttpUtils.isLocalhost(), study, workerSet,
						studyResultCount));
	}

	@Transactional
	@Authenticated
	public Result study(Long studyId) throws JatosGuiException {
		return study(studyId, Http.Status.OK);
	}

	/**
	 * Ajax POST request of the form to create a new study.
	 */
	@Transactional
	@Authenticated
	public Result submitCreated() {
		LOGGER.info(".submitCreated");
		User loggedInUser = userService.retrieveLoggedInUser();

		Form<StudyProperties> form = formFactory.form(StudyProperties.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}
		StudyProperties studyProperties = form.get();

		try {
			ioUtils.createStudyAssetsDir(studyProperties.getDirName());
		} catch (IOException e) {
			form.reject(new ValidationError(StudyProperties.DIRNAME,
					e.getMessage()));
			return badRequest(form.errorsAsJson());
		}

		Study study = studyService.createAndPersistStudy(loggedInUser,
				studyProperties);
		return ok(study.getId().toString());
	}

	/**
	 * Ajax GET request that gets the study properties as JSON.
	 */
	@Transactional
	@Authenticated
	public Result properties(Long studyId) throws JatosGuiException {
		LOGGER.info(".properties: studyId " + studyId);
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
	@Authenticated
	public Result submitEdited(Long studyId) throws JatosGuiException {
		LOGGER.info(".submitEdited: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		Form<StudyProperties> form = formFactory.form(StudyProperties.class)
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
	@Authenticated
	public Result toggleLock(Long studyId) throws JatosGuiException {
		LOGGER.info(".toggleLock: studyId " + studyId);
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
	@Authenticated
	public Result remove(Long studyId) throws JatosGuiException {
		LOGGER.info(".remove: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		studyService.remove(study);
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
	@Authenticated
	public Result cloneStudy(Long studyId) throws JatosGuiException {
		LOGGER.info(".cloneStudy: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		try {
			Study clone = studyService.clone(study);
			studyService.createAndPersistStudy(loggedInUser, clone);
		} catch (IOException e) {
			jatosGuiExceptionThrower.throwAjax(e.getMessage(),
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok();
	}

	/**
	 * Ajax GET request that gets all users and whether they are admin of this
	 * study as a JSON array.
	 */
	@Transactional
	@Authenticated
	public Result users(Long studyId) throws JatosGuiException {
		LOGGER.info(".users: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		List<User> userList = userDao.findAll();
		return ok(jsonUtils.usersForStudyUI(userList, study));
	}

	/**
	 * Ajax POST request that handles changed users of a study.
	 */
	@Transactional
	@Authenticated
	public Result submitChangedUsers(Long studyId) throws JatosGuiException {
		LOGGER.info(".submitChangedUser: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		String[] checkedUsers = (request().body().asFormUrlEncoded() != null)
				? request().body().asFormUrlEncoded().get(Study.USERS) : null;
		try {
			studyService.exchangeUsers(study, checkedUsers);
		} catch (BadRequestException e) {
			return badRequest(e.getMessage());
		}
		return ok();
	}

	/**
	 * Ajax POST request
	 * 
	 * Change the oder of components within a study.
	 */
	@Transactional
	@Authenticated
	public Result changeComponentOrder(Long studyId, Long componentId,
			String newPosition) throws JatosGuiException {
		LOGGER.info(".changeComponentOrder: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);
			checker.checkStandardForComponents(studyId, componentId, component);
			studyService.changeComponentPosition(newPosition, study, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok();
	}

	/**
	 * Actually runs the study with the given study ID, in the batch with the
	 * given batch ID while using a JatosWorker. It redirects to
	 * Publix.startStudy() action.
	 */
	@Transactional
	@Authenticated
	public Result runStudy(Long studyId, Long batchId)
			throws JatosGuiException {
		LOGGER.info(
				".runStudy: studyId " + studyId + ", " + "batch " + batchId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		session("jatos_run", "RUN_STUDY");
		String startStudyUrl = "/publix/" + study.getId() + "/start?"
				+ "batchId" + "=" + batchId + "&" + "jatosWorkerId" + "="
				+ loggedInUser.getWorker().getId();
		return redirect(startStudyUrl);
	}

	/**
	 * Ajax request
	 * 
	 * Returns all Components of the given study as JSON.
	 */
	@Transactional
	@Authenticated
	public Result tableDataByStudy(Long studyId) throws JatosGuiException {
		LOGGER.info(".tableDataByStudy: studyId " + studyId);
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
	@Authenticated
	public Result workers(Long studyId, String errorMsg, int httpStatus)
			throws JatosGuiException {
		LOGGER.info(".workers: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStandardForStudy(studyId, study, loggedInUser);

		RequestScopeMessaging.error(errorMsg);
		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.WORKER_SETUP);
		return status(httpStatus, views.html.gui.study.studysWorkers.render(
				loggedInUser, breadcrumbs, HttpUtils.isLocalhost(), study));
	}

	@Transactional
	@Authenticated
	public Result workers(Long studyId, String errorMsg)
			throws JatosGuiException {
		return workers(studyId, errorMsg, Http.Status.OK);
	}

	@Transactional
	@Authenticated
	public Result workers(Long studyId) throws JatosGuiException {
		return workers(studyId, null, Http.Status.OK);
	}

	private void checkStandardForStudy(Long studyId, Study study,
			User loggedInUser) throws JatosGuiException {
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}
	}

}
