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
		List<MAComponent> componentList = MAComponent.findAll();
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return ok(index.render(componentList, null, user));
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
			// session().clear();
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
	public static Result experiment(Long id) {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestExperimentNotExist(id);
		}
		List<MAComponent> componentList = MAComponent.findAll();
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return ok(views.html.admin.experiment.render(componentList, null,
				user, experiment));
	}
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result createExperiment() {
		List<MAComponent> componentList = MAComponent.findAll();
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return ok(experiment_create.render(componentList, null, user,
				Form.form(MAExperiment.class)));
	}
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitExperiment() {
		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			List<MAComponent> componentList = MAComponent.findAll();
			MAUser user = MAUser.findById(session(COOKIE_EMAIL));
			return badRequest(experiment_create.render(componentList, null,
					user, form));
		} else {
			MAExperiment experiment = form.get();
			MAUser user = MAUser.findById(session(COOKIE_EMAIL));
			experiment.author = user.toString();
			experiment.persist();
			return redirect(routes.Admin.experiment(experiment.id));
		}
	}
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result updateExperiment(Long id) {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestExperimentNotExist(id);
		}
		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.fill(experiment);
		List<MAComponent> componentList = MAComponent.findAll();
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return ok(experiment_update
				.render(componentList, null, user, form, id));
	}
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdatedExperiment(Long id) {
		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			List<MAComponent> componentList = MAComponent.findAll();
			MAUser user = MAUser.findById(session(COOKIE_EMAIL));
			return badRequest(experiment_update.render(componentList, null,
					user, form, id));
		}

		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestExperimentNotExist(id);
		}

		DynamicForm requestData = Form.form().bindFromRequest();
		experiment.title = requestData.get("title");
		experiment.merge();
		return redirect(routes.Admin.experiment(id));
	}
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result deleteExperiment(Long id) {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestExperimentNotExist(id);
		}
		experiment.remove();
		return redirect(routes.Admin.index());
	}
	
	private static Result badRequestExperimentNotExist(Long id) {
		List<MAComponent> componentList = MAComponent.findAll();
		String error = "An experiment with id " + id + " doesn't exist.";
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return badRequest(index.render(componentList, error, user));
	}

	// *** Component ***
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result component(Long id) {
		MAComponent component = MAComponent.findById(id);
		if (component == null) {
			return badRequestComponentNotExist(id);
		}
		List<MAComponent> componentList = MAComponent.findAll();
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return ok(views.html.admin.component.render(componentList, null,
				user, component));
	}
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result createComponent() {
		List<MAComponent> componentList = MAComponent.findAll();
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return ok(component_create.render(componentList, null, user,
				Form.form(MAComponent.class)));
	}
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitComponent() {
		Form<MAComponent> form = Form.form(MAComponent.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			List<MAComponent> componentList = MAComponent.findAll();
			MAUser user = MAUser.findById(session(COOKIE_EMAIL));
			return badRequest(component_create.render(componentList, null,
					user, form));
		} else {
			MAComponent component = form.get();
			MAUser user = MAUser.findById(session(COOKIE_EMAIL));
			component.author = user.toString();
			component.persist();
			return redirect(routes.Admin.component(component.id));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result updateComponent(Long id) {
		MAComponent component = MAComponent.findById(id);
		if (component == null) {
			return badRequestComponentNotExist(id);
		}
		Form<MAComponent> form = Form.form(MAComponent.class)
				.fill(component);
		List<MAComponent> componentList = MAComponent.findAll();
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return ok(component_update
				.render(componentList, null, user, form, id));
	}
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdatedComponent(Long id) {
		Form<MAComponent> form = Form.form(MAComponent.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			List<MAComponent> componentList = MAComponent.findAll();
			MAUser user = MAUser.findById(session(COOKIE_EMAIL));
			return badRequest(component_update.render(componentList, null,
					user, form, id));
		}

		MAComponent component = MAComponent.findById(id);
		if (component == null) {
			return badRequestComponentNotExist(id);
		}

		DynamicForm requestData = Form.form().bindFromRequest();
		component.title = requestData.get("title");
		component.setData(requestData.get("data"));
		component.view = requestData.get("view");
		component.merge();
		return redirect(routes.Admin.component(id));
	}
	
	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result deleteComponent(Long id) {
		MAComponent component = MAComponent.findById(id);
		if (component == null) {
			return badRequestComponentNotExist(id);
		}
		component.remove();
		return redirect(routes.Admin.index());
	}
	
	private static Result badRequestComponentNotExist(Long id) {
		List<MAComponent> componentList = MAComponent.findAll();
		String error = "An component with id " + id + " doesn't exist.";
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return badRequest(index.render(componentList, error, user));
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
