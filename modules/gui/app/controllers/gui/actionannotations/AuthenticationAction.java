package controllers.gui.actionannotations;

import controllers.gui.Authentication;
import controllers.gui.Home;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import general.common.Common;
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
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * This class defines the @Authenticated annotation used in JATOS GUI
 * controllers. It checks Play's session cookie and the cached user session.
 * Additionally it does authorization. It has several layers of security:
 * <p>
 * 1) First it checks if an username is in Play's session cookie and if this username
 * belongs to a user in the database.
 * <p>
 * 2) We check whether the session ID stored in Play's session cookie is the
 * same as stored in the UserSession in the cache. After a user logs out this
 * session ID is deleted in the cache and from the session cookie and thus
 * subsequent log-ins will fail.
 * <p>
 * 3) Check if the session timed out. The time span is defined in the
 * application.conf.
 * <p>
 * 4) Check if the session timed out due to inactivity of the user. With each
 * request by the user the time of last activity gets refreshed in the session.
 * <p>
 * 5) Check if the logged-in user has the proper Role needed to access this
 * page. This Role is an optional parameter in the @Authenticated annotation.
 * <p>
 * The @Authenticated annotation does not check the user's password. This is
 * done once during login (class {@link Authentication}).
 * <p>
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
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Authenticated {
        Role value() default Role.USER;
    }

    private static final ALogger LOGGER = Logger.of(AuthenticationAction.class);

    private final Provider<Home> homeProvider;
    private final AuthenticationService authenticationService;

    @Inject
    AuthenticationAction(Provider<Home> homeProvider, AuthenticationService authenticationService) {
        this.homeProvider = homeProvider;
        this.authenticationService = authenticationService;
    }

    public CompletionStage<Result> call(Http.Context ctx) {
        // For authentication it's actually enough to check that the username is in Play's session. Play's session
        // is safe from tempering. But we retrieve the user from the database and put it into our RequestScope
        // since we need it later anyway. Storing it in the RequestScope now saves us some database requests later.
        User loggedInUser = authenticationService.getLoggedInUserBySessionCookie(ctx.session());
        if (loggedInUser == null) {
            authenticationService.clearSessionCookie(ctx.session());
            return callForbiddenDueToAuthentication(ctx.request().remoteAddress(), ctx.request().path());
        }
        RequestScope.put(AuthenticationService.LOGGED_IN_USER, loggedInUser);

        // Check user's session ID (only if not switched off in configuration)
        if (Common.isUserSessionValidation() && !authenticationService.isValidSessionId(ctx.session(),
                        loggedInUser.getUsername(), ctx.request().remoteAddress())) {
            authenticationService.clearSessionCookie(ctx.session());
            return callForbiddenDueToInvalidSession(loggedInUser.getUsername(),
                    ctx.request().remoteAddress(), ctx.request().path());
        }

        // Check session timeout
        if (authenticationService.isSessionTimeout(ctx.session())) {
            authenticationService.clearSessionCookieAndSessionCache(
                    ctx.session(), loggedInUser.getUsername(), ctx.request().remoteAddress());
            return callForbiddenDueToSessionTimeout(loggedInUser.getUsername());
        }

        // Check inactivity timeout
        if (authenticationService.isInactivityTimeout(ctx.session())) {
            authenticationService.clearSessionCookieAndSessionCache(
                    ctx.session(), loggedInUser.getUsername(), ctx.request().remoteAddress());
            return callForbiddenDueToInactivityTimeout(loggedInUser.getUsername());
        }

        authenticationService.refreshSessionCookie(ctx.session());

        // Check authorization
        if (!isAuthorized(loggedInUser)) {
            return callForbiddenDueToAuthorization(loggedInUser.getUsername(), ctx.request().path());
        }

        // Everything ok: authenticated and authorized
        return delegate.call(ctx);
    }

    private boolean isAuthorized(User loggedInUser) {
        // configuration.value() contains the Role parameter of @Authenticated
        Role neededRole = configuration.value();
        return loggedInUser.hasRole(neededRole);
    }

    private CompletionStage<Result> callForbiddenDueToAuthentication(String remoteAddress, String urlPath) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress + " tried to access page " + urlPath);
        String msg = "You are not allowed to access this page. Please log in.";
        if (Helpers.isAjax()) {
            return CompletableFuture.completedFuture(forbidden(msg));
        }
        if (!urlPath.isEmpty() && !urlPath.matches("(/|/jatos|/jatos/)")) {
            FlashScopeMessaging.error(msg);
        }
        return CompletableFuture.completedFuture(redirect(controllers.gui.routes.Authentication.login()));
    }

    private CompletionStage<Result> callForbiddenDueToInvalidSession(String normalizedUsername, String remoteAddress,
            String urlPath) {
        LOGGER.warn("Invalid session: user " + normalizedUsername + " tried to access page " + urlPath +
                " from remote address " + remoteAddress + ".");
        String msg = "You have been logged out.";
        if (Helpers.isAjax()) {
            return CompletableFuture.completedFuture(forbidden(msg));
        } else {
            FlashScopeMessaging.warning(msg);
            return CompletableFuture.completedFuture(redirect(controllers.gui.routes.Authentication.login()));
        }
    }

    private CompletionStage<Result> callForbiddenDueToSessionTimeout(String normalizedUsername) {
        LOGGER.info("Session of user " + normalizedUsername + " has expired and the user has been logged out.");
        String msg = "Your session has expired. You have been logged out.";
        if (Helpers.isAjax()) {
            return CompletableFuture.completedFuture(forbidden(msg));
        } else {
            FlashScopeMessaging.success(msg);
            return CompletableFuture.completedFuture(redirect(controllers.gui.routes.Authentication.login()));
        }
    }

    private CompletionStage<Result> callForbiddenDueToInactivityTimeout(String normalizedUsername) {
        LOGGER.info("User " + normalizedUsername + " has been logged out due to inactivity.");
        String msg = "You have been logged out due to inactivity.";
        if (Helpers.isAjax()) {
            return CompletableFuture.completedFuture(forbidden(msg));
        } else {
            FlashScopeMessaging.success(msg);
            return CompletableFuture.completedFuture(redirect(controllers.gui.routes.Authentication.login()));
        }
    }

    private CompletionStage<Result> callForbiddenDueToAuthorization(String normalizedUsername, String urlPath) {
        String msg = "User " + normalizedUsername + " isn't allowed to access page " + urlPath + ".";
        LOGGER.warn(msg);
        if (Helpers.isAjax()) {
            return CompletableFuture.completedFuture(forbidden(msg));
        } else {
            RequestScopeMessaging.error(msg);
            return CompletableFuture.completedFuture(homeProvider.get().home(Http.Status.FORBIDDEN));
        }
    }

}
