package auth.gui;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import daos.common.UserDao;
import exceptions.gui.AuthException;
import general.common.Common;
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
import services.gui.UserService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Class that handles the sign-in of users via Google OIDC sign-in button.
 * The actual authentication is done with Google's gsi/client JavaScript library in the browser. Here we just check the
 * Token ID, create the user if it doesn't exist yet und sign in the user into JATOS. Google OIDC is just used for
 * authentication - authorization and session management is still done by JATOS and Play Framework.
 *
 * More info: https://developers.google.com/identity/gsi/web
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class SigninGoogle extends Controller {

    private static final ALogger LOGGER = Logger.of(SigninGoogle.class);

    private final AuthService authService;
    private final SigninFormValidation signinFormValidation;
    private final FormFactory formFactory;
    private final UserDao userDao;
    private final UserService userService;

    @Inject
    SigninGoogle(AuthService authService, SigninFormValidation signinFormValidation,
            FormFactory formFactory, UserService userService, UserDao userDao) {
        this.authService = authService;
        this.signinFormValidation = signinFormValidation;
        this.formFactory = formFactory;
        this.userDao = userDao;
        this.userService = userService;
    }

    /**
     * HTTP POST Endpoint for the sign-in form
     */
    @Transactional
    public Result signin(Http.Request request) throws GeneralSecurityException, IOException {
        String idTokenString = request.body().asFormUrlEncoded().get("credential")[0];
        GoogleIdToken idToken = fetchOAuthGoogleIdToken(idTokenString);
        if (idToken == null) {
            LOGGER.warn("Google sign in: Invalid ID token.");
            FlashScopeMessaging.error("Google sign in: Invalid ID token");
            return redirect(auth.gui.routes.Signin.signin());
        }

        GoogleIdToken.Payload idTokenPayload = idToken.getPayload();

        if (!idTokenPayload.getEmailVerified()) {
            LOGGER.info("Google sign in: Couldn't sign in user due to email not verified");
            FlashScopeMessaging.error("Google sign in: Email not verified");
            return redirect(auth.gui.routes.Signin.signin());
        }

        try {
            persistUserIfNotExisting(idTokenPayload);
        } catch (AuthException e) {
            LOGGER.warn(e.getMessage());
            FlashScopeMessaging.error(e.getMessage());
            return redirect(auth.gui.routes.Signin.signin());
        }

        String normalizedUsername = User.normalizeUsername(idTokenPayload.getEmail());
        authService.writeSessionCookie(session(), normalizedUsername);
        userService.setLastSignin(normalizedUsername);

        return redirect(controllers.gui.routes.Home.home());
    }

    /**
     * Verifies and fetches an ID token from Google OAuth by sending an HTTP POST to Google. The actual authentication
     * happens in the frontend with Google's gapi library.
     */
    private GoogleIdToken fetchOAuthGoogleIdToken(String idTokenString) throws GeneralSecurityException, IOException {
        HttpTransport transport = new NetHttpTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                .setAudience(Collections.singletonList(Common.getOauthGoogleClientId())).build();
        return verifier.verify(idTokenString);
    }

    private void persistUserIfNotExisting(GoogleIdToken.Payload idTokenPayload) throws AuthException {
        String normalizedUsername = User.normalizeUsername(idTokenPayload.getEmail());

        User existingUser = userDao.findByUsername(normalizedUsername);
        if (existingUser == null) {
            String name = (String) idTokenPayload.get("name");
            NewUserModel newUserModel = new NewUserModel();
            newUserModel.setUsername(normalizedUsername);
            newUserModel.setName(name);
            newUserModel.setEmail(idTokenPayload.getEmail());
            newUserModel.setAuthMethod(User.AuthMethod.OAUTH_GOOGLE);
            Form<NewUserModel> newUserForm = formFactory.form(NewUserModel.class).fill(newUserModel);
            newUserForm = signinFormValidation.validateNewUser(newUserForm);
            if (newUserForm.hasErrors()) {
                throw new AuthException("Google sign in: user validation failed - " + newUserForm.errors().get(0).message());
            }

            userService.bindToUserAndPersist(newUserModel);
        } else if (!existingUser.isOauthGoogle()) {
            throw new AuthException("User exists already - but does not use Google sign in");
        }
    }

}
