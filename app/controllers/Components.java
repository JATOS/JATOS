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

	public static final String JSON_DATA = "jsonData";
	public static final String TITLE = "title";
	public static final String RESULT = "result";

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
	public static Result create(Long experimentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.isMember(user)) {
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
		if (!experiment.isMember(user)) {
			return forbiddenNotMember(user, experiment, experimentList);
		}

		Form<MAComponent> form = Form.form(MAComponent.class).bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(views.html.admin.component.create.render(
					experimentList, experiment, user, form));
		} else {
			MAComponent component = form.get();
			component.experiment = experiment;
			component.persist();
			return redirect(routes.Components.index(experiment.id, component.id));
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
		component.title = requestData.get(TITLE);
		component.setJsonData(requestData.get(JSON_DATA));
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

		component.remove();
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
				removeResult(resultIdStr);
			}
		}
		
		return redirect(routes.Components.index(experiment.id, componentId));
	}
	
	private static void removeResult(String resultIdStr) {
		try {
			Long resultId = Long.valueOf(resultIdStr);
			MAResult result = MAResult.findById(resultId);
			if (result != null) {
				result.remove();
			}
		} catch (NumberFormatException e) {
			// Do nothing
		}
	}
	
	private static Result checkStandard(Long experimentId, Long componentId,
			MAExperiment experiment, List<MAExperiment> experimentList,
			MAUser user, MAComponent component) {
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.isMember(user)) {
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
