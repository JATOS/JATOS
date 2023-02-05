package auth.gui;

import controllers.gui.Home;
import general.common.RequestScope;
import general.gui.FlashScopeMessaging;
import general.gui.RequestScopeMessaging;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Http;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Provider;

import static play.mvc.Results.forbidden;
import static play.mvc.Results.redirect;

/**
 * This class defines authentication via session cookies (which is the default authentication in the Play Framework).
 * <p>
 * It checks Play's session cookie and the cached user session. Additionally, it does authorization. It sets the user's
 * lastSeen time. It has several layers of security:
 * <p>
 * 1) First it checks if a username is in Play's session cookie and if this username belongs to a user in the database.
 * <p>
 * 2) We check whether the session ID stored in Play's session cookie is the same as stored in the UserSession in the
 * cache. After a user logs out this session ID is deleted in the cache and from the session cookie and thus subsequent
 * log-ins will fail.
 * <p>
 * 3) Check if the session timed out. The time span is defined in the application.conf.
 * <p>
 * 4) Check if the session timed out due to inactivity of the user. With each request by the user the time of last
 * activity gets refreshed in the session.
 * <p>
 * 5) Check if the logged-in user has the proper Role needed to access this page. This Role is an optional parameter in
 * the {@link AuthAction.Auth} annotation.
 * <p>
 * 6) It checks if the user was deactivated by an admin.
 * <p>
 * The {@link AuthAction.Auth} annotation does not check the user's password. This is
 * done once during login (class {@link SignIn}).
 * <p>
 * The {@link User} object is put in the {@link RequestScope} for later use during request processing.
 *
 * @author Kristian Lange
 */
public class AuthActionSessionCookie implements AuthAction.IAuth {

    private static final ALogger LOGGER = Logger.of(AuthActionSessionCookie.class);

    private final Provider<Home> homeProvider;
    private final AuthService authenticationService;

    @Inject
    AuthActionSessionCookie(Provider<Home> homeProvider, AuthService authenticationService) {
        this.homeProvider = homeProvider;
        this.authenticationService = authenticationService;
    }

    public AuthResult authenticate(Http.Request request, User.Role role) {

        if (!Helpers.isSessionCookieRequest(request)) {
            return AuthResult.of(false);
        }

        // For authentication, it's actually enough to check that the username is in Play's session. Play's session
        // is safe from tempering. But we retrieve the user from the database and put it into our RequestScope
        // since we need it later anyway. Storing it in the RequestScope now saves us some database requests later.
        User loggedInUser = authenticationService.getLoggedInUserBySessionCookie(request.session());
        if (loggedInUser == null) {
            authenticationService.clearSessionCookie(request.session());
            return callForbiddenDueToAuthentication(request.remoteAddress(), request.path());
        }
        RequestScope.put(AuthService.LOGGED_IN_USER, loggedInUser);

        // Check session timeout
        if (authenticationService.isSessionTimeout(request.session())) {
            authenticationService.clearSessionCookie(request.session());
            return callForbiddenDueToSessionTimeout(loggedInUser.getUsername());
        }

        // Check inactivity timeout
        if (authenticationService.isInactivityTimeout(request.session())) {
            authenticationService.clearSessionCookie(request.session());
            return callForbiddenDueToInactivityTimeout(loggedInUser.getUsername());
        }

        // Check user deactivated by admin
        if (!loggedInUser.isActive()) {
            authenticationService.clearSessionCookie(request.session());
            return callForbiddenDueToUserDeactivated(loggedInUser.getUsername());
        }

        authenticationService.refreshSessionCookie(request.session());
        authenticationService.setLastSeen(loggedInUser.getUsername());

        // Check authorization
        if (!loggedInUser.hasRole(role)) {
            return callForbiddenDueToAuthorization(loggedInUser.getUsername(), request.path());
        }

        return AuthResult.of(true);
    }

    private AuthResult callForbiddenDueToAuthentication(String remoteAddress, String urlPath) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress + " tried to access page " + urlPath);
        String msg = "You are not allowed to access this page. Please log in.";
        if (Helpers.isAjax()) {
            return AuthResult.of(forbidden(msg));
        }
        if (!urlPath.isEmpty() && !urlPath.matches("(/|/jatos|/jatos/)")) {
            FlashScopeMessaging.error(msg);
        }
        return AuthResult.of(redirect(auth.gui.routes.SignIn.login()));
    }

    private AuthResult callForbiddenDueToSessionTimeout(String normalizedUsername) {
        LOGGER.info("Session of user " + normalizedUsername + " has expired and the user has been logged out.");
        String msg = "Your session has expired. You have been logged out.";
        if (Helpers.isAjax()) {
            return AuthResult.of(forbidden(msg));
        } else {
            FlashScopeMessaging.success(msg);
            return AuthResult.of(redirect(auth.gui.routes.SignIn.login()));
        }
    }

    private AuthResult callForbiddenDueToInactivityTimeout(String normalizedUsername) {
        LOGGER.info("User " + normalizedUsername + " has been logged out due to inactivity.");
        String msg = "You have been logged out due to inactivity.";
        if (Helpers.isAjax()) {
            return AuthResult.of(forbidden(msg));
        } else {
            FlashScopeMessaging.success(msg);
            return AuthResult.of(redirect(auth.gui.routes.SignIn.login()));
        }
    }

    private AuthResult callForbiddenDueToUserDeactivated(String normalizedUsername) {
        LOGGER.info("User " + normalizedUsername + " has been logged out because an admin deactivated this user.");
        String msg = "Your user was deactivated by an admin.";
        if (Helpers.isAjax()) {
            return AuthResult.of(forbidden(msg));
        } else {
            FlashScopeMessaging.warning(msg);
            return AuthResult.of(redirect(auth.gui.routes.SignIn.login()));
        }
    }

    private AuthResult callForbiddenDueToAuthorization(String normalizedUsername, String urlPath) {
        String msg = "User " + normalizedUsername + " isn't allowed to access page " + urlPath + ".";
        LOGGER.warn(msg);
        if (Helpers.isAjax()) {
            return AuthResult.of(forbidden(msg));
        } else {
            RequestScopeMessaging.error(msg);
            return AuthResult.of(homeProvider.get().home(Http.Status.FORBIDDEN));
        }
    }

}
