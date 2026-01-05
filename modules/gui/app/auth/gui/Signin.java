package auth.gui;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import auth.gui.AuthAction.Auth;
import com.google.common.collect.ImmutableMap;
import daos.common.LoginAttemptDao;
import daos.common.UserDao;
import general.common.MessagesStrings;
import models.common.LoginAttempt;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.data.FormFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.UserService;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

import static messaging.common.FlashScopeMessaging.*;

/**
 * Controller that deals with authentication for users stored in JATOS' DB and users authenticated by LDAP. OIDC auth is
 * handled by the classes {@link SigninGoogle} and {@link SigninOidc}.There are two sign-in views: 1) sign-in HTML page,
 * and 2) an overlay. The second one is triggered by a session timeout or an inactivity timeout in JavaScript.
 *
 * @author Kristian Lange
 */
//@GuiAccessLogging
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
            return returnUnauthorizedDueToRepeatedSigninAttempt(normalizedUsername, remoteAddress);
        }

        User user = userDao.findByUsername(normalizedUsername);
        boolean authenticated = authService.authenticate(user, password);

        if (!authenticated) {
            loginAttemptDao.persist(new LoginAttempt(normalizedUsername, remoteAddress));
            if (authService.isRepeatedSigninAttempt(normalizedUsername, remoteAddress)) {
                return returnUnauthorizedDueToRepeatedSigninAttempt(normalizedUsername, remoteAddress);
            } else {
                return returnUnauthorizedDueToFailedAuth(normalizedUsername, remoteAddress);
            }
        } else {
            userService.setLastSignin(normalizedUsername);
            loginAttemptDao.removeByUsername(normalizedUsername);
            return ok(JsonUtils.asJsonNode(ImmutableMap.of(
                    "redirectUrl", authService.getRedirectPageAfterSignin(user),
                    "userSigninTime", authService.getSessionSigninTime(request))))
                    .addingToSession(request, authService.writeSessionCookie(normalizedUsername, signinData.getKeepSignedin()));
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

    /**
     * Removes user from session and shows sign-in view with a sign-out message.
     */
    @Async(Executor.IO)
    @Auth
    public Result signout(Http.Request request) {
        LOGGER.info(".signout: " + request.session().get(AuthService.SESSION_USERNAME));
        return redirect(auth.gui.routes.Signin.signin())
                .withNewSession()
                .flashing(SUCCESS, "You've been signed out.");
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
