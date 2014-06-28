package controllers;

import java.util.ArrayList;
import java.util.List;

import models.MAExperiment;
import models.MAUser;
import play.data.DynamicForm;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Result;
import play.mvc.Security;

public class Users extends MAController {

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result get(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, experimentList);
		}

		return ok(views.html.admin.user.user.render(experimentList, null,
				loggedInUser, user));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result create() {
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		return ok(views.html.admin.user.create.render(experimentList, null,
				loggedInUser, Form.form(MAUser.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submit() {
		Form<MAUser> form = Form.form(MAUser.class).bindFromRequest();
		if (form.hasErrors()) {
			List<MAExperiment> experimentList = MAExperiment.findAll();
			MAUser loggedInUser = MAUser
					.findByEmail(session(MAController.COOKIE_EMAIL));
			return badRequest(views.html.admin.user.create.render(
					experimentList, null, loggedInUser, form));
		}

		MAUser newUser = form.get();
		// Check if user with this email already exists.
		if (MAUser.findByEmail(newUser.email) != null) {
			List<ValidationError> errorList = new ArrayList<ValidationError>();
			errorList.add(new ValidationError("email",
					"This e-mail is already registered."));
			form.errors().put("email", errorList);
			List<MAExperiment> experimentList = MAExperiment.findAll();
			MAUser loggedInUser = MAUser
					.findByEmail(session(MAController.COOKIE_EMAIL));
			return badRequest(views.html.admin.user.create.render(
					experimentList, null, loggedInUser, form));
		}

		newUser.persist();
		return redirect(routes.Users.get(newUser.email));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result update(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, experimentList);
		}
		if (user.email != loggedInUser.email) {
			String errorMsg = "You must be logged in as " + user.toString()
					+ " to update this user.";
			return badRequest(views.html.admin.user.user.render(experimentList,
					errorMsg, loggedInUser, user));
		}

		Form<MAUser> form = Form.form(MAUser.class).fill(user);
		return ok(views.html.admin.user.update.render(experimentList, user,
				null, loggedInUser, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdated(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, experimentList);
		}

		Form<MAUser> form = Form.form(MAUser.class).bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(views.html.admin.user.update.render(
					experimentList, user, null, loggedInUser, form));
		}

		DynamicForm requestData = Form.form().bindFromRequest();
		// Do not update 'email' since it's the id and should stay unaltered.
		user.name = requestData.get("name");
		user.password = requestData.get("password");
		user.merge();
		return redirect(routes.Users.get(email));
	}

}
