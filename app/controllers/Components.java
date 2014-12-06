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
import play.mvc.Security;
import services.Breadcrumbs;
import services.ErrorMessages;
import services.Messages;
import services.PersistanceUtils;
import controllers.publix.JatosPublix;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Components extends Controller {

	private static final String CLASS_NAME = Components.class.getSimpleName();

	@Transactional
	public static Result showComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".showComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		StudyModel study = StudyModel.findById(studyId);
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		if (component.getHtmlFilePath() == null
				|| component.getHtmlFilePath().isEmpty()) {
			String errorMsg = ErrorMessages.urlViewEmpty(componentId);
			ControllerUtils.throwStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		session(JatosPublix.JATOS_SHOW, JatosPublix.SHOW_COMPONENT_START);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, componentId));
	}

	@Transactional
	public static Result create(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".create: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		Form<ComponentModel> form = Form.form(ComponentModel.class);
		Call submitAction = routes.Components.submit(studyId);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.NEW_COMPONENT);
		return ok(views.html.jatos.component.edit.render(studyList,
				loggedInUser, breadcrumbs, null, submitAction, form, study));
	}

	@Transactional
	public static Result submit(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".submit: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			Call submitAction = routes.Components.submit(studyId);
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
					Breadcrumbs.NEW_COMPONENT);
			ControllerUtils.throwEditComponentResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, study);
		}

		ComponentModel component = form.get();
		PersistanceUtils.addComponent(study, component);
		return redirectAfterEdit(studyId, component.getId(), study);
	}

	@Transactional
	public static Result edit(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".edit: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
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

	@Transactional
	public static Result submitEdited(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);
		ControllerUtils.checkStudyLocked(study);

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			Call submitAction = routes.Components.submitEdited(studyId,
					componentId);
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForComponent(study,
					component, Breadcrumbs.EDIT_PROPERTIES);
			ControllerUtils.throwEditComponentResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, study);
		}

		// Update component in DB
		ComponentModel updatedComponent = form.get();
		PersistanceUtils.updateComponentsProperties(component, updatedComponent);
		return redirectAfterEdit(studyId, componentId, study);
	}

	private static Result redirectAfterEdit(Long studyId, Long componentId,
			StudyModel study) {
		// Check which submit button was pressed: "Submit" or "Submit & Show".
		String[] postAction = request().body().asFormUrlEncoded().get("action");
		if (postAction[0].toLowerCase().contains("show")) {
			return redirect(routes.Components.showComponent(studyId,
					componentId));
		} else {
			return redirect(routes.Studies.index(study.getId(), null));
		}
	}

	/**
	 * Ajax POST request to change a single property. So far the only property
	 * possible to change is 'active'.
	 */
	@Transactional
	public static Result changeProperty(Long studyId, Long componentId,
			Boolean active) throws ResultException {
		Logger.info(CLASS_NAME + ".changeProperty: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "active " + active
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);
		ControllerUtils.checkStudyLocked(study);

		if (active != null) {
			PersistanceUtils.changeActive(component, active);
		}
		return ok();
	}

	@Transactional
	public static Result cloneComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".cloneComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);
		ControllerUtils.checkStudyLocked(study);

		ComponentModel clone = new ComponentModel(component);
		PersistanceUtils.addComponent(study, clone);
		return redirect(routes.Studies.index(studyId, null));
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result remove(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".remove: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);
		ControllerUtils.checkStudyLocked(study);

		PersistanceUtils.removeComponent(study, component);
		return ok();
	}

}
