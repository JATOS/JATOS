package controllers;

import java.util.List;

import models.MAComponent;
import models.MAExperiment;
import models.MAUser;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import views.html.admin.component_create;
import views.html.admin.component_update;
import views.html.admin.experiment_create;
import views.html.admin.experiment_update;
import views.html.admin.index;
import views.html.admin.login;

public class Admin extends Controller {

	public static final String COOKIE_EMAIL = "email";

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result index() {
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
		return ok(index.render(experimentList, null, user));
	}

	public static Result login() {
		return ok(login.render(Form.form(Login.class)));
	}

	@Transactional
	public static Result authenticate() {
		Form<Login> loginForm = Form.form(Login.class).bindFromRequest();
		if (loginForm.hasErrors()) {
			return badRequest(login.render(loginForm));
		} else {
			session(COOKIE_EMAIL, loginForm.get().email);
			return redirect(routes.Admin.index());
		}
	}

	@Security.Authenticated(Secured.class)
	public static Result logout() {
		session().remove(COOKIE_EMAIL);
		flash("success", "You've been logged out");
		return redirect(routes.Admin.login());
	}

	// *** Experiment ***

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result experiment(Long experimentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.isMember(user)) {
			return forbiddenNotMember(user, experiment, experimentList);
		}

		return ok(views.html.admin.experiment.render(experimentList, null,
				user, experiment));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result createExperiment() {
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
		return ok(experiment_create.render(experimentList, null, user,
				Form.form(MAExperiment.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitExperiment() {
		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			List<MAExperiment> experimentList = MAExperiment.findAll();
			MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
			return badRequest(experiment_create.render(experimentList, null,
					user, form));
		} else {
			MAExperiment experiment = form.get();
			MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
			experiment.addMember(user);
			experiment.persist();
			return redirect(routes.Admin.experiment(experiment.id));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result updateExperiment(Long experimentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.isMember(user)) {
			return forbiddenNotMember(user, experiment, experimentList);
		}

		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.fill(experiment);
		return ok(experiment_update.render(experimentList, experiment, null,
				user, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdatedExperiment(Long experimentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.isMember(user)) {
			return forbiddenNotMember(user, experiment, experimentList);
		}

		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(experiment_update.render(experimentList,
					experiment, null, user, form));
		}

		DynamicForm requestData = Form.form().bindFromRequest();
		experiment.title = requestData.get("title");
		experiment.merge();
		return redirect(routes.Admin.experiment(experimentId));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result deleteExperiment(Long experimentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.isMember(user)) {
			return forbiddenNotMember(user, experiment, experimentList);
		}

		experiment.remove();
		return redirect(routes.Admin.index());
	}

	// *** Component ***

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result component(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
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

		return ok(views.html.admin.component.render(experimentList, experiment,
				null, user, component));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result createComponent(Long experimentId)
			throws NumberFormatException {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.isMember(user)) {
			return forbiddenNotMember(user, experiment, experimentList);
		}

		return ok(component_create.render(experimentList, experiment, null,
				user, Form.form(MAComponent.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitComponent(Long experimentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
		if (experiment == null) {
			return badRequestExperimentNotExist(experimentId, user,
					experimentList);
		}
		if (!experiment.isMember(user)) {
			return forbiddenNotMember(user, experiment, experimentList);
		}

		Form<MAComponent> form = Form.form(MAComponent.class).bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(component_create.render(experimentList,
					experiment, null, user, form));
		} else {
			MAComponent component = form.get();
			component.experiment = experiment;
			component.persist();
			return redirect(routes.Admin.component(experiment.id, component.id));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result updateComponent(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
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
		return ok(component_update.render(experimentList, component,
				experiment, null, user, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdatedComponent(Long experimentId,
			Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
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
			return badRequest(component_update.render(experimentList,
					component, experiment, null, user, form));
		}

		DynamicForm requestData = Form.form().bindFromRequest();
		component.title = requestData.get("title");
		component.setJsonData(requestData.get("jsonData"));
		component.merge();
		return redirect(routes.Admin.component(experiment.id, componentId));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result deleteComponent(Long experimentId, Long componentId) {
		MAExperiment experiment = MAExperiment.findById(experimentId);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
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

	private static Result badRequestExperimentNotExist(Long experimentId,
			MAUser user, List<MAExperiment> experimentList) {
		String errorMsg = "An experiment with id " + experimentId
				+ " doesn't exist.";
		return badRequest(index.render(experimentList, errorMsg, user));
	}

	private static Result badRequestComponentNotExist(Long componentId,
			MAExperiment experiment, MAUser user,
			List<MAExperiment> experimentList) {
		String errorMsg = "An component with id " + componentId
				+ " doesn't exist.";
		return badRequest(index.render(experimentList, errorMsg, user));
	}

	private static Result badRequestComponentNotBelongToExperiment(
			MAExperiment experiment, MAComponent component, MAUser user,
			List<MAExperiment> experimentList) {
		String errorMsg = "There is no experiment with id " + experiment.id
				+ " that has a component with id " + component.id + ".";
		return badRequest(index.render(experimentList, errorMsg, user));
	}

	private static Result forbiddenNotMember(MAUser user,
			MAExperiment experiment, List<MAExperiment> experimentList) {
		String errorMsg = user.name + " (" + user.email
				+ ") isn't member of experiment " + experiment.id + " \""
				+ experiment.title + "\".";
		return forbidden(views.html.admin.index.render(experimentList,
				errorMsg, user));
	}

	/**
	 * Inner class needed for authentication
	 */
	public static class Login {
		public String email;
		public String password;

		public String validate() {
			if (MAUser.authenticate(email, password) == null) {
				return "Invalid user or password";
			}
			return null;
		}
	}

}
