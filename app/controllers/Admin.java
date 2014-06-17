package controllers;

import java.util.List;

import models.MAExperiment;
import models.MAUser;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
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
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
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

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result experiment(Long id) {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestNotExist(id);
		}
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return ok(views.html.admin.experiment.render(experimentList, null,
				user, experiment));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result createExperiment() {
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
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
			MAUser user = MAUser.findById(session(COOKIE_EMAIL));
			return badRequest(experiment_create.render(experimentList, null,
					user, form));
		} else {
			MAExperiment experiment = form.get();
			MAUser user = MAUser.findById(session(COOKIE_EMAIL));
			experiment.creator = user.toString();
			experiment.persist();
			return redirect(routes.Admin.experiment(experiment.id));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result updateExperiment(Long id) {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestNotExist(id);
		}
		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.fill(experiment);
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return ok(experiment_update
				.render(experimentList, null, user, form, id));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdatedExperiment(Long id) {
		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			List<MAExperiment> experimentList = MAExperiment.findAll();
			MAUser user = MAUser.findById(session(COOKIE_EMAIL));
			return badRequest(experiment_update.render(experimentList, null,
					user, form, id));
		}

		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestNotExist(id);
		}

		DynamicForm requestData = Form.form().bindFromRequest();
		experiment.title = requestData.get("title");
		experiment.setData(requestData.get("data"));
		experiment.view = requestData.get("view");
		experiment.merge();
		return redirect(routes.Admin.experiment(id));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result deleteExperiment(Long id) {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestNotExist(id);
		}

		experiment.remove();

		return redirect(routes.Admin.index());
	}

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

	private static Result badRequestNotExist(Long id) {
		List<MAExperiment> experimentList = MAExperiment.findAll();
		String error = "An experiment with id " + id + " doesn't exist.";
		MAUser user = MAUser.findById(session(COOKIE_EMAIL));
		return badRequest(index.render(experimentList, error, user));
	}

}
