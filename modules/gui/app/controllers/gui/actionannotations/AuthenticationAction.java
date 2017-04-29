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
import general.common.RequestScope;
import general.gui.FlashScopeMessaging;
import general.gui.RequestScopeMessaging;
import models.common.User;
import models.common.User.Role;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.gui.AuthenticationService;
import utils.common.HttpUtils;

/**
 * This class defines the @Authenticated annotation used in JATOS GUI
 * controllers. It checks Play's session cookie and the cached user session.
 * Additionally it does authorization. It has several layers of security:
 * 
 * 1) First it checks if an email is in Play's session cookie and if this email
 * belongs to a user in the database.
 * 
 * 2) We check whether the session ID stored in Play's session cookie is the
 * same as stored in the UserSession in the cache. After a user logs out this
 * session ID is deleted in the cache and from the session cookie and thus
 * subsequent log-ins will fail.
 * 
 * 3) Check if the session timed out. The time span is defined in the
 * application.conf.
 * 
 * 4) Check if the session timed out due to inactivity of the user. With each
 * request by the user the time of last activity gets refreshed in the session.
 * 
 * 5) Check if the logged-in user has the proper Role needed to access this
 * page. This Role is an optional parameter in the @Authenticated annotation.
 * 
 * The @Authenticated annotation does not check the user's password. This is
 * done once during login (class {@link Authentication}).
 * 
 * IMPORTANT: Since this annotation accesses the database the annotated method
 * has to be within a transaction. This means the @Transactional annotation has
 * to be BEFORE the @Authenticated annotation.
 * 
 * @author Kristian Lange (2015 - 2017)
 */
public class AuthenticationAction extends Action<Authenticated> {

	/**
	 * This @Authenticated annotation can be used on every controller action
	 * where authentication and authorization is required. If no Role is added
	 * than the default Role 'USER' is assumed.
	 */
	@With(AuthenticationAction.class)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Authenticated {
		Role value() default Role.USER;
	}

	private static final ALogger LOGGER = Logger.of(AuthenticationAction.class);

	private final Provider<Home> homeProvider;
	private final AuthenticationService authenticationService;

	@Inject
	AuthenticationAction(Provider<Home> homeProvider,
			AuthenticationService authenticationService) {
		this.homeProvider = homeProvider;
		this.authenticationService = authenticationService;
	}

	public CompletionStage<Result> call(Http.Context ctx) {
		// For authentication it's actually enough to check that the email is in
		// Play's session. Play's session is safe from tempering. But we
		// retrieve the user from the database and put it into our RequestScope
		// since we need it later anyway. Storing it in the RequestScope now
		// saves us some database requests later.
		User loggedInUser = authenticationService
				.getLoggedInUserBySessionCookie(ctx.session());
		if (loggedInUser == null) {
			authenticationService.clearSessionCookie(ctx.session());
			return callForbiddenDueToAuthentication(ctx.request().remoteAddress(),
					ctx.request().path());
		}
		RequestScope.put(AuthenticationService.LOGGED_IN_USER, loggedInUser);

		// Check user's session ID
		if (!authenticationService.isValidSessionId(ctx.session(),
				loggedInUser.getEmail(), ctx.request().remoteAddress())) {
			authenticationService.clearSessionCookie(ctx.session());
			return callForbiddenDueToInvalidSession(loggedInUser.getEmail(),
					ctx.request().remoteAddress(), ctx.request().path());
		}

		// Check session timeout
		if (authenticationService.isSessionTimeout(ctx.session())) {
			authenticationService.clearSessionCookieAndSessionCache(
					ctx.session(), loggedInUser.getEmail(),
					ctx.request().remoteAddress());
			return callForbiddenDueToSessionTimeout(loggedInUser.getEmail());
		}

		// Check inactivity timeout
		if (authenticationService.isInactivityTimeout(ctx.session())) {
			authenticationService.clearSessionCookieAndSessionCache(
					ctx.session(), loggedInUser.getEmail(),
					ctx.request().remoteAddress());
			return callForbiddenDueToInactivityTimeout(loggedInUser.getEmail());
		}

		authenticationService.refreshSessionCookie(ctx.session());

		// Check authorization
		if (!isAuthorized(loggedInUser)) {
			return callForbiddenDueToAuthorization(loggedInUser.getEmail(),
					ctx.request().path());
		}

		// Everything ok: authenticated and authorized
		return delegate.call(ctx);
	}

	private boolean isAuthorized(User loggedInUser) {
		// configuration.value() contains the Role parameter of @Authenticated
		Role neededRole = configuration.value();
		return loggedInUser.hasRole(neededRole);
	}

	private CompletionStage<Result> callForbiddenDueToAuthentication(
			String remoteAddress, String urlPath) {
		LOGGER.warn("Authentication failed: remote address " + remoteAddress
				+ " tried to access page " + urlPath);
		if (HttpUtils.isAjax()) {
			return CompletableFuture
					.completedFuture(forbidden("Not logged in"));
		} else {
			FlashScopeMessaging.error(
					"You are not allowed to access this page. Please log in.");
			return CompletableFuture.completedFuture(
					redirect(controllers.gui.routes.Authentication.login()));
		}
	}

	private CompletionStage<Result> callForbiddenDueToInvalidSession(
			String userEmail, String remoteAddress, String urlPath) {
		LOGGER.warn("Invalid session: user " + userEmail
				+ " tried to access page " + urlPath + " from remote address "
				+ remoteAddress + ".");
		if (HttpUtils.isAjax()) {
			return CompletableFuture
					.completedFuture(forbidden("Invalid session"));
		} else {
			FlashScopeMessaging.warning("You have been logged out.");
			return CompletableFuture.completedFuture(
					redirect(controllers.gui.routes.Authentication.login()));
		}
	}

	private CompletionStage<Result> callForbiddenDueToSessionTimeout(
			String userEmail) {
		LOGGER.info("Session of user " + userEmail
				+ " has expired and the user has been logged out.");
		if (HttpUtils.isAjax()) {
			return CompletableFuture
					.completedFuture(forbidden("Session timeout"));
		} else {
			FlashScopeMessaging.success(
					"Your session has expired. You have been logged out.");
			return CompletableFuture.completedFuture(
					redirect(controllers.gui.routes.Authentication.login()));
		}
	}

	private CompletionStage<Result> callForbiddenDueToInactivityTimeout(
			String userEmail) {
		LOGGER.info("User " + userEmail
				+ " has been logged out due to inactivity.");
		if (HttpUtils.isAjax()) {
			return CompletableFuture
					.completedFuture(forbidden("Inactivity timeout"));
		} else {
			FlashScopeMessaging
					.success("You have been logged out due to inactivity.");
			return CompletableFuture.completedFuture(
					redirect(controllers.gui.routes.Authentication.login()));
		}
	}

	private CompletionStage<Result> callForbiddenDueToAuthorization(
			String userEmail, String urlPath) {
		String message = "User " + userEmail + " isn't allowed to access page "
				+ urlPath + ".";
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
