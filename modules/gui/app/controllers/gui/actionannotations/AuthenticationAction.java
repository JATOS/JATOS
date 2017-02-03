package controllers.gui.actionannotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;

import controllers.gui.Authentication;
import controllers.gui.Users;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import daos.common.UserDao;
import general.common.RequestScope;
import models.common.User;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import utils.common.HttpUtils;

/**
 * For all actions in a controller that are annotated with @Authenticated check
 * authentication. An action is authenticated if there is an email in the
 * session that correspondents to a valid user. If successful the User is stored
 * in the RequestScope.
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

	public CompletionStage<Result> call(Http.Context ctx) {
		CompletionStage<Result> call;
		String email = ctx.session().get(Users.SESSION_EMAIL);
		User loggedInUser = null;
		if (email != null) {
			loggedInUser = userDao.findByEmail(email);
		}
		if (loggedInUser == null) {
			if (HttpUtils.isAjax()) {
				call = CompletableFuture
						.completedFuture(forbidden("Not logged in"));
			} else {
				call = CompletableFuture.completedFuture(redirect(
						controllers.gui.routes.Authentication.login()));
			}
		} else {
			RequestScope.put(Authentication.LOGGED_IN_USER, loggedInUser);
			call = delegate.call(ctx);
		}
		return call;
	}

}
