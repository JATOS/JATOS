package controllers;

import java.util.List;
import java.util.Map;

import models.MAComponent;
import models.MAExperiment;
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
	public static Result index(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		Result result = checkStandard(experimentId, componentId, experiment,
				experimentList, user, component);
		if (result != null) {
			return result;
		}

		return ok(views.html.admin.component.index.render(experimentList,
				experiment, null, user, component));
	}
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result tryComponent(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		Result result = checkStandard(experimentId, componentId, experiment,
				experimentList, user, component);
		if (result != null) {
			return result;
		}
		
		if (component.viewUrl == null || component.viewUrl.isEmpty()) {
			return badRequestUrlViewEmpty(user, experiment, experimentList);
		}
		return redirect(component.viewUrl);
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result create(Long experimentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.hasMember(user)) {
			return forbiddenNotMember(user, experiment, experimentList);
		}

		return ok(views.html.admin.component.create.render(experimentList,
				experiment, user, Form.form(MAComponent.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submit(Long experimentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.hasMember(user)) {
			return forbiddenNotMember(user, experiment, experimentList);
		}

		Form<MAComponent> form = Form.form(MAComponent.class).bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(views.html.admin.component.create.render(
					experimentList, experiment, user, form));
		} else {
			MAComponent component = form.get();
			addComponent(experiment, component);
			return redirect(routes.Components
					.index(experiment.id, component.id));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result update(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(experimentId, componentId, experiment,
				experimentList, user, component);
		if (result != null) {
			return result;
		}

		Form<MAComponent> form = Form.form(MAComponent.class).fill(component);
		return ok(views.html.admin.component.update.render(experimentList,
				component, experiment, user, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdated(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(experimentId, componentId, experiment,
				experimentList, user, component);
		if (result != null) {
			return result;
		}

		Form<MAComponent> form = Form.form(MAComponent.class).bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(views.html.admin.component.update.render(
					experimentList, component, experiment, user, form));
		}

		// Update component in DB
		DynamicForm requestData = Form.form().bindFromRequest();
		String title = requestData.get(TITLE);
		String viewUrl = requestData.get(VIEW_URL);
		String jsonData = requestData.get(JSON_DATA);
		boolean reloadable = (requestData.get(RELOADABLE) != null);
		component.update(title, reloadable, viewUrl, jsonData);
		component.merge();
		return redirect(routes.Components.index(experiment.id, componentId));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result delete(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(experimentId, componentId, experiment,
				experimentList, user, component);
		if (result != null) {
			return result;
		}

		removeComponent(experiment, component);
		return redirect(routes.Experiments.index(experiment.id));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result deleteResults(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(experimentId, componentId, experiment,
				experimentList, user, component);
		if (result != null) {
			return result;
		}

		return ok(views.html.admin.component.deleteResults.render(
				experimentList, component, experiment, user));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitDeletedResults(Long experimentId,
			Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		Result result = checkStandard(experimentId, componentId, experiment,
				experimentList, user, component);
		if (result != null) {
			return result;
		}

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedComponents = formMap.get(RESULT);
		if (checkedComponents != null) {
			for (String resultIdStr : checkedComponents) {
				removeResult(resultIdStr, component);
			}
		}

		return redirect(routes.Components.index(experiment.id, componentId));
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

	private static void addComponent(MAExperiment experiment,
			MAComponent component) {
		component.experiment = experiment;
		experiment.addComponent(component);
		component.persist();
		experiment.merge();
	}

	private static void removeComponent(MAExperiment experiment,
			MAComponent component) {
		component.remove(); // TODO unnecessary because cascade.ALL?
		experiment.removeComponent(component);
		experiment.merge();
	}

	private static Result checkStandard(Long experimentId, Long componentId,
			MAExperiment experiment, List<MAExperiment> experimentList,
			MAUser user, MAComponent component) {
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.hasMember(user)) {
			return forbiddenNotMember(user, experiment, experimentList);
		}
		if (component == null) {
			return badRequestComponentNotExist(componentId, experiment, user,
					experimentList);
		}
		if (component.experiment.id != experiment.id) {
			return badRequestComponentNotBelongToExperiment(experiment,
					component, user, experimentList);
		}
		return null;
	}

}
