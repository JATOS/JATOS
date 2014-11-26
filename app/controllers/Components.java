package controllers;

import java.io.IOException;
import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.api.mvc.Call;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import play.mvc.Security;
import services.Breadcrumbs;
import services.ErrorMessages;
import services.IOUtils;
import services.JsonUtils;
import services.JsonUtils.UploadUnmarshaller;
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
				"New Component");
		return ok(views.html.jatos.component.edit.render(studyList,
				loggedInUser, breadcrumbs, null, submitAction, form,
				study));
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
					"New Component");
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
				component, "Edit");
		return ok(views.html.jatos.component.edit.render(studyList,
				loggedInUser, breadcrumbs, messages, submitAction, form,
				study));
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
					component, "Edit");
			ControllerUtils.throwEditComponentResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, breadcrumbs,
					submitAction, study);
		}

		// Update component in DB
		DynamicForm requestData = Form.form().bindFromRequest();
		String title = requestData.get(ComponentModel.TITLE);
		String filePath = requestData.get(ComponentModel.HTML_FILE_PATH);
		String comments = requestData.get(ComponentModel.COMMENTS);
		String jsonData = requestData.get(ComponentModel.JSON_DATA);
		boolean reloadable = (Boolean.valueOf(requestData
				.get(ComponentModel.RELOADABLE)));
		PersistanceUtils.updateComponent(component, title, reloadable,
				filePath, comments, jsonData);
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
	public static Result exportComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".exportComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME);
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		String componentAsJson = null;
		try {
			componentAsJson = JsonUtils.asJsonForIO(component);
		} catch (IOException e) {
			String errorMsg = ErrorMessages.componentExportFailure(componentId);
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}

		response().setContentType("application/x-download");
		String filename = IOUtils.generateFileName(component.getTitle(),
				component.getId(), IOUtils.COMPONENT_FILE_SUFFIX);
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		// Set cookie for jQuery.fileDownload plugin
		response().setCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME,
				ControllerUtils.JQDOWNLOAD_COOKIE_CONTENT);
		return ok(componentAsJson);
	}

	/**
	 * HTTP Ajax request Imports a arbitrary number of components and files. A
	 * component is persisted into the DB. All other files are just stored in
	 * the study's folder.
	 */
	@Transactional
	public static Result importComponent(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".importComponent: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		// Loop through all uploaded files
		MultipartFormData mfd = request().body().asMultipartFormData();
		List<FilePart> filePartList = mfd.getFiles();
		for (FilePart filePart : filePartList) {
			// If the key isn't right the upload doesn't come from the right
			// form
			if (!filePart.getKey().equals(ComponentModel.COMPONENT)) {
				String errorMsg = ErrorMessages.NO_COMPONENT_UPLOAD;
				ControllerUtils.throwStudiesResultException(errorMsg,
						Http.Status.BAD_REQUEST, studyId);
			}
			// Try whether we have a component
			ComponentModel component = new UploadUnmarshaller().unmarshalling(
					filePart.getFile(), ComponentModel.class);
			if (component != null) {
				if (component.validate() != null) {
					String errorMsg = ErrorMessages.COMPONENT_INVALID;
					ControllerUtils.throwStudiesResultException(errorMsg,
							Http.Status.BAD_REQUEST, studyId);
				} else {
					PersistanceUtils.addComponent(study, component);
				}
			} else {
				// It's no component, so just save the file in the study dir
				moveFileIntoStudyFolder(filePart, study);
			}
		}
		return ok();
	}

	private static void moveFileIntoStudyFolder(FilePart filePart,
			StudyModel study) throws ResultException {
		try {
			IOUtils.moveFileIntoStudyFolder(filePart, study);
		} catch (IOException e) {
			ControllerUtils.throwStudiesResultException(e.getMessage(),
					Http.Status.BAD_REQUEST, study.getId());
		}
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
