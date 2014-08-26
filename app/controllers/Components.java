package controllers;

import java.util.List;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import play.mvc.SimpleResult;
import services.ErrorMessages;
import services.Persistance;
import controllers.publix.MAPublix;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Components extends Controller {

	public static final String COMPONENT = "component";
	private static final String CLASS_NAME = Components.class.getSimpleName();

	@Transactional
	public static Result index(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandard(studyId, componentId, study, studyList, loggedInUser,
				component);

		List<ComponentResult> componentResultList = ComponentResult
				.findAllByComponent(component);

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				Breadcrumbs.getComponentBreadcrumb(study, component));
		return ok(views.html.mecharg.component.index.render(studyList,
				loggedInUser, breadcrumbs, study, null, component,
				componentResultList));
	}

	@Transactional
	public static Result tryComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".tryComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		ComponentModel component = ComponentModel.findById(componentId);
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandard(studyId, componentId, study, studyList, loggedInUser,
				component);

		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}

		if (component.getViewUrl() == null || component.getViewUrl().isEmpty()) {
			throw BadRequests.badRequestUrlViewEmpty(loggedInUser, study,
					component, studyList);
		}
		session(MAPublix.MECHARG_TRY, COMPONENT);
		return redirect(component.getViewUrl());
	}

	@Transactional
	public static Result create(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".create: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		if (study == null) {
			throw BadRequests.badRequestStudyNotExist(studyId, loggedInUser,
					studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
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
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		if (study == null) {
			throw BadRequests.badRequestStudyNotExist(studyId, loggedInUser,
					studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(),
					Breadcrumbs.getStudyBreadcrumb(study), "New Component");
			SimpleResult result = badRequest(views.html.mecharg.component.create
					.render(studyList, loggedInUser, breadcrumbs, study, form));
			throw new ResultException(result);
		}

		ComponentModel component = form.get();
		Persistance.addComponent(study, component);
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
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandard(studyId, componentId, study, studyList, loggedInUser,
				component);

		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}

		Form<ComponentModel> form = Form.form(ComponentModel.class).fill(
				component);
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
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
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandard(studyId, componentId, study, studyList, loggedInUser,
				component);

		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(),
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
		Persistance.updateComponent(component, title, reloadable, viewUrl,
				jsonData);
		return redirect(routes.Components.index(study.getId(), componentId));
	}

	@Transactional
	public static Result remove(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".remove: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandard(studyId, componentId, study, studyList, loggedInUser,
				component);

		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}

		Persistance.removeComponent(study, component);
		return redirect(routes.Studies.index(study.getId()));
	}

	@Transactional
	public static Result removeSingleResult(Long studyId, Long componentId,
			Long componentResultId) throws ResultException {
		Logger.info(CLASS_NAME + ".removeSingleResult: studyId " + studyId
				+ ", " + "componentId " + componentId + ", " + "resultId "
				+ componentResultId + ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		ComponentModel component = ComponentModel.findById(componentId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		checkStandard(studyId, componentId, study, studyList, loggedInUser,
				component);
		if (!study.hasMember(loggedInUser)) {
			return badRequest(ErrorMessages.notMember(loggedInUser.getName(),
					loggedInUser.getEmail(), study.getId(), study.getTitle()));
		}
		
		ComponentResult componentResult = ComponentResult
				.findById(componentResultId);
		if (component == null) {
			throw BadRequests.badRequestComponentResultNotExist(
					componentResultId, study, loggedInUser, studyList);
		}
		Persistance.removeComponentResult(componentResult);
		return ok();
	}

	@Transactional
	public static Result removeResults(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".removeResults: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandard(studyId, componentId, study, studyList, loggedInUser,
				component);

		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}

		List<ComponentResult> componentResultList = ComponentResult
				.findAllByComponent(component);

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				Breadcrumbs.getComponentBreadcrumb(study, component),
				"Delete Results");
		return ok(views.html.mecharg.component.removeResults.render(studyList,
				loggedInUser, breadcrumbs, component, study,
				componentResultList));
	}

	@Transactional
	public static Result submitRemovedResults(Long studyId, Long componentId)
			throws Exception {
		Logger.info(CLASS_NAME + ".submitRemovedResults: studyId " + studyId
				+ ", " + "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		checkStandard(studyId, componentId, study, studyList, loggedInUser,
				component);

		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedComponents = formMap.get(ComponentModel.RESULT);
		if (checkedComponents != null) {
			for (String resultIdStr : checkedComponents) {
				Persistance.removeComponentResult(resultIdStr);
			}
		}

		return redirect(routes.Components.index(study.getId(), componentId));
	}

	private static void checkStandard(Long studyId, Long componentId,
			StudyModel study, List<StudyModel> studyList,
			UserModel loggedInUser, ComponentModel component)
			throws ResultException {
		if (loggedInUser == null) {
			throw new ResultException(redirect(routes.Authentication.login()));
		}
		if (study == null) {
			throw BadRequests.badRequestStudyNotExist(studyId, loggedInUser,
					studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}
		if (component == null) {
			throw BadRequests.badRequestComponentNotExist(componentId, study,
					loggedInUser, studyList);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			throw BadRequests.badRequestComponentNotBelongToStudy(study,
					component, loggedInUser, studyList);
		}
	}

}
