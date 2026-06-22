package auth.gui;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import auth.gui.AuthAction.Auth;
import com.google.common.collect.ImmutableMap;
import daos.common.LoginAttemptDao;
import daos.common.UserDao;
import http.common.Http.Context;
import general.common.MessagesStrings;
import json.common.DefaultJson;
import models.common.LoginAttempt;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.data.FormFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.UserService;

import javax.inject.Inject;
import javax.inject.Singleton;

import static messaging.common.FlashMessagingHelper.SUCCESS;
import static models.common.User.Role.*;

/**
 * Controller that deals with authentication for users stored in JATOS' DB and users authenticated by LDAP. OIDC auth is
 * handled by the classes {@link SigninGoogle} and {@link SigninOidc}.There are two sign-in views: 1) sign-in HTML page,
 * and 2) an overlay. The second one is triggered by a session timeout or an inactivity timeout in JavaScript.
 */
@Singleton
public class Signin extends Controller {

    private static final ALogger LOGGER = Logger.of(Signin.class);

    private final AuthService authService;
    private final FormFactory formFactory;
    private final UserDao userDao;
    private final LoginAttemptDao loginAttemptDao;
    private final UserService userService;
    private final DefaultJson defaultJson;

    @Inject
    Signin(AuthService authService,
           FormFactory formFactory,
           UserDao userDao,
           LoginAttemptDao loginAttemptDao,
           UserService userService,
           DefaultJson defaultJson) {
        this.authService = authService;
        this.formFactory = formFactory;
        this.userDao = userDao;
        this.loginAttemptDao = loginAttemptDao;
        this.userService = userService;
        this.defaultJson = defaultJson;
    }

    /**
     * Shows the sign-in page
     */
    @Async
    public Result signin(Http.Request request) {
        return ok(views.html.gui.auth.signin.render(request.asScala()));
    }

    /**
     * HTTP POST Endpoint for the sign-in form. It handles both Ajax and normal requests.
     */
    @Async(Executor.IO)
    public Result authenticate(Http.Request request) {
        SigninData signinData = formFactory.form(SigninData.class).bindFromRequest(request).withDirectFieldAccess(true).get();
        String normalizedUsername = User.normalizeUsername(signinData.getUsername());
        String password = signinData.getPassword();
        String remoteAddress = request.remoteAddress();

        if (authService.isRepeatedSigninAttempt(normalizedUsername, remoteAddress)) {
            return return401DueToRepeatedSigninAttempt(normalizedUsername, remoteAddress);
        }

        User user = userDao.findByUsername(normalizedUsername);
        boolean authenticated = authService.authenticate(user, password);

        if (!authenticated) {
            loginAttemptDao.persist(new LoginAttempt(normalizedUsername, remoteAddress));
            if (authService.isRepeatedSigninAttempt(normalizedUsername, remoteAddress)) {
                return return401DueToRepeatedSigninAttempt(normalizedUsername, remoteAddress);
            } else {
                return return401DueToFailedAuth(normalizedUsername, remoteAddress);
            }
        } else {
            userService.setLastSignin(normalizedUsername);
            loginAttemptDao.removeByUsername(normalizedUsername);
            authService.writeSessionCookie(normalizedUsername, signinData.getKeepSignedin());
            return ok(defaultJson.objAsJsonNode(ImmutableMap.of(
                    "redirectUrl", authService.getRedirectPageAfterSignin(user),
                    "userSigninTime", authService.getSessionSigninTime())));
        }
    }

    private Result return401DueToRepeatedSigninAttempt(String normalizedUsername, String remoteAddress) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress
                + " failed repeatedly for username " + normalizedUsername);
        return unauthorized(MessagesStrings.FAILED_THREE_TIMES);
    }

    private Result return401DueToFailedAuth(String normalizedUsername, String remoteAddress) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress + " failed for username "
                + normalizedUsername);
        return unauthorized(MessagesStrings.INVALID_USER_OR_PASSWORD);
    }

    /**
     * Removes user from session and shows sign-in view with a sign-out message.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN})
    public Result signout() {
        LOGGER.info(".signout: " + Context.current().response().session().get(AuthService.SESSION_USERNAME));
        Context.current().response().clearSession();
        Context.current().response().putFlash(SUCCESS, "You've been signed out.");
        return redirect(auth.gui.routes.Signin.signin());
    }

    /**
     * Simple model class needed for sign-in template
     */
    public static class SigninData {
        public String username;
        public String password;
        public boolean keepSignedin = false;

        public void setUsername(String username) {
            this.username = username;
        }

        public String getUsername() {
            return username;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPassword() {
            return password;
        }

        public void setKeepSignedin(boolean keepSignedin) {
            this.keepSignedin = keepSignedin;
        }

        public boolean getKeepSignedin() {
            return keepSignedin;
        }
    }

}
