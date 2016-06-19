package controllers.gui;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiExceptionAction.GuiException;
import controllers.gui.actionannotations.GuiLoggingAction.GuiLogging;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.gui.ComponentProperties;
import play.Logger;
import play.Logger.ALogger;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.Checker;
import services.gui.ComponentService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.UserService;
import utils.common.JsonUtils;

/**
 * Controller that deals with all requests regarding Components within the JATOS
 * GUI.
 * 
 * @author Kristian Lange
 */
@GuiException
@GuiLogging
@Authenticated
@Singleton
public class Components extends Controller {

	private static final ALogger LOGGER = Logger.of(Components.class);

	public static final String EDIT_SUBMIT_NAME = "action";
	public static final String EDIT_SAVE = "save";
	public static final String EDIT_SAVE_AND_RUN = "saveAndRun";

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final Checker checker;
	private final ComponentService componentService;
	private final UserService userService;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;

	@Inject
	Components(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			Checker checker, ComponentService componentService,
			UserService userService, StudyDao studyDao,
			ComponentDao componentDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.checker = checker;
		this.componentService = componentService;
		this.userService = userService;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
	}

	/**
	 * Actually shows a single component. It uses a JatosWorker and redirects to
	 * Publix.startStudy().
	 */
	@Transactional
	public Result runComponent(Long studyId, Long componentId, Long batchId)
			throws JatosGuiException {
		LOGGER.info(".runComponent: studyId " + studyId + ", " + "componentId "
				+ componentId + ", " + "batch " + batchId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Study study = studyDao.findById(studyId);
		Component component = componentDao.findById(componentId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}
		try {
			checker.checkStandardForComponents(studyId, componentId, component);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwStudy(e, studyId);
		}

		if (component.getHtmlFilePath() == null
				|| component.getHtmlFilePath().trim().isEmpty()) {
			String errorMsg = MessagesStrings.htmlFilePathEmpty(componentId);
			jatosGuiExceptionThrower.throwStudy(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		session("jatos_run", "single_component_start");
		session("run_component_id", componentId.toString());
		// Redirect to jatos-publix: start study
		String startComponentUrl = "/publix/" + study.getId() + "/start?"
				+ "batchId" + "=" + batchId + "&" + "jatosWorkerId" + "="
				+ loggedInUser.getWorker().getId();
		return redirect(startComponentUrl);
	}

	/**
	 * Ajax POST request: Handles the post request of the form to create a new
	 * Component.
	 */
	@Transactional
	public Result submitCreated(Long studyId) throws JatosGuiException {
		LOGGER.info(".submitCreated: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStudyAndLocked(studyId, study, loggedInUser);

		Form<ComponentProperties> form = Form.form(ComponentProperties.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}
		ComponentProperties componentProperties = form.get();

		Component component = componentService.createAndPersistComponent(study,
				componentProperties);
		return ok(component.getId().toString());
	}

	/**
	 * Ajax GET requests for getting the properties of a Component.
	 */
	@Transactional
	public Result properties(Long studyId, Long componentId)
			throws JatosGuiException {
		LOGGER.info(".properties: studyId " + studyId + ", " + "componentId "
				+ componentId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForComponents(studyId, componentId, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		ComponentProperties p = componentService.bindToProperties(component);
		return ok(JsonUtils.asJsonNode(p));
	}

	/**
	 * Handles the post of the edit form.
	 */
	@Transactional
	public Result submitEdited(Long studyId, Long componentId)
			throws JatosGuiException {
		LOGGER.info(".submitEdited: studyId " + studyId + ", " + "componentId "
				+ componentId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		checkStudyAndLockedAndComponent(studyId, componentId, study,
				loggedInUser, component);

		Form<ComponentProperties> form = Form.form(ComponentProperties.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}
		ComponentProperties componentProperties = form.get();
		componentService.updateComponentAfterEdit(component,
				componentProperties);
		try {
			componentService.renameHtmlFilePath(component,
					componentProperties.getHtmlFilePath());
		} catch (IOException e) {
			form.reject(new ValidationError(ComponentProperties.HTML_FILE_PATH,
					e.getMessage()));
			return badRequest(form.errorsAsJson());
		}
		return ok(component.getId().toString());
	}

	/**
	 * Ajax POST
	 * 
	 * Request to change the property 'active' of a component.
	 */
	@Transactional
	public Result toggleActive(Long studyId, Long componentId, Boolean active)
			throws JatosGuiException {
		LOGGER.info(".toggleActive: studyId " + studyId + ", " + "componentId "
				+ componentId + ", " + "active " + active);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		checkStudyAndLockedAndComponent(studyId, componentId, study,
				loggedInUser, component);

		if (active != null) {
			componentDao.changeActive(component, active);
		}
		return ok(JsonUtils.asJsonNode(component.isActive()));
	}

	/**
	 * Ajax request
	 * 
	 * Clone a component.
	 */
	@Transactional
	public Result cloneComponent(Long studyId, Long componentId)
			throws JatosGuiException {
		LOGGER.info(".cloneComponent: studyId " + studyId + ", "
				+ "componentId " + componentId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		checkStudyAndLockedAndComponent(studyId, componentId, study,
				loggedInUser, component);

		Component clone = componentService.cloneWholeComponent(component);
		componentService.createAndPersistComponent(study, clone);
		return ok(RequestScopeMessaging.getAsJson());
	}

	/**
	 * Ajax request
	 * 
	 * Remove a component.
	 */
	@Transactional
	public Result remove(Long studyId, Long componentId)
			throws JatosGuiException {
		LOGGER.info(".remove: studyId " + studyId + ", " + "componentId "
				+ componentId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		checkStudyAndLockedAndComponent(studyId, componentId, study,
				loggedInUser, component);

		componentService.remove(component);
		RequestScopeMessaging
				.success(MessagesStrings.COMPONENT_DELETED_BUT_FILES_NOT);
		return ok(RequestScopeMessaging.getAsJson());
	}

	private void checkStudyAndLocked(Long studyId, Study study,
			User loggedInUser) throws JatosGuiException {
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudy(e, studyId);
		}
	}

	private void checkStudyAndLockedAndComponent(Long studyId, Long componentId,
			Study study, User loggedInUser, Component component)
			throws JatosGuiException {
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);
			checker.checkStandardForComponents(studyId, componentId, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudy(e, studyId);
		}
	}
}
