package controllers;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;

import org.apache.commons.lang3.StringUtils;

import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;
import play.mvc.Security;
import play.mvc.SimpleResult;
import services.ErrorMessages;
import services.JsonUtils;
import services.PersistanceUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import controllers.publix.MAPublix;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Components extends Controller {

	public static final String COMPONENT = "component";
	private static final String CLASS_NAME = Components.class.getSimpleName();

	@Transactional
	public static Result index(Long studyId, Long componentId, String errorMsg,
			int httpStatus) throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandardForComponents(studyId, componentId, study, studyList,
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
	public static Result tryComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".tryComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = Users.getLoggedInUser();
		StudyModel study = StudyModel.findById(studyId);
		ComponentModel component = ComponentModel.findById(componentId);
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandardForComponents(studyId, componentId, study, studyList,
				loggedInUser, component);
		Studies.checkStudyLocked(study);

		if (component.getViewUrl() == null || component.getViewUrl().isEmpty()) {
			String errorMsg = ErrorMessages.urlViewEmpty(componentId);
			SimpleResult result = (SimpleResult) Components.index(studyId,
					componentId, errorMsg, Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
		session(MAPublix.MECHARG_TRY, COMPONENT);
		return redirect(component.getViewUrl());
	}

	@Transactional
	public static Result create(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".create: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		Studies.checkStandardForStudy(study, studyId, loggedInUser, studyList);
		Studies.checkStudyLocked(study);

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "New Component");
		return ok(views.html.mecharg.component.create.render(studyList,
				loggedInUser, breadcrumbs, study,
				Form.form(ComponentModel.class)));
	}

	@Transactional
	public static Result submit(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".submit: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = Users.getLoggedInUser();
		Studies.checkStandardForStudy(study, studyId, loggedInUser, studyList);
		Studies.checkStudyLocked(study);

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getHomeBreadcrumb(),
					Breadcrumbs.getStudyBreadcrumb(study), "New Component");
			SimpleResult result = badRequest(views.html.mecharg.component.create
					.render(studyList, loggedInUser, breadcrumbs, study, form));
			throw new ResultException(result);
		}

		ComponentModel component = form.get();
		PersistanceUtils.addComponent(study, component);
		return redirect(routes.Components.index(study.getId(),
				component.getId()));
	}

	@Transactional
	public static Result edit(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".edit: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = Users.getLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandardForComponents(studyId, componentId, study, studyList,
				loggedInUser, component);
		Studies.checkStudyLocked(study);

		Form<ComponentModel> form = Form.form(ComponentModel.class).fill(
				component);
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				Breadcrumbs.getComponentBreadcrumb(study, component), "Edit");
		return ok(views.html.mecharg.component.edit.render(studyList,
				loggedInUser, breadcrumbs, component, study, form));
	}

	@Transactional
	public static Result submitEdited(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".submitEdited: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = Users.getLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandardForComponents(studyId, componentId, study, studyList,
				loggedInUser, component);
		Studies.checkStudyLocked(study);

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getHomeBreadcrumb(),
					Breadcrumbs.getStudyBreadcrumb(study),
					Breadcrumbs.getComponentBreadcrumb(study, component),
					"Edit");
			SimpleResult result = badRequest(views.html.mecharg.component.edit
					.render(studyList, loggedInUser, breadcrumbs, component,
							study, form));
			throw new ResultException(result);
		}

		// Update component in DB
		DynamicForm requestData = Form.form().bindFromRequest();
		String title = requestData.get(ComponentModel.TITLE);
		String viewUrl = requestData.get(ComponentModel.VIEW_URL);
		String jsonData = requestData.get(ComponentModel.JSON_DATA);
		boolean reloadable = (requestData.get(ComponentModel.RELOADABLE) != null);
		PersistanceUtils.updateComponent(component, title, reloadable, viewUrl,
				jsonData);

		String[] postAction = request().body().asFormUrlEncoded().get("action");
		if ("UpdateAndShow".equals(postAction[0])) {
			return tryComponent(studyId, componentId);
		}
		return redirect(routes.Components.index(study.getId(), componentId));
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
		UserModel loggedInUser = Users.getLoggedInUserAjax();
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandardForComponentsAjax(studyId, componentId, study,
				loggedInUser, component);
		Studies.checkStudyLockedAjax(study);

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
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandardForComponents(studyId, componentId, study, studyList,
				loggedInUser, component);
		Studies.checkStudyLocked(study);

		ComponentModel clone = new ComponentModel(component);
		PersistanceUtils.addComponent(study, clone);
		return redirect(routes.Components.index(studyId, clone.getId()));
	}

	@Transactional
	public static Result export(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".export: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandardForComponentsAjax(studyId, componentId, study,
				loggedInUser, component);

		String componentAsJson;
		try {
			componentAsJson = JsonUtils.asJsonForIO(component);
		} catch (JsonProcessingException e) {
			String errorMsg = ErrorMessages.componentExportFailure(componentId);
			SimpleResult result = internalServerError(errorMsg);
			throw new ResultException(result, errorMsg);
		}

		response().setContentType("application/x-download");
		String filename = component.getTitle().trim()
				.replaceAll("[^a-zA-Z0-9\\.\\-]", "_").toLowerCase();
		filename = StringUtils.left(filename, 250).concat(".mac");
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		return ok(componentAsJson);
	}

	@Transactional
	public static Result importComponent(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".importComponent: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = Users.getLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAll();
		Studies.checkStandardForStudy(study, studyId, loggedInUser, studyList);
		Studies.checkStudyLocked(study);

		ComponentModel component;
		try {
			MultipartFormData mfd = request().body().asMultipartFormData();
			component = JsonUtils.rippingObjectFromJsonUploadRequest(mfd,
					ComponentModel.class);
		} catch (ResultException e) {
			SimpleResult result = (SimpleResult) Studies.index(study.getId(),
					e.getMessage(), Http.Status.BAD_REQUEST);
			e.setResult(result);
			throw e;
		}
		if (component.validate() != null) {
			String errorMsg = ErrorMessages.componentIsntValid();
			SimpleResult result = (SimpleResult) Studies.index(study.getId(),
					errorMsg, Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
		PersistanceUtils.addComponent(study, component);
		return redirect(routes.Studies.index(studyId));
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
		UserModel loggedInUser = Users.getLoggedInUserAjax();
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandardForComponentsAjax(studyId, componentId, study,
				loggedInUser, component);
		Studies.checkStudyLockedAjax(study);

		PersistanceUtils.removeComponent(study, component);
		return ok();
	}

	public static void checkStandardForComponents(Long studyId,
			Long componentId, StudyModel study, List<StudyModel> studyList,
			UserModel loggedInUser, ComponentModel component)
			throws ResultException {
		Studies.checkStandardForStudy(study, studyId, loggedInUser, studyList);
		if (component == null) {
			String errorMsg = ErrorMessages.componentNotExist(componentId);
			SimpleResult result = (SimpleResult) Home.home(errorMsg,
					Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			String errorMsg = ErrorMessages.componentNotBelongToStudy(studyId,
					componentId);
			SimpleResult result = (SimpleResult) Home.home(errorMsg,
					Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStandardForComponentsAjax(Long studyId,
			Long componentId, StudyModel study, UserModel loggedInUser,
			ComponentModel component) throws ResultException {
		Studies.checkStandardForStudyAjax(study, studyId, loggedInUser);
		if (component == null) {
			String errorMsg = ErrorMessages.componentNotExist(componentId);
			SimpleResult result = badRequest(errorMsg);
			throw new ResultException(result, errorMsg);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			String errorMsg = ErrorMessages.componentNotBelongToStudy(studyId,
					componentId);
			SimpleResult result = badRequest(errorMsg);
			throw new ResultException(result, errorMsg);
		}
	}

}
