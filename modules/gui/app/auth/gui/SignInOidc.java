package auth.gui;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import controllers.gui.routes;
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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

/**
 * OpenID Connect (OIDC) authentication using Authorization Code Flow with Proof Key for Code Exchange (PKCE). OIDC is
 * just used for authentication - authorization and session management are still done with the session cookies from the
 * Play Framework.
 *
 * Using library: Nimbus OAuth 2.0 SDK with OpenID Connect extensions
 * https://connect2id.com/products/nimbus-oauth-openid-connect-sdk/guides/java-cookbook-for-openid-connect-public-clients
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class SignInOidc extends Controller {

    private static final ALogger LOGGER = Logger.of(SignInOidc.class);

    private final AuthService authenticationService;
    private final UserSessionCacheAccessor userSessionCacheAccessor;
    private final SignInFormValidation authenticationValidation;
    private final FormFactory formFactory;
    private final UserDao userDao;
    private final UserService userService;

    private OIDCProviderMetadata oidcProviderMetadata;

    @Inject
    SignInOidc(AuthService authenticationService, UserSessionCacheAccessor userSessionCacheAccessor,
            SignInFormValidation authenticationValidation,
            FormFactory formFactory, UserService userService, UserDao userDao) {
        this.authenticationService = authenticationService;
        this.userSessionCacheAccessor = userSessionCacheAccessor;
        this.authenticationValidation = authenticationValidation;
        this.formFactory = formFactory;
        this.userDao = userDao;
        this.userService = userService;
    }

    // todo make Google and OIDC users only addable by user manager (and not by the user via login page)
    // todo make LDAP users addable by login page (and not only by user manager)
    // todo allow user self register
    // todo wording login to sign in and register
    // todo check with Google
    // todo test user manager, user page, login, popup-login, logout
    // todo Users.changePasswordByAdmin todo
    // todo Users.remove todo
    // todo application.conf jatos.userSession.validation still necessary
    // todo API via OIDC
    @GuiAccessLogging
    @Transactional
    public Result signIn(Http.Request request) throws URISyntaxException, IOException, ParseException, AuthException {
        ClientID clientID = new ClientID(Common.getOidcClientId());
        URI callback = new URI(auth.gui.routes.SignInOidc.callback().absoluteURL(request));
        State state = new State();
        Nonce nonce = new Nonce();
        AuthenticationRequest authRequest = new AuthenticationRequest.Builder(
                new ResponseType("code"),
                new Scope("openid"),
                clientID,
                callback
        ).endpointURI(getProviderInfo().getAuthorizationEndpointURI())
                .state(state)
                .nonce(nonce)
                .build();
        return ok(authRequest.toURI().toString())
                .addingToSession(request, "oidcState", state.getValue())
                .addingToSession(request, "oidcNonce", nonce.getValue());
    }

    /**
     * Callback handed to the OIDC provider to be called after authentication
     */
    @Transactional
    public Result callback(Http.Request request) {
        try {
            AuthorizationCode authorizationCode = getAuthorisationCode(request);

            OIDCTokens oidcTokens = requestToken(request, authorizationCode);

            verifyIdToken(request, oidcTokens.getIDToken(), getProviderInfo());

            BearerAccessToken bearerAccessToken = (BearerAccessToken) oidcTokens.getAccessToken();
            UserInfo userInfo = getUserInfo(bearerAccessToken);

            persistUserIfNotExisting(userInfo);

            String username = userInfo.getSubject().getValue();
            authenticationService.writeSessionCookie(session(), username);
            userService.setLastLogin(username);
            userSessionCacheAccessor.add(username);
        } catch (AuthException e) {
            LOGGER.warn(e.getMessage());
            FlashScopeMessaging.error(e.getMessage());
            return redirect(auth.gui.routes.SignIn.login());
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            FlashScopeMessaging.error("OIDC error - contact your admin and check the logs for more information.");
            return redirect(auth.gui.routes.SignIn.login());
        }

        return redirect(routes.Home.home());
    }

    private OIDCProviderMetadata getProviderInfo() throws ParseException, URISyntaxException, AuthException {
        if (oidcProviderMetadata != null) return oidcProviderMetadata;

        try {
            URL providerConfigurationURL = new URI(Common.getOidcProviderConfigUrl()).toURL();
            InputStream stream = providerConfigurationURL.openStream();
            String providerInfo;
            try (java.util.Scanner s = new java.util.Scanner(stream)) {
                providerInfo = s.useDelimiter("\\A").hasNext() ? s.next() : "";
            }
            oidcProviderMetadata = OIDCProviderMetadata.parse(providerInfo);
            return oidcProviderMetadata;
        } catch (IOException e) {
            throw new AuthException("Could not get metadata from OIDC provider");
        }
    }

    private AuthorizationCode getAuthorisationCode(Http.Request request)
            throws AuthException, URISyntaxException, ParseException {
        AuthenticationResponse response = AuthenticationResponseParser.parse(new URI(request.uri()));

        // Check state, submitted with sign-in request, is still the same
        Optional<String> state = request.session().getOptional("oidcState");
        if (!state.isPresent() || !response.getState().getValue().equals(state.get())) {
            throw new AuthException("OIDC error - Unexpected authentication response");
        }

        if (response instanceof AuthenticationErrorResponse) {
            throw new AuthException("OIDC error - " + response.toErrorResponse().getErrorObject());
        }

        return response.toSuccessResponse().getAuthorizationCode();
    }

    /**
     * Construct the code grant from the code obtained from the authentication endpoint and the original callback URI
     * used at the authentication endpoint
     */
    private OIDCTokens requestToken(Http.Request request, AuthorizationCode authorizationCode)
            throws AuthException, URISyntaxException, ParseException, IOException {
        URI callback = new URI(auth.gui.routes.SignInOidc.callback().absoluteURL(request));
        AuthorizationGrant authorizationCodeGrant = new AuthorizationCodeGrant(authorizationCode, callback);
        ClientID clientID = new ClientID(Common.getOidcClientId());
        URI tokenEndpoint = getProviderInfo().getTokenEndpointURI();
        TokenRequest tokenRequest = new TokenRequest(tokenEndpoint, clientID, authorizationCodeGrant);
        TokenResponse tokenResponse = OIDCTokenResponseParser.parse(tokenRequest.toHTTPRequest().send());
        if (!tokenResponse.indicatesSuccess()) {
            throw new AuthException("OIDC error requesting access token");
        }
        OIDCTokenResponse successResponse = (OIDCTokenResponse) tokenResponse.toSuccessResponse();
        return successResponse.getOIDCTokens();
    }

    private void verifyIdToken(Http.Request request, JWT idToken, OIDCProviderMetadata providerMetadata)
            throws AuthException, MalformedURLException {
        Issuer issuer = providerMetadata.getIssuer();
        ClientID clientID = new ClientID(Common.getOidcClientId());
        JWSAlgorithm jwsAlg = JWSAlgorithm.parse(Common.getOidcIdTokenSigningAlgorithm());
        URL jwkSetURL = providerMetadata.getJWKSetURI().toURL();
        IDTokenValidator validator = new IDTokenValidator(issuer, clientID, jwsAlg, jwkSetURL);
        Nonce expectedNonce = request.session().getOptional("oidcNonce").map(Nonce::new).orElse(null);
        try {
            validator.validate(idToken, expectedNonce);
        } catch (BadJOSEException | JOSEException e) {
            throw new AuthException("OIDC token validation failed");
        }
    }

    private UserInfo getUserInfo(BearerAccessToken bearerAccessToken)
            throws AuthException, ParseException, IOException, URISyntaxException {
        URI userInfoEndpoint = getProviderInfo().getUserInfoEndpointURI();
        UserInfoRequest userInfoRequest = new UserInfoRequest(userInfoEndpoint, bearerAccessToken);
        HTTPResponse userInfoHTTPResponse = userInfoRequest.toHTTPRequest().send();
        UserInfoResponse userInfoResponse = UserInfoResponse.parse(userInfoHTTPResponse);
        if (!userInfoResponse.indicatesSuccess()) {
            throw new AuthException("OIDC error - " + userInfoResponse.toErrorResponse().getErrorObject());
        }
        return userInfoResponse.toSuccessResponse().getUserInfo();
    }

    private void persistUserIfNotExisting(UserInfo userInfo) throws AuthException {
        String username = userInfo.getSubject().getValue();
        User existingUser = userDao.findByUsername(username);
        if (existingUser == null) {
            NewUserModel newUserModel = new NewUserModel();
            newUserModel.setUsername(username);
            newUserModel.setName(userInfo.getName());
            newUserModel.setEmail(userInfo.getEmailAddress());
            newUserModel.setAuthByOidc(true);
            Form<NewUserModel> newUserForm = formFactory.form(NewUserModel.class).fill(newUserModel);

            newUserForm = authenticationValidation.validateNewUser(newUserForm);
            if (newUserForm.hasErrors()) {
                throw new AuthException("OIDC: user validation failed - " + newUserForm.errors().get(0).message());
            }

            userService.bindToUserAndPersist(newUserModel);
        } else if (!existingUser.isOidc()) {
            throw new AuthException("User exists already - but does not use OIDC sign in");
        }
    }

}
