package controllers;

import java.util.List;
import java.util.Map;

import controllers.routes;
import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import models.results.StudyResult;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

public class Components extends Controller {

	public static final String TITLE = "title";
	public static final String VIEW_URL = "viewUrl";
	public static final String JSON_DATA = "jsonData";
	public static final String RESULT = "result";
	public static final String RELOADABLE = "reloadable";

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result index(Long studyId, Long componentId) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		List<StudyModel> studyList = StudyModel.findAll();
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}

		List<ComponentResult> componentResultList = ComponentResult
				.findAllByComponent(componentId);

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				Breadcrumbs.getComponentBreadcrumb(study, component));
		return ok(views.html.mecharg.component.index.render(studyList,
				loggedInUser, breadcrumbs, study, null, component,
				componentResultList));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result tryComponent(Long studyId, Long componentId) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		List<StudyModel> studyList = StudyModel.findAll();
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}

		if (!study.hasMember(loggedInUser)) {
			return BadRequests.forbiddenNotMember(loggedInUser, study,
					studyList);
		}

		if (component.getViewUrl() == null || component.getViewUrl().isEmpty()) {
			return BadRequests.badRequestUrlViewEmpty(loggedInUser, study,
					component, studyList);
		}
		return redirect(component.getViewUrl());
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result create(Long studyId) {
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		if (study == null) {
			return BadRequests.badRequestStudyNotExist(studyId, loggedInUser,
					studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			return BadRequests.forbiddenNotMember(loggedInUser, study,
					studyList);
		}

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "New Component");
		return ok(views.html.mecharg.component.create.render(studyList,
				loggedInUser, breadcrumbs, study,
				Form.form(ComponentModel.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submit(Long studyId) {
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		if (study == null) {
			return BadRequests.badRequestStudyNotExist(studyId, loggedInUser,
					studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			return BadRequests.forbiddenNotMember(loggedInUser, study,
					studyList);
		}

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(),
					Breadcrumbs.getStudyBreadcrumb(study), "New Component");
			return badRequest(views.html.mecharg.component.create.render(
					studyList, loggedInUser, breadcrumbs, study, form));
		} else {
			ComponentModel component = form.get();
			addComponent(study, component);
			return redirect(routes.Components.index(study.getId(),
					component.getId()));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result edit(Long studyId, Long componentId) {
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}
		if (!study.hasMember(loggedInUser)) {
			return BadRequests.forbiddenNotMember(loggedInUser, study,
					studyList);
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
	@Security.Authenticated(Secured.class)
	public static Result submitEdited(Long studyId, Long componentId) {
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}
		if (!study.hasMember(loggedInUser)) {
			return BadRequests.forbiddenNotMember(loggedInUser, study,
					studyList);
		}

		Form<ComponentModel> form = Form.form(ComponentModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(),
					Breadcrumbs.getStudyBreadcrumb(study),
					Breadcrumbs.getComponentBreadcrumb(study, component),
					"Edit");
			return badRequest(views.html.mecharg.component.edit.render(
					studyList, loggedInUser, breadcrumbs, component, study,
					form));
		}

		// Update component in DB
		DynamicForm requestData = Form.form().bindFromRequest();
		String title = requestData.get(TITLE);
		String viewUrl = requestData.get(VIEW_URL);
		String jsonData = requestData.get(JSON_DATA);
		boolean reloadable = (requestData.get(RELOADABLE) != null);
		component.update(title, reloadable, viewUrl, jsonData);
		component.merge();
		return redirect(routes.Components.index(study.getId(), componentId));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result remove(Long studyId, Long componentId) {
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}
		if (!study.hasMember(loggedInUser)) {
			return BadRequests.forbiddenNotMember(loggedInUser, study,
					studyList);
		}

		removeComponent(study, component);
		return redirect(routes.Studies.index(study.getId()));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result removeResults(Long studyId, Long componentId) {
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}
		if (!study.hasMember(loggedInUser)) {
			return BadRequests.forbiddenNotMember(loggedInUser, study,
					studyList);
		}

		List<ComponentResult> componentResultList = ComponentResult
				.findAllByComponent(componentId);

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
	@Security.Authenticated(Secured.class)
	public static Result submitRemovedResults(Long studyId, Long componentId) {
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}
		if (!study.hasMember(loggedInUser)) {
			return BadRequests.forbiddenNotMember(loggedInUser, study,
					studyList);
		}

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedComponents = formMap.get(RESULT);
		if (checkedComponents != null) {
			for (String resultIdStr : checkedComponents) {
				removeResult(resultIdStr);
			}
		}

		return redirect(routes.Components.index(study.getId(), componentId));
	}

	private static void removeResult(String componentResultIdStr) {
		try {
			Long componentResultId = Long.valueOf(componentResultIdStr);
			ComponentResult componentResult = ComponentResult
					.findById(componentResultId);
			if (componentResult != null) {
				StudyResult studyResult = componentResult.getStudyResult();
				studyResult.removeComponentResult(componentResult);
				studyResult.merge();
				componentResult.remove();
			}
		} catch (NumberFormatException e) {
			// Do nothing
		}
	}

	private static void addComponent(StudyModel study, ComponentModel component) {
		component.setStudy(study);
		study.addComponent(component);
		component.persist();
		study.merge();
	}

	private static void removeComponent(StudyModel study,
			ComponentModel component) {
		component.remove();
		study.removeComponent(component);
		study.merge();
	}

	private static Result checkStandard(Long studyId, Long componentId,
			StudyModel study, List<StudyModel> studyList,
			UserModel loggedInUser, ComponentModel component) {
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		if (study == null) {
			return BadRequests.badRequestStudyNotExist(studyId, loggedInUser,
					studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			return BadRequests.forbiddenNotMember(loggedInUser, study,
					studyList);
		}
		if (component == null) {
			return BadRequests.badRequestComponentNotExist(componentId, study,
					loggedInUser, studyList);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			return BadRequests.badRequestComponentNotBelongToStudy(study,
					component, loggedInUser, studyList);
		}
		return null;
	}

}
