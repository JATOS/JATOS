package controllers.actionannotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.inject.Inject;

import models.UserModel;
import persistance.UserDao;
import play.libs.F;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;

import common.RequestScope;

import controllers.Authentication;
import controllers.Users;
import controllers.actionannotations.AuthenticationAction.Authenticated;

/**
 * For all actions in a controller that are annotated with @Authenticated check
 * authentication. An action is authenticated if there is an email in the
 * session that correspondents to a valid user. If successful the UserModel is
 * stored in the RequestScope.
 *
 * @author Kristian Lange
 */
public class AuthenticationAction extends Action<Authenticated> {

	@With(AuthenticationAction.class)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Authenticated {
	}

	private final UserDao userDao;

	@Inject
	AuthenticationAction(UserDao userService) {
		this.userDao = userService;
	}

	public F.Promise<Result> call(Http.Context ctx) throws Throwable {
		Promise<Result> call;
		String email = ctx.session().get(Users.SESSION_EMAIL);
		UserModel loggedInUser = null;
		if (email != null) {
			loggedInUser = userDao.findByEmail(email);
		}
		if (loggedInUser == null) {
			call = Promise.pure(redirect(controllers.routes.Authentication
					.login()));
		} else {
			RequestScope.put(Authentication.LOGGED_IN_USER, loggedInUser);
			call = delegate.call(ctx);
		}
		return call;
	}

}
