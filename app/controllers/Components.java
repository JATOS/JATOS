package controllers;

import java.io.IOException;
import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
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
import play.mvc.SimpleResult;
import services.ErrorMessages;
import services.IOUtils;
import services.JsonUtils;
import services.PersistanceUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import controllers.publix.MAPublix;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Components extends Controller {

	private static final String CLASS_NAME = Components.class.getSimpleName();

	@Transactional
	public static Result index(Long studyId, Long componentId, String errorMsg,
			int httpStatus) throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		List<ComponentResult> componentResultList = ComponentResult
				.findAllByComponent(component);

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				Breadcrumbs.getComponentBreadcrumb(study, component));
		return status(httpStatus, views.html.mecharg.component.index.render(
				studyList, loggedInUser, breadcrumbs, study, errorMsg,
				component, componentResultList));
	}

	@Transactional
	public static Result index(Long studyId, Long componentId)
			throws ResultException {
		return index(studyId, componentId, null, Http.Status.OK);
	}

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
		ControllerUtils.checkStudyLocked(study);

		if (component.getHtmlFilePath() == null
				|| component.getHtmlFilePath().isEmpty()) {
			String errorMsg = ErrorMessages.urlViewEmpty(componentId);
			SimpleResult result = (SimpleResult) Components.index(studyId,
					componentId, errorMsg, Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
		session(MAPublix.MECHARG_SHOW, MAPublix.SHOW_COMPONENT_START);
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
		String studyDirName = IOUtils.generateStudyDirName(study);
		services.Breadcrumbs breadcrumbs = services.Breadcrumbs
				.generateForStudy(study, "New Component");
		return ok(views.html.mecharg.component.edit2.render(studyList,
				loggedInUser, breadcrumbs, null, submitAction, form,
				studyDirName));
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
			String studyDirName = IOUtils.generateStudyDirName(study);
			services.Breadcrumbs breadcrumbs = services.Breadcrumbs
					.generateForStudy(study, "New Component");
			SimpleResult result = badRequest(views.html.mecharg.component.edit2
					.render(studyList, loggedInUser, breadcrumbs, null,
							submitAction, form, studyDirName));
			throw new ResultException(result);
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
		ControllerUtils.checkStudyLocked(study);

		Form<ComponentModel> form = Form.form(ComponentModel.class).fill(
				component);
		Call submitAction = routes.Components
				.submitEdited(studyId, componentId);
		String studyDirName = IOUtils.generateStudyDirName(study);
		services.Breadcrumbs breadcrumbs = services.Breadcrumbs
				.generateForComponent(study, component, "Edit");
		return ok(views.html.mecharg.component.edit2.render(studyList,
				loggedInUser, breadcrumbs, null, submitAction, form,
				studyDirName));
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
			String studyDirName = IOUtils.generateStudyDirName(study);
			services.Breadcrumbs breadcrumbs = services.Breadcrumbs
					.generateForComponent(study, component, "Edit");
			SimpleResult result = badRequest(views.html.mecharg.component.edit2
					.render(studyList, loggedInUser, breadcrumbs, null,
							submitAction, form, studyDirName));
			throw new ResultException(result);
		}

		// Update component in DB
		DynamicForm requestData = Form.form().bindFromRequest();
		String title = requestData.get(ComponentModel.TITLE);
		String filePath = requestData.get(ComponentModel.HTML_FILE_PATH);
		String jsonData = requestData.get(ComponentModel.JSON_DATA);
		boolean reloadable = (Boolean.valueOf(requestData
				.get(ComponentModel.RELOADABLE)));
		PersistanceUtils.updateComponent(component, title, reloadable,
				filePath, jsonData);
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

	@Transactional
	public static Result exportComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".exportComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		String componentAsJson;
		try {
			componentAsJson = JsonUtils.asJsonForIO(component);
		} catch (JsonProcessingException e) {
			String errorMsg = ErrorMessages.componentExportFailure(componentId);
			SimpleResult result = (SimpleResult) Components.index(studyId,
					componentId, errorMsg, Http.Status.INTERNAL_SERVER_ERROR);
			throw new ResultException(result, errorMsg);
		}

		response().setContentType("application/x-download");
		String filename = IOUtils.generateFileName(component.getTitle(),
				component.getId(), IOUtils.COMPONENT_FILE_SUFFIX);
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		return ok(componentAsJson);
	}

	/**
	 * Imports a arbitrary number of components and files. A component is
	 * persisted into the DB. All other files are just stored in the study's
	 * folder.
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
			ComponentModel component = new IOUtils.UploadUnmarshaller()
					.unmarshalling(filePart.getFile(), ComponentModel.class);
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
