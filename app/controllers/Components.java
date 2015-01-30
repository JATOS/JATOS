package controllers;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.api.mvc.Call;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.Breadcrumbs;
import services.ErrorMessages;
import services.Messages;
import services.PersistanceUtils;

import com.google.inject.Inject;

import controllers.publix.jatos.JatosPublix;
import exceptions.ResultException;

/**
 * Controller that deals with all requests regarding Components within the JATOS
 * GUI.
 * 
 * @author Kristian Lange
 */
public class Components extends Controller {

	public static final String EDIT_SUBMIT_NAME = "action";
	public static final String EDIT_SUBMIT = "Submit";
	public static final String EDIT_SUBMIT_AND_SHOW = "Submit & Show";
	private static final String CLASS_NAME = Components.class.getSimpleName();

	private final ControllerUtils controllerUtils;
	private final PersistanceUtils persistanceUtils;

	@Inject
	public Components(ControllerUtils controllerUtils,
			PersistanceUtils persistanceUtils) {
		this.controllerUtils = controllerUtils;
		this.persistanceUtils = persistanceUtils;
	}

	/**
	 * Actually shows a single component. It uses a JatosWorker and redirects to
	 * Publix.startStudy().
	 */
	@Transactional
	public Result showComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".showComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = controllerUtils.retrieveLoggedInUser();
		StudyModel study = StudyModel.findById(studyId);
		ComponentModel component = ComponentModel.findById(componentId);
		controllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		if (component.getHtmlFilePath() == null
				|| component.getHtmlFilePath().isEmpty()) {
			String errorMsg = ErrorMessages.htmlFilePathEmpty(componentId);
			controllerUtils.throwStudiesResultException(errorMsg,
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
	public Result create(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".create: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = controllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		controllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		controllerUtils.checkStudyLocked(study);

		Form<ComponentModel> form = Form.form(ComponentModel.class);
		Call submitAction = routes.Components.submit(studyId);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.NEW_COMPONENT);
		return ok(views.html.jatos.component.edit.render(studyList,
				loggedInUser, breadcrumbs, null, submitAction, form, study));
	}

	/**
	 * Handles the post request of the form to create a new Component.
	 */
	@Transactional
	public Result submit(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".submit: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = controllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		controllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		controllerUtils.checkStudyLocked(study);

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			Call submitAction = routes.Components.submit(studyId);
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
					Breadcrumbs.NEW_COMPONENT);
			controllerUtils.throwEditComponentResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, study);
		}

		ComponentModel component = form.get();
		persistanceUtils.addComponent(study, component);
		return redirectAfterEdit(studyId, component.getId(), study);
	}

	/**
	 * Shows a view with a form to edit the properties of a Component.
	 */
	@Transactional
	public Result edit(Long studyId, Long componentId) throws ResultException {
		Logger.info(CLASS_NAME + ".edit: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = controllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ComponentModel component = ComponentModel.findById(componentId);
		controllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		Messages messages = new Messages();
		if (study.isLocked()) {
			messages.warning(ErrorMessages.STUDY_IS_LOCKED);
		}
		Form<ComponentModel> form = Form.form(ComponentModel.class).fill(
				component);
		Call submitAction = routes.Components
				.submitEdited(studyId, componentId);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForComponent(study,
				component, Breadcrumbs.EDIT_PROPERTIES);
		return ok(views.html.jatos.component.edit.render(studyList,
				loggedInUser, breadcrumbs, messages, submitAction, form, study));
	}

	/**
	 * Handles the post of the edit form.
	 */
	@Transactional
	public Result submitEdited(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = controllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ComponentModel component = ComponentModel.findById(componentId);
		controllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);
		controllerUtils.checkStudyLocked(study);

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			Call submitAction = routes.Components.submitEdited(studyId,
					componentId);
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForComponent(study,
					component, Breadcrumbs.EDIT_PROPERTIES);
			controllerUtils.throwEditComponentResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, study);
		}

		// Update component in DB
		ComponentModel updatedComponent = form.get();
		persistanceUtils
				.updateComponentsProperties(component, updatedComponent);
		return redirectAfterEdit(studyId, componentId, study);
	}

	private Result redirectAfterEdit(Long studyId, Long componentId,
			StudyModel study) {
		// Check which submit button was pressed: "Submit" or "Submit & Show".
		String[] postAction = request().body().asFormUrlEncoded()
				.get(EDIT_SUBMIT_NAME);
		if (postAction[0].equals(EDIT_SUBMIT_AND_SHOW)) {
			return redirect(routes.Components.showComponent(studyId,
					componentId));
		} else {
			return redirect(routes.Studies.index(study.getId(), null));
		}
	}

	/**
	 * Ajax POST
	 * 
	 * Request to change the property 'active' of a component.
	 */
	@Transactional
	public Result changeProperty(Long studyId, Long componentId, Boolean active)
			throws ResultException {
		Logger.info(CLASS_NAME + ".changeProperty: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "active " + active
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = controllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		controllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);
		controllerUtils.checkStudyLocked(study);

		if (active != null) {
			persistanceUtils.changeActive(component, active);
		}
		return ok();
	}

	/**
	 * Clone a component.
	 */
	@Transactional
	public Result cloneComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".cloneComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = controllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		controllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);
		controllerUtils.checkStudyLocked(study);

		ComponentModel clone = new ComponentModel(component);
		persistanceUtils.addComponent(study, clone);
		return redirect(routes.Studies.index(studyId, null));
	}

	/**
	 * Ajax request
	 * 
	 * Remove a component.
	 */
	@Transactional
	public Result remove(Long studyId, Long componentId) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = controllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		controllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);
		controllerUtils.checkStudyLocked(study);

		persistanceUtils.removeComponent(study, component);
		return ok();
	}

}
