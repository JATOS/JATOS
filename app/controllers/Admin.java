package controllers;

import java.io.IOException;
import java.util.List;

import models.MAExperiment;
import models.MAUser;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import views.html.admin.change_experiment;
import views.html.admin.create_experiment;
import views.html.admin.index;
import views.html.admin.login;

public class Admin extends Controller {

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result index() {
		List<MAExperiment> experimentList = MAExperiment.findAll();
		return ok(index.render(experimentList, null));
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
			session().clear();
			session("email", loginForm.get().email);
			return redirect(routes.Admin.index());
		}
	}

	@Security.Authenticated(Secured.class)
	public static Result logout() {
		session().clear();
		flash("success", "You've been logged out");
		return redirect(routes.Admin.login());
	}

	@Security.Authenticated(Secured.class)
	public static Result createExperiment() {
		return ok(create_experiment.render(Form.form(MAExperiment.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result changeExperiment(Long id) {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestNotExist(id);
		}
		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.fill(experiment);
		return ok(change_experiment.render(form, id));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result experiment(Long id) {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestNotExist(id);
		}
		return ok(views.html.admin.experiment.render(experiment));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitExperiment() {
		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(create_experiment.render(form));
		} else {
			MAExperiment experiment = form.get();
			experiment.persist();
			return redirect(routes.Admin.index());
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitChangedExperiment(Long id) throws IOException {
		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(change_experiment.render(form, id));
		}

		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestNotExist(id);
		}

		DynamicForm requestData = Form.form().bindFromRequest();
		experiment.title = requestData.get("title");
		experiment.setData(requestData.get("data"));
		experiment.merge();
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
		return badRequest(index.render(experimentList, error));
	}

}
