package auth.gui;

import controllers.gui.Home;
import http.common.HttpUtils;
import messaging.common.RequestScopeMessaging;
import models.common.User;
import models.common.User.Role;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Http;
import services.gui.UserService;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Instant;
import java.util.EnumSet;

import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static http.common.Http.Context;
import static messaging.common.FlashMessagingHelper.*;
import static play.mvc.Results.*;

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
 * invalid/missing/expired session → 401 (unauthorized)
 * authenticated but insufficient role → 403 (forbidden)
 *
 * The {@link AuthAction.Auth} annotation does not check the user's password. This is done once during signing in (class
 * {@link Signin}).
 *
 * The {@link User} object is put in the {@link Context} for later use during request processing.
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
    public Type type() {
        return SESSION;
    }

    @Override
    public AuthResult authenticate(EnumSet<Role> allowedRoles) {

        if (!HttpUtils.isSessionCookieRequest()) {
            return AuthResult.wrongMethod();
        }

        // For authentication, it's actually enough to check that the username is in Play's session. Play's session
        // is safe from tempering. But we retrieve the user from the database and put it into the Http.Context
        // since we need it later anyway. Storing it in the Http.Context now saves us some database requests later.
        User signedinUser = authService.getSignedinUserBySessionCookie();
        if (signedinUser == null) {
            return call401DueToAuthentication();
        }

        // Check session timeout and inactivity timeout (only if keepSignedin flag is not set)
        if (!authService.isSessionKeepSignedin()) {
            if (authService.isSessionTimeout()) {
                return call401DueToSessionTimeout(signedinUser.getUsername());
            }
            if (authService.isInactivityTimeout()) {
                return call401DueToInactivityTimeout(signedinUser.getUsername());
            }
        }

        // Check user deactivated by admin
        if (!signedinUser.isActive()) {
            return call403DueToUserDeactivated(signedinUser.getUsername());
        }

        // Check authorization
        if (!signedinUser.hasRole(allowedRoles)) {
            return call403DueToAuthorization(signedinUser.getUsername());
        }

        userService.setLastSeen(signedinUser);
        Context.current().response().putSession(
                AuthService.SESSION_LAST_ACTIVITY_TIME, String.valueOf(Instant.now().toEpochMilli()));
        Context.current().args().put(SIGNEDIN_USER, signedinUser);
        return AuthResult.authenticated();
    }

    private AuthResult call401DueToAuthentication() {
        String remoteAddress = Context.current().requestHeader().remoteAddress();
        String urlPath = Context.current().requestHeader().path();
        LOGGER.warn("Authentication failed: remote address " + remoteAddress + " tried to access page " + urlPath);
        String msg = "You are not allowed to access this page. Please sign in.";

        Context.current().response().clearSession();

        if (HttpUtils.isHtmlRequest()) {
            if (HttpUtils.isNotSigninPage()) {
                Context.current().response().putFlash(ERROR, msg);
            }
            return AuthResult.denied(redirect(auth.gui.routes.Signin.signin()));
        } else {
            return AuthResult.denied(unauthorized(msg));
        }
    }



    private AuthResult call401DueToSessionTimeout(String username) {
        LOGGER.info("Session of user " + username + " has expired and the user has been signed out.");
        String msg = "Your session has expired. You have been signed out. Please sign in again.";

        Context.current().response().clearSession();

        if (HttpUtils.isHtmlRequest()) {
            Context.current().response().putFlash(SUCCESS, msg);
            return AuthResult.denied(redirect(auth.gui.routes.Signin.signin()));
        } else {
            return AuthResult.denied(unauthorized(msg));
        }
    }

    private AuthResult call401DueToInactivityTimeout(String username) {
        LOGGER.info("User " + username + " has been signed out due to inactivity.");
        String msg = "You have been signed out due to inactivity.";

        Context.current().response().clearSession();

        if (HttpUtils.isHtmlRequest()) {
            Context.current().response().putFlash(SUCCESS, msg);
            return AuthResult.denied(redirect(auth.gui.routes.Signin.signin()));
        } else {
            return AuthResult.denied(unauthorized(msg));
        }
    }

    private AuthResult call403DueToUserDeactivated(String username) {
        LOGGER.info("User " + username + " has been signed out because an admin deactivated this user.");
        String msg = "Your user was deactivated by an admin.";

        Context.current().response().clearSession();

        if (HttpUtils.isHtmlRequest()) {
            Context.current().response().putFlash(WARNING, msg);
            return AuthResult.denied(redirect(auth.gui.routes.Signin.signin()));
        } else {
            return AuthResult.denied(forbidden(msg));
        }
    }

    private AuthResult call403DueToAuthorization(String username) {
        String urlPath = Context.current().requestHeader().path();
        LOGGER.warn("User " + username + " isn't allowed to access page " + urlPath + ".");
        String msg = "Your user isn't allowed to access page " + urlPath + ".";

        // Do not clear the session cookie - do not sign out

        if (HttpUtils.isHtmlRequest()) {
            RequestScopeMessaging.error(msg);
            return AuthResult.denied(homeProvider.get().home(Http.Status.FORBIDDEN));
        } else {
            return AuthResult.denied(forbidden(msg));
        }
    }

}
