package controllers.gui;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import persistance.ComponentDao;
import persistance.StudyDao;
import play.Logger;
import play.api.mvc.Call;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.RequestScopeMessaging;
import services.gui.Breadcrumbs;
import services.gui.ComponentService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.MessagesStrings;
import services.gui.StudyService;
import services.gui.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.jatos.JatosPublix;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.gui.JatosGuiException;

/**
 * Controller that deals with all requests regarding Components within the JATOS
 * GUI.
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
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
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		StudyModel study = studyDao.findById(studyId);
		ComponentModel component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			componentService.checkStandardForComponents(studyId, componentId,
					loggedInUser, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		if (component.getHtmlFilePath() == null
				|| component.getHtmlFilePath().trim().isEmpty()) {
			String errorMsg = MessagesStrings.htmlFilePathEmpty(componentId);
			jatosGuiExceptionThrower.throwStudies(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		session(JatosPublix.JATOS_SHOW, JatosPublix.SHOW_COMPONENT_START);
		session(JatosPublix.SHOW_COMPONENT_ID, componentId.toString());
		String queryStr = "?" + JatosPublix.JATOS_WORKER_ID + "="
				+ loggedInUser.getWorker().getId();
		return redirect(controllers.publix.routes.PublixInterceptor.startStudy(
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
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		Form<ComponentModel> form = Form.form(ComponentModel.class);
		Call submitAction = controllers.gui.routes.Components.submit(studyId);
		String breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.NEW_COMPONENT);
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
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			Call submitAction = controllers.gui.routes.Components
					.submit(studyId);
			String breadcrumbs = Breadcrumbs.generateForStudy(study,
					Breadcrumbs.NEW_COMPONENT);
			return showEditAfterError(studyList, loggedInUser, form,
					Http.Status.BAD_REQUEST, breadcrumbs, submitAction, study);
		}

		ComponentModel component = form.get();
		componentDao.create(study, component);
		return redirectAfterEdit(studyId, component.getId(), study);
	}

	public Result showEditAfterError(List<StudyModel> studyList,
			UserModel loggedInUser, Form<ComponentModel> form, int httpStatus,
			String breadcrumbs, Call submitAction, StudyModel study) {
		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			return status(httpStatus, views.html.gui.component.edit.render(
					loggedInUser, breadcrumbs, submitAction, form, study));
		}
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
					loggedInUser, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		if (study.isLocked()) {
			RequestScopeMessaging.warning(MessagesStrings.STUDY_IS_LOCKED);
		}
		Form<ComponentModel> form = Form.form(ComponentModel.class).fill(
				component);
		Call submitAction = controllers.gui.routes.Components.submitEdited(
				studyId, componentId);
		String breadcrumbs = Breadcrumbs.generateForComponent(study, component,
				Breadcrumbs.EDIT_PROPERTIES);
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
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		ComponentModel component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
			componentService.checkStandardForComponents(studyId, componentId,
					loggedInUser, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			Call submitAction = controllers.gui.routes.Components.submitEdited(
					studyId, componentId);
			String breadcrumbs = Breadcrumbs.generateForComponent(study,
					component, Breadcrumbs.EDIT_PROPERTIES);
			return showEditAfterError(studyList, loggedInUser, form,
					Http.Status.BAD_REQUEST, breadcrumbs, submitAction, study);
		}

		componentService.updateComponentAfterEdit(component, form.get());
		return redirectAfterEdit(studyId, componentId, study);
	}

	private Result redirectAfterEdit(Long studyId, Long componentId,
			StudyModel study) {
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
			componentService.checkStandardForComponents(studyId, componentId,
					loggedInUser, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		if (active != null) {
			componentDao.changeActive(component, active);
		}
		return ok().as("text/html");
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
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
			componentService.checkStandardForComponents(studyId, componentId,
					loggedInUser, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		ComponentModel clone = componentService.clone(component);
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
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
			componentService.checkStandardForComponents(studyId, componentId,
					loggedInUser, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		componentDao.remove(study, component);
		return ok().as("text/html");
	}

}
