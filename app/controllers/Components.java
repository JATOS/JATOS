package controllers;

import java.util.List;
import java.util.Map;

import models.MAComponent;
import models.MAStudy;
import models.MAResult;
import models.MAUser;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Result;
import play.mvc.Security;

public class Components extends MAController {

	public static final String TITLE = "title";
	public static final String VIEW_URL = "viewUrl";
	public static final String JSON_DATA = "jsonData";
	public static final String RESULT = "result";
	public static final String RELOADABLE = "reloadable";

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result index(Long studyId, Long componentId) {
		MAStudy study = MAStudy.findById(studyId);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		List<MAStudy> studyList = MAStudy.findAll();
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}

		String breadcrumbs = MAController.getBreadcrumbs(
				MAController.getDashboardBreadcrumb(),
				Studies.getStudyBreadcrumb(study),
				getComponentBreadcrumb(study, component));
		return ok(views.html.admin.component.index.render(studyList,
				loggedInUser, breadcrumbs, study, null, component));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result tryComponent(Long studyId, Long componentId) {
		MAStudy study = MAStudy.findById(studyId);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		List<MAStudy> studyList = MAStudy.findAll();
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}

		if (!study.hasMember(loggedInUser)) {
			return forbiddenNotMember(loggedInUser, study, studyList);
		}

		if (component.getViewUrl() == null || component.getViewUrl().isEmpty()) {
			return badRequestUrlViewEmpty(loggedInUser, study, component,
					studyList);
		}
		return redirect(component.getViewUrl());
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result create(Long studyId) {
		MAStudy study = MAStudy.findById(studyId);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}
		if (study == null) {
			return badRequestStudyNotExist(studyId, loggedInUser, studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			return forbiddenNotMember(loggedInUser, study, studyList);
		}

		String breadcrumbs = MAController.getBreadcrumbs(
				MAController.getDashboardBreadcrumb(),
				Studies.getStudyBreadcrumb(study), "New Component");
		return ok(views.html.admin.component.create.render(studyList,
				loggedInUser, breadcrumbs, study, Form.form(MAComponent.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submit(Long studyId) {
		MAStudy study = MAStudy.findById(studyId);
		List<MAStudy> studyList = MAStudy.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}
		if (study == null) {
			return badRequestStudyNotExist(studyId, loggedInUser, studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			return forbiddenNotMember(loggedInUser, study, studyList);
		}

		Form<MAComponent> form = Form.form(MAComponent.class).bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = MAController.getBreadcrumbs(
					MAController.getDashboardBreadcrumb(),
					Studies.getStudyBreadcrumb(study), "New Component");
			return badRequest(views.html.admin.component.create.render(
					studyList, loggedInUser, breadcrumbs, study, form));
		} else {
			MAComponent component = form.get();
			addComponent(study, component);
			return redirect(routes.Components.index(study.getId(),
					component.getId()));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result edit(Long studyId, Long componentId) {
		MAStudy study = MAStudy.findById(studyId);
		List<MAStudy> studyList = MAStudy.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}
		if (!study.hasMember(loggedInUser)) {
			return forbiddenNotMember(loggedInUser, study, studyList);
		}

		Form<MAComponent> form = Form.form(MAComponent.class).fill(component);
		String breadcrumbs = MAController.getBreadcrumbs(
				MAController.getDashboardBreadcrumb(),
				Studies.getStudyBreadcrumb(study),
				getComponentBreadcrumb(study, component), "Edit");
		return ok(views.html.admin.component.edit.render(studyList,
				loggedInUser, breadcrumbs, component, study, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitEdited(Long studyId, Long componentId) {
		MAStudy study = MAStudy.findById(studyId);
		List<MAStudy> studyList = MAStudy.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}
		if (!study.hasMember(loggedInUser)) {
			return forbiddenNotMember(loggedInUser, study, studyList);
		}

		Form<MAComponent> form = Form.form(MAComponent.class).bindFromRequest();
		if (form.hasErrors()) {
			String breadcrumbs = MAController.getBreadcrumbs(
					MAController.getDashboardBreadcrumb(),
					Studies.getStudyBreadcrumb(study),
					getComponentBreadcrumb(study, component),
					"Edit");
			return badRequest(views.html.admin.component.edit.render(
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
		MAStudy study = MAStudy.findById(studyId);
		List<MAStudy> studyList = MAStudy.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}
		if (!study.hasMember(loggedInUser)) {
			return forbiddenNotMember(loggedInUser, study, studyList);
		}

		removeComponent(study, component);
		return redirect(routes.Studies.index(study.getId()));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result removeResults(Long studyId, Long componentId) {
		MAStudy study = MAStudy.findById(studyId);
		List<MAStudy> studyList = MAStudy.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}
		if (!study.hasMember(loggedInUser)) {
			return forbiddenNotMember(loggedInUser, study, studyList);
		}

		String breadcrumbs = MAController.getBreadcrumbs(
				MAController.getDashboardBreadcrumb(),
				Studies.getStudyBreadcrumb(study),
				getComponentBreadcrumb(study, component), "Delete Results");
		return ok(views.html.admin.component.removeResults.render(studyList,
				loggedInUser, breadcrumbs, component, study));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitRemovedResults(Long studyId, Long componentId) {
		MAStudy study = MAStudy.findById(studyId);
		List<MAStudy> studyList = MAStudy.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(studyId, componentId, study, studyList,
				loggedInUser, component);
		if (result != null) {
			return result;
		}
		if (!study.hasMember(loggedInUser)) {
			return forbiddenNotMember(loggedInUser, study, studyList);
		}

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedComponents = formMap.get(RESULT);
		if (checkedComponents != null) {
			for (String resultIdStr : checkedComponents) {
				removeResult(resultIdStr, component);
			}
		}

		return redirect(routes.Components.index(study.getId(), componentId));
	}

	private static void removeResult(String resultIdStr, MAComponent component) {
		try {
			Long resultId = Long.valueOf(resultIdStr);
			MAResult result = MAResult.findById(resultId);
			if (result != null) {
				component.removeResult(result);
				component.merge();
				result.remove();
			}
		} catch (NumberFormatException e) {
			// Do nothing
		}
	}

	private static void addComponent(MAStudy study, MAComponent component) {
		component.setStudy(study);
		study.addComponent(component);
		component.persist();
		study.merge();
	}

	private static void removeComponent(MAStudy study, MAComponent component) {
		component.remove();
		study.removeComponent(component);
		study.merge();
	}

	private static Result checkStandard(Long studyId, Long componentId,
			MAStudy study, List<MAStudy> studyList, MAUser loggedInUser,
			MAComponent component) {
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}
		if (study == null) {
			return badRequestStudyNotExist(studyId, loggedInUser, studyList);
		}
		if (!study.hasMember(loggedInUser)) {
			return forbiddenNotMember(loggedInUser, study, studyList);
		}
		if (component == null) {
			return badRequestComponentNotExist(componentId, study,
					loggedInUser, studyList);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			return badRequestComponentNotBelongToStudy(study, component,
					loggedInUser, studyList);
		}
		return null;
	}

	public static String getComponentBreadcrumb(MAStudy study,
			MAComponent component) {
		StringBuffer sb = new StringBuffer();
		sb.append("<a href=\"");
		sb.append(routes.Components.index(study.getId(), component.getId()));
		sb.append("\">");
		sb.append(component.getTitle());
		sb.append("</a>");
		return sb.toString();
	}

}
