package auth.gui;

import controllers.gui.Home;
import general.common.RequestScope;
import general.gui.FlashScopeMessaging;
import general.gui.RequestScopeMessaging;
import models.common.User;
import models.common.User.Role;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.UserService;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Instant;
import java.util.EnumSet;
import java.util.function.Function;

import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static play.mvc.Results.*;

/**
 * This class defines authentication via session cookies (which is the default authentication in the Play Framework).
 * <p>
 * It checks Play's session cookie and does authorization. It has several layers of security:
 * <p>
 * 1) First, it checks if a username is in Play's session cookie and if this username belongs to a user in the
 * database.
 * <p>
 * 2) Check if the session timed out. The time span is defined in the application.conf.
 * <p>
 * 3) Check if the session timed out due to inactivity of the user. With each request by the user, the time of last
 * activity gets refreshed in the session.
 * <p>
 * 4) Check if the signed-in user has the proper Role needed to access this page. This Role is an optional parameter in
 * the {@link AuthAction.Auth} annotation.
 * <p>
 * 5) It checks if the user was deactivated by an admin.
 * <p>
 * The {@link AuthAction.Auth} annotation does not check the user's password. This is done once during signing in (class
 * {@link Signin}).
 * <p>
 * The {@link User} object is put in the {@link RequestScope} for later use during request processing.
 *
 * @author Kristian Lange
 */
public class AuthSessionCookie implements AuthAction.AuthMethod {

    private static final ALogger LOGGER = Logger.of(AuthSessionCookie.class);

    private final Provider<Home> homeProvider;
    private final AuthService authService;
    private final UserService userService;

    @Inject
    AuthSessionCookie(Provider<Home> homeProvider, AuthService authService, UserService userService) {
        this.homeProvider = homeProvider;
        this.authService = authService;
        this.userService = userService;
    }

    @Override
    public Type type() {
        return SESSION;
    }

    @Override
    public AuthResult authenticate(Http.Request request, EnumSet<Role> allowedRoles) {

        if (!Helpers.isSessionCookieRequest(request)) {
            return AuthResult.wrongMethod();
        }

        // For authentication, it's actually enough to check that the username is in Play's session. Play's session
        // is safe from tempering. But we retrieve the user from the database and put it into our RequestScope
        // since we need it later anyway. Storing it in the RequestScope now saves us some database requests later.
        User signedinUser = authService.getSignedinUserBySessionCookie(request.session());
        if (signedinUser == null) {
            return callUnauthorizedDueToAuthentication(request);
        }
        RequestScope.put(AuthService.SIGNEDIN_USER, signedinUser);

        // Check session timeout and inactivity timeout (only if keepSignedin flag is not set)
        if (!authService.isSessionKeepSignedin(request.session())) {
            if (authService.isSessionTimeout(request.session())) {
                return callForbiddenDueToSessionTimeout(request, signedinUser.getUsername());
            }
            if (authService.isInactivityTimeout(request.session())) {
                return callForbiddenDueToInactivityTimeout(request, signedinUser.getUsername());
            }
        }

        // Check user deactivated by admin
        if (!signedinUser.isActive()) {
            return callForbiddenDueToUserDeactivated(request, signedinUser.getUsername());
        }

        // Check authorization
        if (!signedinUser.hasRole(allowedRoles)) {
            return callUnauthorizedDueToAuthorization(request, signedinUser.getUsername(), request.path());
        }

        userService.setLastSeen(signedinUser);
        Function<Result, Result> postHook = result -> result.addingToSession(request,
                AuthService.SESSION_LAST_ACTIVITY_TIME, String.valueOf(Instant.now().toEpochMilli()));
        return AuthResult.authenticated(postHook);
    }

    private AuthResult callUnauthorizedDueToAuthentication(Http.Request request) {
        String remoteAddress = request.remoteAddress();
        String urlPath = request.path();
        LOGGER.warn("Authentication failed: remote address " + remoteAddress + " tried to access page " + urlPath);
        String msg = "You are not allowed to access this page. Please sign in.";
        if (!urlPath.isEmpty() && !urlPath.matches("(/|/jatos|/jatos/)")) {
            FlashScopeMessaging.error(msg);
        }
        if (Helpers.isHtmlRequest(request)) {
            return AuthResult.denied(redirect(auth.gui.routes.Signin.signin()).withNewSession());
        }
        return AuthResult.denied(unauthorized(msg));
    }

    private AuthResult callForbiddenDueToSessionTimeout(Http.Request request, String normalizedUsername) {
        LOGGER.info("Session of user " + normalizedUsername + " has expired and the user has been signed out.");
        String msg = "Your session has expired. You have been signed out. Please sign in again.";
        if (Helpers.isHtmlRequest(request)) {
            FlashScopeMessaging.success(msg);
            return AuthResult.denied(redirect(auth.gui.routes.Signin.signin()).withNewSession());
        }
        return AuthResult.denied(forbidden(msg));
    }

    private AuthResult callForbiddenDueToInactivityTimeout(Http.Request request, String normalizedUsername) {
        LOGGER.info("User " + normalizedUsername + " has been signed out due to inactivity.");
        String msg = "You have been signed out due to inactivity.";
        if (Helpers.isHtmlRequest(request)) {
            FlashScopeMessaging.success(msg);
            return AuthResult.denied(redirect(auth.gui.routes.Signin.signin()).withNewSession());
        }
        return AuthResult.denied(forbidden(msg));
    }

    private AuthResult callForbiddenDueToUserDeactivated(Http.Request request, String normalizedUsername) {
        LOGGER.info("User " + normalizedUsername + " has been signed out because an admin deactivated this user.");
        String msg = "Your user was deactivated by an admin.";
        if (Helpers.isHtmlRequest(request)) {
            FlashScopeMessaging.warning(msg);
            return AuthResult.denied(redirect(auth.gui.routes.Signin.signin()).withNewSession());
        }
        return AuthResult.denied(forbidden(msg));
    }

    private AuthResult callUnauthorizedDueToAuthorization(Http.Request request, String normalizedUsername, String urlPath) {
        String msg = "User " + normalizedUsername + " isn't allowed to access page " + urlPath + ".";
        LOGGER.warn(msg);
        // Do not clear the session cookie - do not sign out
        if (Helpers.isHtmlRequest(request)) {
            RequestScopeMessaging.error(msg);
            return AuthResult.denied(homeProvider.get().home(request, Http.Status.UNAUTHORIZED));
        }
        return AuthResult.denied(unauthorized(msg));
    }

}
