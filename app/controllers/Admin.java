package controllers;

import java.io.IOException;
import java.util.List;

import models.MAExperiment;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.admin.change_experiment;
import views.html.admin.create_experiment;
import views.html.admin.index;

public class Admin extends Controller {

	@Transactional
	public static Result index() {
		List<MAExperiment> experimentList = MAExperiment.findAll();
		return ok(index.render(experimentList, null));
	}

	public static Result createExperiment() {
		return ok(create_experiment.render(Form.form(MAExperiment.class)));
	}

	@Transactional
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
	public static Result experiment(Long id) {
		MAExperiment experiment = MAExperiment.findById(id);
		if (experiment == null) {
			return badRequestNotExist(id);
		}
		return ok(views.html.admin.experiment.render(experiment));
	}

	@Transactional
	public static Result submitExperiment() {
		Form<MAExperiment> form = Form.form(MAExperiment.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(create_experiment.render(form));
		} else {
			MAExperiment experiment = form.get();
			experiment.persist();
			return redirect("/admin/");
		}
	}

	@Transactional
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
		return redirect("/admin/");
	}
	
	private static Result badRequestNotExist(Long id) {
		List<MAExperiment> experimentList = MAExperiment.findAll();
		String error = "An experiment with id " + id + " doesn't exist.";
		return badRequest(index.render(experimentList, error));
	}

}
