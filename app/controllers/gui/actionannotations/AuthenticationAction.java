package controllers.gui.actionannotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import models.UserModel;
import persistance.UserDao;
import play.libs.F;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.SimpleResult;
import play.mvc.With;

import com.google.inject.Inject;
import common.RequestScope;

import controllers.gui.Authentication;
import controllers.gui.Users;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;

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

	public F.Promise<SimpleResult> call(Http.Context ctx) throws Throwable {
		Promise<SimpleResult> call;
		String email = Controller.session(Users.SESSION_EMAIL);
		UserModel loggedInUser = null;
		if (email != null) {
			loggedInUser = userDao.findByEmail(email);
		}
		if (loggedInUser == null) {
			call = Promise
					.<SimpleResult> pure(redirect(controllers.gui.routes.Authentication
							.login()));
		} else {
			RequestScope.put(Authentication.LOGGED_IN_USER, loggedInUser);
			call = delegate.call(ctx);
		}
		return call;
	}

}
