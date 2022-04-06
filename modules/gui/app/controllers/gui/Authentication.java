package controllers.gui;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.UserDao;
import general.common.MessagesStrings;
import general.gui.FlashScopeMessaging;
import models.common.User;
import models.gui.NewUserModel;
import play.Logger;
import play.Logger.ALogger;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.AuthenticationService;
import services.gui.AuthenticationValidation;
import services.gui.UserService;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.NamingException;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Controller that deals with login/logout. There are two login views: 1) login HTML page, and 2) an overlay. The second
 * one is triggered by a session timeout or an inactivity timeout in JavaScript.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Authentication extends Controller {

    private static final ALogger LOGGER = Logger.of(Authentication.class);

    private final AuthenticationService authenticationService;
    private final AuthenticationValidation authenticationValidation;
    private final FormFactory formFactory;
    private final UserDao userDao;
    private final UserService userService;

    @Inject
    Authentication(AuthenticationService authenticationService, AuthenticationValidation authenticationValidation,
            FormFactory formFactory, UserService userService, UserDao userDao) {
        this.authenticationService = authenticationService;
        this.authenticationValidation = authenticationValidation;
        this.formFactory = formFactory;
        this.userDao = userDao;
        this.userService = userService;

        userService.createAdminIfNotExists();
    }

    /**
     * Shows the login page
     */
    public Result login() {
        return ok(views.html.gui.auth.login.render(formFactory.form(Authentication.Login.class)));
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
            authenticationService.writeSessionCookieAndSessionCache(session(), normalizedUsername,
                    request.remoteAddress());
            userService.setLastLogin(normalizedUsername);
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
     * HTTP POST Endpoint for the login form. It handles the sign-in / log-in of users via Google OAuth sign-in button.
     * The actual authentication is done with Google's gapi JavaScript library in the browser. Here we just check the
     * Token ID, create the user if it doesn't exist yet und log in the user into JATOS.
     * More info: https://developers.google.com/identity/sign-in/web/sign-in
     */
    @Transactional
    public Result signInWithGoogle(Http.Request request) throws GeneralSecurityException, IOException {
        String idTokenString = request.body().asText();

        GoogleIdToken idToken = authenticationService.fetchOAuthGoogleIdToken(idTokenString);
        if (idToken == null) {
            LOGGER.warn("Google OAuth: Invalid ID token.");
            return unauthorized("Invalid ID token.");
        }

        GoogleIdToken.Payload idTokenPayload = idToken.getPayload();

        if (!idTokenPayload.getEmailVerified()) {
            LOGGER.info("Google OAuth: Couldn't sign in user due to email not verified");
            return unauthorized("Email not verified");
        }

        // Create new user if they doesn't exist in the DB
        String normalizedUsername = User.normalizeUsername(idTokenPayload.getEmail());
        User existingUser = userDao.findByUsername(normalizedUsername);
        if (existingUser == null) {
            String name = (String) idTokenPayload.get("name");
            NewUserModel newUserModel = new NewUserModel();
            newUserModel.setUsername(normalizedUsername);
            newUserModel.setName(name);
            newUserModel.setEmail(idTokenPayload.getEmail());
            newUserModel.setAuthByOAuthGoogle(true);
            Form<NewUserModel> newUserForm = formFactory.form(NewUserModel.class).fill(newUserModel);

            newUserForm = authenticationValidation.validateNewUser(newUserForm);
            if (newUserForm.hasErrors()) {
                LOGGER.warn("Google OAuth: user validation failed - " + newUserForm.errors().get(0).message());
                return unauthorized(newUserForm.errors().get(0).message());
            }

            userService.bindToUserAndPersist(newUserModel);
        } else if (!existingUser.isOauthGoogle()) {
            return unauthorized("User exists already - but does not use Google sign in");
        }

        authenticationService.writeSessionCookieAndSessionCache(session(), normalizedUsername, request.remoteAddress());
        userService.setLastLogin(normalizedUsername);

        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Removes user from session and shows login view with an logout message.
     */
    @Transactional
    @Authenticated
    public Result logout(Http.Request request) {
        LOGGER.info(".logout: " + request.session().get(AuthenticationService.SESSION_USERNAME));
        User loggedInUser = authenticationService.getLoggedInUser();
        authenticationService.clearSessionCookieAndSessionCache(session(), loggedInUser.getUsername(),
                request.remoteAddress());
        FlashScopeMessaging.success("You've been logged out.");
        return redirect(controllers.gui.routes.Authentication.login());
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
