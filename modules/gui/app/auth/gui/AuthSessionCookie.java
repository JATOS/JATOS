package auth.gui;

import controllers.gui.Home;
import messaging.common.RequestScopeMessaging;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.UserService;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Instant;
import java.util.function.Function;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static general.common.Http.*;
import static messaging.common.FlashScopeMessaging.*;
import static play.mvc.Results.forbidden;
import static play.mvc.Results.redirect;

// @formatter:off
/**
 * This class defines authentication via session cookies (which is the default authentication in the Play Framework).
 *
 * It checks Play's session cookie and does authorization. It has several layers of security:
 * 1) First, it checks if a username is in Play's session cookie and if this username belongs to a user in the database.
 * 2) Check if the session timed out. The time span is defined in the application.conf.
 * 3) Check if the session timed out due to inactivity of the user. With each request by the user, the time of the last
 * activity gets refreshed in the session.
 * 4) Check if the signed-in user has the proper Role needed to access this page. This Role is an optional parameter in
 * the {@link AuthAction.Auth} annotation.
 * 5) It checks if the user was deactivated by an admin.
 *
 * The {@link AuthAction.Auth} annotation does not check the user's password. This is done once during signing in (class
 * {@link Signin}).
 *
 * The {@link User} object is put in the {@link Context} for later use during request processing.
 *
 * @author Kristian Lange
 */
// @formatter:on
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
    public AuthResult authenticate(Http.Request request, User.Role role) {

        if (!Helpers.isSessionCookieRequest(request)) {
            return AuthResult.wrongMethod(request);
        }

        // For authentication, it's actually enough to check that the username is in Play's session. Play's session
        // is safe from tempering. But we retrieve the user from the database and put it into the Http.Context
        // since we need it later anyway. Storing it in the Http.Context now saves us some database requests later.
        User signedinUser = authService.getSignedinUserBySessionCookie(request.session());
        if (signedinUser == null) {
            return callForbiddenDueToAuthentication(request, request.remoteAddress(), request.path());
        }
        Context.current().args().put(SIGNEDIN_USER, signedinUser);

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
        if (!signedinUser.hasRole(role)) {
            return callForbiddenDueToAuthorization(request, signedinUser.getUsername(), request.path());
        }

        userService.setLastSeen(signedinUser);
        Http.Request finalRequest = request;
        Function<Result, Result> postHook = result -> result.addingToSession(finalRequest,
                AuthService.SESSION_LAST_ACTIVITY_TIME, String.valueOf(Instant.now().toEpochMilli()));
        return AuthResult.authenticated(request, postHook);
    }

    private AuthResult callForbiddenDueToAuthentication(Http.Request request, String remoteAddress, String urlPath) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress + " tried to access page " + urlPath);
        String msg = "You are not allowed to access this page. Please sign in.";
        if (Helpers.isAjax(request)) {
            return AuthResult.denied(request, forbidden(msg));
        }
        AuthResult authResult = AuthResult.denied(request, redirect(auth.gui.routes.Signin.signin())
                .withNewSession());
        if (!urlPath.isEmpty() && !urlPath.matches("(/|/jatos|/jatos/)")) {
            authResult.result = authResult.result.flashing(ERROR, msg);
        }
        return authResult;
    }

    private AuthResult callForbiddenDueToSessionTimeout(Http.Request request, String normalizedUsername) {
        LOGGER.info("Session of user " + normalizedUsername + " has expired and the user has been signed out.");
        String msg = "Your session has expired. You have been signed out. Please sign in again.";
        if (Helpers.isAjax(request)) {
            return AuthResult.denied(request, forbidden(msg));
        } else {
            return AuthResult.denied(request, redirect(auth.gui.routes.Signin.signin())
                    .withNewSession()
                    .flashing(SUCCESS, msg));
        }
    }

    private AuthResult callForbiddenDueToInactivityTimeout(Http.Request request, String normalizedUsername) {
        LOGGER.info("User " + normalizedUsername + " has been signed out due to inactivity.");
        String msg = "You have been signed out due to inactivity.";
        if (Helpers.isAjax(request)) {
            return AuthResult.denied(request, forbidden(msg));
        } else {
            return AuthResult.denied(request, redirect(auth.gui.routes.Signin.signin())
                    .withNewSession()
                    .flashing(SUCCESS, msg));
        }
    }

    private AuthResult callForbiddenDueToUserDeactivated(Http.Request request, String normalizedUsername) {
        LOGGER.info("User " + normalizedUsername + " has been signed out because an admin deactivated this user.");
        String msg = "Your user was deactivated by an admin.";
        if (Helpers.isAjax(request)) {
            return AuthResult.denied(request, forbidden(msg));
        } else {
            return AuthResult.denied(request, redirect(auth.gui.routes.Signin.signin())
                    .withNewSession()
                    .flashing(WARNING, msg));
        }
    }

    private AuthResult callForbiddenDueToAuthorization(Http.Request request, String normalizedUsername, String urlPath) {
        String msg = "User " + normalizedUsername + " isn't allowed to access page " + urlPath + ".";
        LOGGER.warn(msg);
        // Do not clear the session cookie - do not sign out
        if (Helpers.isAjax(request)) {
            return AuthResult.denied(request, forbidden(msg));
        } else {
            RequestScopeMessaging.error(msg);
            return AuthResult.denied(request, homeProvider.get().home(request, Http.Status.FORBIDDEN));
        }
    }

}
