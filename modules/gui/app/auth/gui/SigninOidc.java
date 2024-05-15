package auth.gui;

import com.google.common.base.Strings;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
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
import daos.common.UserDao;
import exceptions.gui.AuthException;
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
import utils.common.Helpers;

import javax.inject.Inject;
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
 * <p>
 * This class is meant to be extended by the actual OIDC implementations.
 * <p>
 * Using library: Nimbus OAuth 2.0 SDK with OpenID Connect extensions
 * https://connect2id.com/products/nimbus-oauth-openid-connect-sdk/guides/java-cookbook-for-openid-connect-public-clients
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
public abstract class SigninOidc extends Controller {

    private static final ALogger LOGGER = Logger.of(SigninOidc.class);

    @Inject private AuthService authService;
    @Inject private SigninFormValidation signinFormValidation;
    @Inject private FormFactory formFactory;
    @Inject private UserDao userDao;
    @Inject private UserService userService;

    private final OidcConfig oidcConfig;
    private OIDCProviderMetadata oidcProviderMetadata;

    /**
     * All configuration needed for an OIDC auth method
     */
    public static class OidcConfig {

        private final User.AuthMethod authMethod;
        private final String discoveryUrl;
        private final String callbackUrlPath;
        private String callbackUrl; // Filled during signin request
        private final String clientId;
        private final String clientSecret;
        private final String idTokenSigningAlgorithm;
        private final String successMsg;

        OidcConfig(User.AuthMethod authMethod, String discoveryUrl, String callbackUrlPath,
                String clientId, String clientSecret, String idTokenSigningAlgorithm, String successMsg) {
            this.authMethod = authMethod;
            this.discoveryUrl = discoveryUrl;
            this.callbackUrlPath = callbackUrlPath;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.idTokenSigningAlgorithm = idTokenSigningAlgorithm;
            this.successMsg = successMsg;
        }
    }

    SigninOidc(OidcConfig oidcConfig) {
        this.oidcConfig = oidcConfig;
    }

    @GuiAccessLogging
    @Transactional
    public final Result signin(Http.Request request, String realHostUrl, boolean keepSignedin)
            throws URISyntaxException, IOException, ParseException, AuthException {
        oidcConfig.callbackUrl = Helpers.urlDecode(realHostUrl) + oidcConfig.callbackUrlPath;
        ClientID clientID = new ClientID(oidcConfig.clientId);
        URI callback = new URI(oidcConfig.callbackUrl);
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

        // We use Play's session here to pass on information to the callback
        return ok(authRequest.toURI().toString())
                .addingToSession(request, "oidcState", state.getValue())
                .addingToSession(request, "oidcNonce", nonce.getValue())
                .addingToSession(request, "keepSignedin", String.valueOf(keepSignedin));
    }

    /**
     * Callback handed to the OIDC provider to be called after authentication
     */
    @Transactional
    public final Result callback(Http.Request request) {
        try {
            AuthorizationCode authorizationCode = getAuthorisationCode(request);

            OIDCTokens oidcTokens = requestToken(authorizationCode);

            verifyIdToken(request, oidcTokens.getIDToken(), getProviderInfo());

            BearerAccessToken bearerAccessToken = (BearerAccessToken) oidcTokens.getAccessToken();
            UserInfo userInfo = getUserInfo(bearerAccessToken);

            User user = persistUserIfNotExisting(userInfo);

            String normalizedUsername = User.normalizeUsername(userInfo.getSubject().getValue());
            boolean keepSignedin = Boolean.parseBoolean(request.session().getOptional("keepSignedin").orElse("false"));
            authService.writeSessionCookie(session(), normalizedUsername, keepSignedin);
            userService.setLastSignin(normalizedUsername);

            if (!Strings.isNullOrEmpty(oidcConfig.successMsg)) {
                FlashScopeMessaging.success(oidcConfig.successMsg);
            }
            return redirect(authService.getRedirectPageAfterSignin(user));
        } catch (AuthException e) {
            LOGGER.warn(".callback: " + e.getMessage());
            FlashScopeMessaging.error(e.getMessage());
            return redirect(auth.gui.routes.Signin.signin());
        } catch (Exception e) {
            LOGGER.error(".callback: " + e.getMessage());
            FlashScopeMessaging.error("OIDC error - contact your admin and check the logs for more information.");
            return redirect(auth.gui.routes.Signin.signin());
        }
    }

    private OIDCProviderMetadata getProviderInfo() throws ParseException, URISyntaxException, AuthException {
        if (oidcProviderMetadata != null) return oidcProviderMetadata;

        try {
            URL providerConfigurationURL = new URI(oidcConfig.discoveryUrl).toURL();
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
            throw new AuthException("OIDC error - " + response.toErrorResponse().getErrorObject().getDescription());
        }

        return response.toSuccessResponse().getAuthorizationCode();
    }

    /**
     * Construct the code grant from the code obtained from the authentication endpoint and the original callback URI
     * used at the authentication endpoint
     */
    private OIDCTokens requestToken(AuthorizationCode authorizationCode)
            throws AuthException, URISyntaxException, ParseException, IOException {
        URI callback = new URI(oidcConfig.callbackUrl);
        TokenRequest tokenRequest = buildTokenRequest(authorizationCode, callback);
        TokenResponse tokenResponse = OIDCTokenResponseParser.parse(tokenRequest.toHTTPRequest().send());
        if (!tokenResponse.indicatesSuccess()) {
            throw new AuthException("OIDC error requesting access token - "
                    + tokenResponse.toErrorResponse().getErrorObject().getDescription());
        }
        OIDCTokenResponse successResponse = (OIDCTokenResponse) tokenResponse.toSuccessResponse();
        return successResponse.getOIDCTokens();
    }

    private TokenRequest buildTokenRequest(AuthorizationCode authorizationCode, URI callback)
            throws ParseException, URISyntaxException, AuthException {
        AuthorizationGrant authorizationCodeGrant = new AuthorizationCodeGrant(authorizationCode, callback);
        ClientID clientID = new ClientID(oidcConfig.clientId);
        URI tokenEndpoint = getProviderInfo().getTokenEndpointURI();
        TokenRequest tokenRequest;
        if (Strings.isNullOrEmpty(oidcConfig.clientSecret)) {
            tokenRequest = new TokenRequest(tokenEndpoint, clientID, authorizationCodeGrant);
        } else {
            Secret clientSecret = new Secret(oidcConfig.clientSecret);
            ClientAuthentication clientAuth = new ClientSecretBasic(clientID, clientSecret);
            tokenRequest = new TokenRequest(tokenEndpoint, clientAuth, authorizationCodeGrant);
        }
        return tokenRequest;
    }

    private void verifyIdToken(Http.Request request, JWT idToken, OIDCProviderMetadata providerMetadata)
            throws AuthException, MalformedURLException {
        Issuer issuer = providerMetadata.getIssuer();
        ClientID clientID = new ClientID(oidcConfig.clientId);
        JWSAlgorithm jwsAlg = JWSAlgorithm.parse(oidcConfig.idTokenSigningAlgorithm);
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
            throw new AuthException("OIDC error - "
                    + userInfoResponse.toErrorResponse().getErrorObject().getDescription());
        }
        return userInfoResponse.toSuccessResponse().getUserInfo();
    }

    private User persistUserIfNotExisting(UserInfo userInfo) throws AuthException {
        String normalizedUsername = User.normalizeUsername(userInfo.getSubject().getValue());
        User user = userDao.findByUsername(normalizedUsername);
        if (user == null) {
            NewUserModel newUserModel = new NewUserModel();
            newUserModel.setUsername(normalizedUsername);
            newUserModel.setName(getName(userInfo));
            newUserModel.setEmail(userInfo.getEmailAddress());
            newUserModel.setAuthMethod(oidcConfig.authMethod);

            Form<NewUserModel> newUserForm = formFactory.form(NewUserModel.class).fill(newUserModel);
            newUserForm = signinFormValidation.validateNewUser(newUserForm);
            if (newUserForm.hasErrors()) {
                throw new AuthException("OIDC: user validation failed - " + newUserForm.errors().get(0).message());
            }

            user = userService.bindToUserAndPersist(newUserModel);
        } else if (user.getAuthMethod() != oidcConfig.authMethod) {
            throw new AuthException("User exists already - but does not use OIDC sign in");
        }
        return user;
    }

    private String getName(UserInfo userInfo) {
        if (!Strings.isNullOrEmpty(userInfo.getName())) {
            return userInfo.getName();
        }
        if (!Strings.isNullOrEmpty(userInfo.getGivenName()) || !Strings.isNullOrEmpty(userInfo.getFamilyName())) {
            String givenName = userInfo.getGivenName() != null ? userInfo.getGivenName() : "";
            String familyName = userInfo.getFamilyName() != null ? userInfo.getFamilyName() : "";
            return (givenName + " " + familyName).trim();
        }
        return userInfo.getSubject().getValue();
    }

}
