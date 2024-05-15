package auth.gui;

import auth.gui.AuthAction.Auth;
import com.google.common.collect.ImmutableMap;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.LoginAttemptDao;
import daos.common.UserDao;
import general.common.MessagesStrings;
import general.gui.FlashScopeMessaging;
import models.common.LoginAttempt;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.UserService;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.NamingException;

/**
 * Controller that deals with authentication for users stored in JATOS' DB and users authenticated by LDAP. OIDC auth is
 * handled by the classes {@link SigninGoogle} and {@link SigninOidc}.There are two sign-in views: 1) sign-in HTML page,
 * and 2) an overlay. The second one is triggered by a session timeout or an inactivity timeout in JavaScript.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Signin extends Controller {

    private static final ALogger LOGGER = Logger.of(Signin.class);

    private final AuthService authService;
    private final FormFactory formFactory;
    private final UserDao userDao;
    private final LoginAttemptDao loginAttemptDao;
    private final UserService userService;

    @Inject
    Signin(AuthService authService, FormFactory formFactory, UserDao userDao, LoginAttemptDao loginAttemptDao,
            UserService userService) {
        this.authService = authService;
        this.formFactory = formFactory;
        this.userDao = userDao;
        this.loginAttemptDao = loginAttemptDao;
        this.userService = userService;
    }

    /**
     * Shows the sign-in page
     */
    public Result signin(Http.Request request) {
        return ok(views.html.gui.auth.signin_new.render(request));
    }

    /**
     * HTTP POST Endpoint for the sign-in form. It handles both Ajax and normal requests.
     */
    @Transactional
    public Result authenticate(Http.Request request) {
        SigninData signinData = formFactory.form(SigninData.class).bindFromRequest(request).withDirectFieldAccess(true).get();
        String normalizedUsername = User.normalizeUsername(signinData.getUsername());
        String password = signinData.getPassword();

        if (authService.isRepeatedSigninAttempt(normalizedUsername)) {
            return returnUnauthorizedDueToRepeatedSigninAttempt(normalizedUsername, request.remoteAddress());
        }

        User user = userDao.findByUsername(normalizedUsername);
        boolean authenticated;
        try {
            authenticated = authService.authenticate(user, password);
        } catch (NamingException e) {
            return returnInternalServerErrorDueToLdapProblems(e);
        }

        if (!authenticated) {
            loginAttemptDao.create(new LoginAttempt(normalizedUsername));
            if (authService.isRepeatedSigninAttempt(normalizedUsername)) {
                return returnUnauthorizedDueToRepeatedSigninAttempt(normalizedUsername, request.remoteAddress());
            } else {
                return returnUnauthorizedDueToFailedAuth(normalizedUsername, request.remoteAddress());
            }
        } else {
            authService.writeSessionCookie(session(), normalizedUsername, signinData.getKeepSignedin());
            userService.setLastSignin(normalizedUsername);
            loginAttemptDao.removeByUsername(normalizedUsername);
            return ok(JsonUtils.asJsonNode(ImmutableMap.of(
                    "redirectUrl", authService.getRedirectPageAfterSignin(user),
                    "userSigninTime", Long.valueOf(session().get(AuthService.SESSION_SIGNIN_TIME)))));
        }
    }

    private Result returnUnauthorizedDueToRepeatedSigninAttempt(String normalizedUsername, String remoteAddress) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress
                + " failed repeatedly for username " + normalizedUsername);
        return unauthorized(MessagesStrings.FAILED_THREE_TIMES);
    }

    private Result returnUnauthorizedDueToFailedAuth(String normalizedUsername, String remoteAddress) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress + " failed for username "
                + normalizedUsername);
        return unauthorized(MessagesStrings.INVALID_USER_OR_PASSWORD);
    }

    private Result returnInternalServerErrorDueToLdapProblems(NamingException e) {
        LOGGER.warn("LDAP problems - " + e.toString());
        return internalServerError(MessagesStrings.LDAP_PROBLEMS);
    }

    /**
     * Removes user from session and shows sign-in view with a sign-out message.
     */
    @Transactional
    @Auth
    public Result signout(Http.Request request) {
        LOGGER.info(".signout: " + request.session().get(AuthService.SESSION_USERNAME));
        FlashScopeMessaging.success("You've been signed out.");
        return redirect(auth.gui.routes.Signin.signin()).withNewSession();
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
