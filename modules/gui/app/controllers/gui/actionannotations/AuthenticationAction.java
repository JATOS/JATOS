package controllers.gui.actionannotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.inject.Provider;

import controllers.gui.Authentication;
import controllers.gui.Home;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import daos.common.UserDao;
import general.common.RequestScope;
import general.gui.RequestScopeMessaging;
import models.common.User;
import models.common.User.Role;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import utils.common.HttpUtils;

/**
 * For all actions in a controller that are annotated with @Authenticated check
 * authentication. An action is authenticated if there is an email in the
 * session that correspondents to a user in the database. If successful the User
 * is stored in the RequestScope.
 * 
 * It also checks whether the user is authorized to access this action. For this
 * it checks the user role (optional in the Authenticated annotation).
 * 
 * @author Kristian Lange
 */
public class AuthenticationAction extends Action<Authenticated> {

	/**
	 * This Annotation can be used on every method where authentication or
	 * authorization is required. If no Role is added than the default Role
	 * 'USER' is assumed.
	 */
	@With(AuthenticationAction.class)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Authenticated {
		Role value() default Role.USER;
	}

	private static final ALogger LOGGER = Logger.of(AuthenticationAction.class);

	private final UserDao userDao;
	private final Provider<Home> homeProvider;

	@Inject
	AuthenticationAction(UserDao userDao, Provider<Home> homeProvider) {
		this.userDao = userDao;
		this.homeProvider = homeProvider;
	}

	public CompletionStage<Result> call(Http.Context ctx) {
		String email = ctx.session().get(Authentication.SESSION_USER_EMAIL);

		// For authentication it's actually enough to check that the email is in
		// Play's session. Play's session is safe from tempering. But we
		// retrieve the user from the database and put it into our RequestScope.
		// We need it later anyway and storing it in the RequestScope now
		// saves us some database requests later.
		User loggedInUser = null;
		if (email != null) {
			loggedInUser = userDao.findByEmail(email);
		}
		if (loggedInUser == null) {
			return callForbiddenDueToAuthentication();
		}
		RequestScope.put(Authentication.LOGGED_IN_USER, loggedInUser);

		// We are authenticated, but are we authorized?
		Role neededRole = configuration.value();
		if (loggedInUser.hasRole(neededRole)) {
			// Everything OK
			return delegate.call(ctx);
		} else {
			return callForbiddenDueToAuthorization(loggedInUser.getEmail(),
					ctx.request().path());
		}
	}

	private CompletionStage<Result> callForbiddenDueToAuthentication() {
		if (HttpUtils.isAjax()) {
			return CompletableFuture
					.completedFuture(forbidden("Not logged in"));
		} else {
			return CompletableFuture.completedFuture(
					redirect(controllers.gui.routes.Authentication.login()));
		}
	}

	private CompletionStage<Result> callForbiddenDueToAuthorization(
			String userEmail, String path) {
		String message = "User " + userEmail + " isn't allowed to access page "
				+ path;
		LOGGER.warn(message);
		if (HttpUtils.isAjax()) {
			return CompletableFuture.completedFuture(forbidden(message));
		} else {
			RequestScopeMessaging.error(message);
			return CompletableFuture.completedFuture(
					homeProvider.get().home(Http.Status.FORBIDDEN));
		}
	}

}
