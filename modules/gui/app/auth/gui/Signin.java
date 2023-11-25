package auth.gui;

import auth.gui.AuthAction.Auth;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.LoginAttemptDao;
import daos.common.UserDao;
import general.common.MessagesStrings;
import general.gui.FlashScopeMessaging;
import models.common.LoginAttempt;
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
    public Result signin() {
        return ok(views.html.gui.auth.signin.render(formFactory.form(Signin.SigninData.class)));
    }

    /**
     * HTTP POST Endpoint for the sign-in form. It handles both Ajax and normal requests.
     */
    @Transactional
    public Result authenticate(Http.Request request) {
        Form<SigninData> signinForm = formFactory.form(SigninData.class).bindFromRequest(request);
        String normalizedUsername = User.normalizeUsername(signinForm.rawData().get("username"));
        String password = signinForm.rawData().get("password");

        if (authService.isRepeatedSigninAttempt(normalizedUsername)) {
            return returnUnauthorizedDueToRepeatedSigninAttempt(signinForm, normalizedUsername, request.remoteAddress());
        }

        boolean authenticated;
        try {
            User user = userDao.findByUsername(normalizedUsername);
            authenticated = authService.authenticate(user, password);
        } catch (NamingException e) {
            return returnInternalServerErrorDueToLdapProblems(signinForm, e);
        }

        if (!authenticated) {
            loginAttemptDao.create(new LoginAttempt(normalizedUsername));
            if (authService.isRepeatedSigninAttempt(normalizedUsername)) {
                return returnUnauthorizedDueToRepeatedSigninAttempt(signinForm, normalizedUsername, request.remoteAddress());
            } else {
                return returnUnauthorizedDueToFailedAuth(signinForm, normalizedUsername, request.remoteAddress());
            }
        } else {
            authService.writeSessionCookie(session(), normalizedUsername);
            userService.setLastSignin(normalizedUsername);
            loginAttemptDao.removeByUsername(normalizedUsername);
            if (Helpers.isAjax()) {
                return ok(" "); // jQuery.ajax cannot handle empty responses
            } else {
                return redirect(controllers.gui.routes.Home.home());
            }
        }
    }

    private Result returnUnauthorizedDueToRepeatedSigninAttempt(Form<SigninData> signinForm, String normalizedUsername,
            String remoteAddress) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress
                + " failed repeatedly for username " + normalizedUsername);
        if (Helpers.isAjax()) {
            return unauthorized(MessagesStrings.FAILED_THREE_TIMES);
        } else {
            return unauthorized(
                    views.html.gui.auth.signin.render(signinForm.withGlobalError(MessagesStrings.FAILED_THREE_TIMES)));
        }
    }

    private Result returnUnauthorizedDueToFailedAuth(Form<SigninData> signinForm, String normalizedUsername,
            String remoteAddress) {
        LOGGER.warn("Authentication failed: remote address " + remoteAddress + " failed for username "
                + normalizedUsername);
        if (Helpers.isAjax()) {
            return unauthorized(MessagesStrings.INVALID_USER_OR_PASSWORD);
        } else {
            return unauthorized(views.html.gui.auth.signin
                    .render(signinForm.withGlobalError(MessagesStrings.INVALID_USER_OR_PASSWORD)));
        }
    }

    private Result returnInternalServerErrorDueToLdapProblems(Form<SigninData> signinForm, NamingException e) {
        LOGGER.warn("LDAP problems - " + e.toString());
        if (Helpers.isAjax()) {
            return internalServerError(MessagesStrings.LDAP_PROBLEMS);
        } else {
            return internalServerError(views.html.gui.auth.signin
                    .render(signinForm.withGlobalError(MessagesStrings.LDAP_PROBLEMS)));
        }
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
    }

}
