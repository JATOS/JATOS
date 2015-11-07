package controllers.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Component;
import models.common.Study;
import models.common.User;
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
import utils.common.ControllerUtils;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import daos.ComponentDao;
import daos.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;

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
	public static final String EDIT_SUBMIT = "Submit";
	public static final String EDIT_SUBMIT_AND_SHOW = "Submit & Show";
	private static final String CLASS_NAME = Components.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final ComponentService componentService;
	private final UserService userService;
	private final BreadcrumbsService breadcrumbsService;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;

	@Inject
	Components(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, ComponentService componentService,
			UserService userService, BreadcrumbsService breadcrumbsService,
			StudyDao studyDao, ComponentDao componentDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.componentService = componentService;
		this.userService = userService;
		this.breadcrumbsService = breadcrumbsService;
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
		String startComponentUrl = "/publix/" + study.getId() + "/"
				+ component.getId() + "/start?" + "jatosWorkerId" + "="
				+ loggedInUser.getWorker().getId();
		return redirect(startComponentUrl);
	}

	/**
	 * Shows a view with a form to create a new Component.
	 */
	@Transactional
	public Result create(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".create: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStudyAndLocked(studyId, study, loggedInUser);

		Form<Component> form = Form.form(Component.class);
		Call submitAction = controllers.gui.routes.Components.submit(studyId);
		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.NEW_COMPONENT);
		return ok(views.html.gui.component.edit.render(loggedInUser,
				breadcrumbs, submitAction, form, study));
	}

	/**
	 * Handles the post request of the form to create a new Component.
	 */
	@Transactional
	public Result submit(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submit: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		checkStudyAndLocked(studyId, study, loggedInUser);

		Component component = componentService
				.bindComponentFromRequest(request().body().asFormUrlEncoded());
		List<ValidationError> errorList = component.validate();
		if (errorList != null) {
			Call submitAction = controllers.gui.routes.Components
					.submit(studyId);
			String breadcrumbs = breadcrumbsService.generateForStudy(study,
					BreadcrumbsService.NEW_COMPONENT);
			Form<Component> form = Form.form(Component.class).fill(component);
			errorList.forEach(form::reject);
			return status(Http.Status.BAD_REQUEST,
					views.html.gui.component.edit.render(loggedInUser,
							breadcrumbs, submitAction, form, study));
		}

		componentDao.create(study, component);
		return redirectAfterEdit(studyId, component.getId(), study);
	}

	/**
	 * Shows a view with a form to edit the properties of a Component.
	 */
	@Transactional
	public Result edit(Long studyId, Long componentId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".edit: studyId " + studyId + ", "
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
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		if (study.isLocked()) {
			RequestScopeMessaging.warning(MessagesStrings.STUDY_IS_LOCKED);
		}
		Form<Component> form = Form.form(Component.class).fill(component);
		Call submitAction = controllers.gui.routes.Components.submitEdited(
				studyId, componentId);
		String breadcrumbs = breadcrumbsService.generateForComponent(study,
				component, BreadcrumbsService.EDIT_PROPERTIES);
		return ok(views.html.gui.component.edit.render(loggedInUser,
				breadcrumbs, submitAction, form, study));
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

		Component editedComponent = componentService
				.bindComponentFromRequest(request().body().asFormUrlEncoded());
		List<ValidationError> errorList = editedComponent.validate();
		if (errorList != null) {
			return showEditAfterError(component, editedComponent, loggedInUser,
					errorList, Http.Status.BAD_REQUEST, study);
		}

		componentService.updateComponentAfterEdit(component, editedComponent);
		try {
			componentService.renameHtmlFilePath(component,
					editedComponent.getHtmlFilePath());
		} catch (IOException e) {
			errorList = new ArrayList<>();
			errorList.add(new ValidationError(Component.HTML_FILE_PATH, e
					.getMessage()));
			return showEditAfterError(component, editedComponent, loggedInUser,
					errorList, Http.Status.BAD_REQUEST, study);
		}

		return redirectAfterEdit(studyId, componentId, study);
	}

	private Result showEditAfterError(Component component,
			Component editedComponent, User loggedInUser,
			List<ValidationError> errorList, int httpStatus, Study study) {
		editedComponent.setId(component.getId());
		editedComponent.setUuid(component.getUuid());
		Call submitAction = controllers.gui.routes.Components.submitEdited(
				study.getId(), editedComponent.getId());
		String breadcrumbs = breadcrumbsService.generateForComponent(study,
				editedComponent, BreadcrumbsService.EDIT_PROPERTIES);
		Form<Component> form = Form.form(Component.class).fill(editedComponent);

		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			errorList.forEach(form::reject);
			return status(httpStatus, views.html.gui.component.edit.render(
					loggedInUser, breadcrumbs, submitAction, form, study));
		}
	}

	private Result redirectAfterEdit(Long studyId, Long componentId, Study study) {
		// Check which submit button was pressed: "Submit" or "Submit & Show".
		String[] postAction = request().body().asFormUrlEncoded()
				.get(EDIT_SUBMIT_NAME);
		if (postAction[0].equals(EDIT_SUBMIT_AND_SHOW)) {
			return redirect(controllers.gui.routes.Components.showComponent(
					studyId, componentId));
		} else {
			return redirect(controllers.gui.routes.Studies.index(study.getId()));
		}
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

	private void checkStudyAndLockedAndComponent(Long studyId,
			Long componentId, Study study, User loggedInUser,
			Component component) throws JatosGuiException {
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
