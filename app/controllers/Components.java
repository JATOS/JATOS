package controllers;

import java.util.List;

import models.MAComponent;
import models.MAExperiment;
import models.MAUser;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Result;
import play.mvc.Security;

public class Components extends MAController {

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result get(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
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

		return ok(views.html.admin.component.component.render(experimentList, experiment,
				null, user, component));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result create(Long experimentId)
			throws NumberFormatException {
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

		return ok(views.html.admin.component.create.render(experimentList, experiment, null,
				user, Form.form(MAComponent.class)));
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
			return badRequest(views.html.admin.component.create.render(experimentList,
					experiment, null, user, form));
		} else {
			MAComponent component = form.get();
			component.experiment = experiment;
			component.persist();
			return redirect(routes.Components.get(experiment.id, component.id));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result update(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
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

		Form<MAComponent> form = Form.form(MAComponent.class).fill(component);
		return ok(views.html.admin.component.update.render(experimentList, component,
				experiment, null, user, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdated(Long experimentId,
			Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
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

		Form<MAComponent> form = Form.form(MAComponent.class).bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(views.html.admin.component.update.render(experimentList,
					component, experiment, null, user, form));
		}

		DynamicForm requestData = Form.form().bindFromRequest();
		component.title = requestData.get("title");
		component.setJsonData(requestData.get("jsonData"));
		component.merge();
		return redirect(routes.Components.get(experiment.id, componentId));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result delete(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(MAController.COOKIE_EMAIL));
		MAComponent component = MAComponent.findById(componentId);
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

		component.remove();
		return redirect(routes.Admin.index());
	}

}
