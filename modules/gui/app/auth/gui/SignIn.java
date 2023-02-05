package auth.gui;

import auth.gui.AuthAction.Auth;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.UserDao;
import general.common.MessagesStrings;
import general.gui.FlashScopeMessaging;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.UserService;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.NamingException;

/**
 * Controller that deals with authentication for users stored in JATOS DB and users authenticated by LDAP. OIDC auth is
 * handled by the classes {@link SignInGoogle} and {@link SignInOidc}.There are two login views: 1) login HTML page,
 * and 2) an overlay. The second one is triggered by a session timeout or an inactivity timeout in JavaScript.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class SignIn extends Controller {

    private static final ALogger LOGGER = Logger.of(SignIn.class);

    private final AuthService authenticationService;
    private final UserSessionCacheAccessor userSessionCacheAccessor;
    private final FormFactory formFactory;
    private final UserDao userDao;
    private final UserService userService;

    @Inject
    SignIn(AuthService authenticationService, UserSessionCacheAccessor userSessionCacheAccessor,
            FormFactory formFactory, UserService userService, UserDao userDao) {
        this.authenticationService = authenticationService;
        this.userSessionCacheAccessor = userSessionCacheAccessor;
        this.formFactory = formFactory;
        this.userDao = userDao;
        this.userService = userService;

        userService.createAdminIfNotExists();
    }

    /**
     * Shows the login page
     */
    public Result login() {
        return ok(views.html.gui.auth.login.render(formFactory.form(SignIn.Login.class)));
    }

    /**
     * HTTP POST Endpoint for the login form. It handles both Ajax and normal requests.
     */
    @Transactional
    public Result authenticate(Http.Request request) {
        Form<Login> loginForm = formFactory.form(Login.class).bindFromRequest(request);
        String normalizedUsername = User.normalizeUsername(loginForm.rawData().get("username"));
        String password = loginForm.rawData().get("password");

        if (authenticationService.isRepeatedLoginAttempt(normalizedUsername)) {
            return returnUnauthorizedDueToRepeatedLoginAttempt(loginForm, normalizedUsername, request.remoteAddress());
        }

        boolean authenticated;
        try {
            User user = userDao.findByUsername(normalizedUsername);
            authenticated = authenticationService.authenticate(user, password);
        } catch (NamingException e) {
            return returnInternalServerErrorDueToLdapProblems(loginForm, e);
        }

        if (!authenticated) {
            return returnUnauthorizedDueToFailedAuth(loginForm, normalizedUsername, request.remoteAddress());
        } else {
            authenticationService.writeSessionCookie(session(), normalizedUsername);
            userService.setLastLogin(normalizedUsername);
            userSessionCacheAccessor.add(normalizedUsername);
            if (Helpers.isAjax()) {
                return ok(" "); // jQuery.ajax cannot handle empty responses
            } else {
                return redirect(controllers.gui.routes.Home.home());
            }
        }
    }

    private Result returnUnauthorizedDueToRepeatedLoginAttempt(Form<Login> loginForm, String normalizedUsername,
            String remoteAddress) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress
                + " failed repeatedly for username " + normalizedUsername);
        if (Helpers.isAjax()) {
            return unauthorized(MessagesStrings.FAILED_THREE_TIMES);
        } else {
            return unauthorized(
                    views.html.gui.auth.login.render(loginForm.withGlobalError(MessagesStrings.FAILED_THREE_TIMES)));
        }
    }

    private Result returnUnauthorizedDueToFailedAuth(Form<Login> loginForm, String normalizedUsername,
            String remoteAddress) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress + " failed for username "
                + normalizedUsername);
        if (Helpers.isAjax()) {
            return unauthorized(MessagesStrings.INVALID_USER_OR_PASSWORD);
        } else {
            return unauthorized(views.html.gui.auth.login
                    .render(loginForm.withGlobalError(MessagesStrings.INVALID_USER_OR_PASSWORD)));
        }
    }

    private Result returnInternalServerErrorDueToLdapProblems(Form<Login> loginForm, NamingException e) {
        LOGGER.warn("LDAP problems - " + e.toString());
        if (Helpers.isAjax()) {
            return internalServerError(MessagesStrings.LDAP_PROBLEMS);
        } else {
            return internalServerError(views.html.gui.auth.login
                    .render(loginForm.withGlobalError(MessagesStrings.LDAP_PROBLEMS)));
        }
    }

    /**
     * Removes user from session and shows login view with an logout message.
     */
    @Transactional
    @Auth
    public Result logout(Http.Request request) {
        LOGGER.info(".logout: " + request.session().get(AuthService.SESSION_USERNAME));
        authenticationService.clearSessionCookie(request.session());
        FlashScopeMessaging.success("You've been logged out.");
        return redirect(auth.gui.routes.SignIn.login());
    }

    /**
     * Simple model class needed for login template
     */
    public static class Login {

        public static final String USERNAME = "username";
        public static final String PASSWORD = "password";

        public String username;
        public String password;
    }

}
