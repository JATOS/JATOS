package controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import persistance.ComponentDao;
import persistance.StudyDao;
import play.Logger;
import play.api.mvc.Call;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import publix.controllers.jatos.JatosPublix;
import services.BreadcrumbsService;
import services.ComponentService;
import services.JatosGuiExceptionThrower;
import services.MessagesStrings;
import services.StudyService;
import services.UserService;
import utils.ControllerUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import common.RequestScopeMessaging;

import controllers.actionannotations.AuthenticationAction.Authenticated;
import controllers.actionannotations.JatosGuiAction.JatosGui;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.JatosGuiException;

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
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		StudyModel study = studyDao.findById(studyId);
		ComponentModel component = componentDao.findById(componentId);
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
		session(JatosPublix.JATOS_RUN, JatosPublix.RUN_COMPONENT_START);
		session(JatosPublix.RUN_COMPONENT_ID, componentId.toString());
		String queryStr = "?" + JatosPublix.JATOS_WORKER_ID + "="
				+ loggedInUser.getWorker().getId();
		return redirect(publix.controllers.routes.PublixInterceptor.startStudy(
				studyId).url()
				+ queryStr);
	}

	/**
	 * Shows a view with a form to create a new Component.
	 */
	@Transactional
	public Result create(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".create: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		checkStudyAndLocked(studyId, study, loggedInUser);

		Form<ComponentModel> form = Form.form(ComponentModel.class);
		Call submitAction = controllers.routes.Components.submit(studyId);
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		checkStudyAndLocked(studyId, study, loggedInUser);

		ComponentModel component = componentService
				.bindComponentFromRequest(request().body().asFormUrlEncoded());
		List<ValidationError> errorList = component.validate();
		if (errorList != null) {
			Call submitAction = controllers.routes.Components.submit(studyId);
			String breadcrumbs = breadcrumbsService.generateForStudy(study,
					BreadcrumbsService.NEW_COMPONENT);
			Form<ComponentModel> form = Form.form(ComponentModel.class).fill(
					component);
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
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
		Form<ComponentModel> form = Form.form(ComponentModel.class).fill(
				component);
		Call submitAction = controllers.routes.Components.submitEdited(studyId,
				componentId);
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		checkStudyAndLockedAndComponent(studyId, componentId, study,
				loggedInUser, component);

		ComponentModel editedComponent = componentService
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
			errorList.add(new ValidationError(ComponentModel.HTML_FILE_PATH, e
					.getMessage()));
			return showEditAfterError(component, editedComponent, loggedInUser,
					errorList, Http.Status.BAD_REQUEST, study);
		}

		return redirectAfterEdit(studyId, componentId, study);
	}

	private Result showEditAfterError(ComponentModel component,
			ComponentModel editedComponent, UserModel loggedInUser,
			List<ValidationError> errorList, int httpStatus, StudyModel study) {
		editedComponent.setId(component.getId());
		editedComponent.setUuid(component.getUuid());
		Call submitAction = controllers.routes.Components.submitEdited(
				study.getId(), editedComponent.getId());
		String breadcrumbs = breadcrumbsService.generateForComponent(study,
				editedComponent, BreadcrumbsService.EDIT_PROPERTIES);
		Form<ComponentModel> form = Form.form(ComponentModel.class).fill(
				editedComponent);

		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			errorList.forEach(form::reject);
			return status(httpStatus, views.html.gui.component.edit.render(
					loggedInUser, breadcrumbs, submitAction, form, study));
		}
	}

	private Result redirectAfterEdit(Long studyId, Long componentId,
			StudyModel study) {
		// Check which submit button was pressed: "Submit" or "Submit & Show".
		String[] postAction = request().body().asFormUrlEncoded()
				.get(EDIT_SUBMIT_NAME);
		if (postAction[0].equals(EDIT_SUBMIT_AND_SHOW)) {
			return redirect(controllers.routes.Components.showComponent(
					studyId, componentId));
		} else {
			return redirect(controllers.routes.Studies.index(study.getId()));
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		checkStudyAndLockedAndComponent(studyId, componentId, study,
				loggedInUser, component);

		ComponentModel clone = componentService.cloneComponent(component);
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		checkStudyAndLockedAndComponent(studyId, componentId, study,
				loggedInUser, component);

		componentDao.remove(study, component);
		RequestScopeMessaging
				.success(MessagesStrings.COMPONENT_DELETED_BUT_FILES_NOT);
		return ok(RequestScopeMessaging.getAsJson());
	}

	private void checkStudyAndLocked(Long studyId, StudyModel study,
			UserModel loggedInUser) throws JatosGuiException {
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}
	}

	private void checkStudyAndLockedAndComponent(Long studyId,
			Long componentId, StudyModel study, UserModel loggedInUser,
			ComponentModel component) throws JatosGuiException {
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
