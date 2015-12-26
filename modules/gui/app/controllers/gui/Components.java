package controllers.gui;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
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
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.ComponentService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.StudyService;
import services.gui.UserService;
import utils.common.JsonUtils;

/**
 * Controller that deals with all requests regarding Components within the JATOS
 * GUI.
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class Components extends Controller {

	public static final String EDIT_SUBMIT_NAME = "action";
	public static final String EDIT_SAVE = "save";
	public static final String EDIT_SAVE_AND_SHOW = "saveAndShow";
	private static final String CLASS_NAME = Components.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final ComponentService componentService;
	private final UserService userService;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;

	@Inject
	Components(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, ComponentService componentService,
			UserService userService, StudyDao studyDao,
			ComponentDao componentDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
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
	public Result showComponent(Long studyId, Long componentId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".showComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		User loggedInUser = userService.retrieveLoggedInUser();
		Study study = studyDao.findById(studyId);
		Component component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}
		try {
			componentService.checkStandardForComponents(studyId, componentId,
					component);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		if (component.getHtmlFilePath() == null
				|| component.getHtmlFilePath().trim().isEmpty()) {
			String errorMsg = MessagesStrings.htmlFilePathEmpty(componentId);
			jatosGuiExceptionThrower.throwStudyIndex(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		session("jatos_run", "single_component_start");
		session("run_component_id", componentId.toString());
		// Redirect to jatos-publix: start study
		String startComponentUrl = "/publix/" + study.getId() + "/start?"
				+ "jatosWorkerId" + "=" + loggedInUser.getWorker().getId();
		return redirect(startComponentUrl);
	}

	/**
	 * Ajax POST request: Handles the post request of the form to create a new
	 * Component.
	 */
	@Transactional
	public Result submitCreated(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitCreated: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStudyAndLocked(studyId, study, loggedInUser);

		Form<ComponentProperties> form = Form.form(ComponentProperties.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}
		ComponentProperties componentProperties = form.get();

		Component component = componentService.createComponent(study,
				componentProperties);
		return ok(component.getId().toString());
	}

	/**
	 * Ajax GET requests for getting the properties of a Component.
	 */
	@Transactional
	public Result properties(Long studyId, Long componentId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".properties: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			componentService.checkStandardForComponents(studyId, componentId,
					component);
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
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
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
	 * Request to change the property 'active' of a component. In future this
	 * action is intended for other properties too.
	 */
	@Transactional
	public Result changeProperty(Long studyId, Long componentId, Boolean active)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".changeProperty: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "active " + active
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		checkStudyAndLockedAndComponent(studyId, componentId, study,
				loggedInUser, component);

		if (active != null) {
			componentDao.changeActive(component, active);
		}
		return ok(String.valueOf(component.isActive()));
	}

	/**
	 * Ajax request
	 * 
	 * Clone a component.
	 */
	@Transactional
	public Result cloneComponent(Long studyId, Long componentId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".cloneComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		checkStudyAndLockedAndComponent(studyId, componentId, study,
				loggedInUser, component);

		Component clone = componentService.cloneWholeComponent(component);
		componentDao.create(study, clone);
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
		Logger.info(CLASS_NAME + ".remove: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		checkStudyAndLockedAndComponent(studyId, componentId, study,
				loggedInUser, component);

		componentDao.remove(study, component);
		RequestScopeMessaging
				.success(MessagesStrings.COMPONENT_DELETED_BUT_FILES_NOT);
		return ok(RequestScopeMessaging.getAsJson());
	}

	private void checkStudyAndLocked(Long studyId, Study study,
			User loggedInUser) throws JatosGuiException {
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}
	}

	private void checkStudyAndLockedAndComponent(Long studyId, Long componentId,
			Study study, User loggedInUser, Component component)
					throws JatosGuiException {
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
			componentService.checkStandardForComponents(studyId, componentId,
					component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}
	}
}
