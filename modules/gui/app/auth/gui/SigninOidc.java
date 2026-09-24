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
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.UserDao;
import exceptions.gui.AuthException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.ValidationException;
import general.gui.FlashScopeMessaging;
import models.common.User;
import models.gui.NewUserProperties;
import play.Logger;
import play.Logger.ALogger;
import play.data.validation.ValidationError;
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
import java.util.List;
import java.util.Optional;

/**
 * OpenID Connect (OIDC) authentication using Authorization Code Flow with optional Proof Key for Code Exchange (PKCE).
 * OIDC is just used for authentication - authorization and session management are still done with the session cookies
 * from the Play Framework.
 *
 * This class is meant to be extended by the actual OIDC implementations.
 *
 * Using library: Nimbus OAuth 2.0 SDK with OpenID Connect extensions
 * https://connect2id.com/products/nimbus-oauth-openid-connect-sdk/guides/java-cookbook-for-openid-connect-public-clients
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
public abstract class SigninOidc extends Controller {

    private static final ALogger LOGGER = Logger.of(SigninOidc.class);

    @Inject
    private AuthService authService;
    @Inject
    private UserDao userDao;
    @Inject
    private UserService userService;

    private final OidcConfig oidcConfig;
    private OIDCProviderMetadata oidcProviderMetadata;

    /**
     * Configuration needed for an OIDC auth implementation
     */
    public static class OidcConfig {

        /**
         * Constant used to distinguish between the OIDC methods
         */
        private final User.AuthMethod authMethod;

        /**
         * OIDC discovery URL. Usually similar to https://server.com/.well-known/openid-configuration
         */
        private final String discoveryUrl;

        /**
         * Callback URL that is invoked after authentication. Usually it routes to the 'callback' method in this class.
         */
        private final String callbackUrlPath;

        /**
         * OIDC client ID
         */
        private final String clientId;

        /**
         * OIDC client secret
         */
        private final String clientSecret;

        /**
         * List of scopes. For OIDC, scopes can be used to request that specific sets of information be made available
         * as Claim Values.
         */
        private final String[] scope;

        /**
         * PKCE policy: "off" preserves the legacy flow, "auto" uses S256 when advertised by discovery, and "required"
         * always uses S256. Validated when configuration is loaded in Common.
         */
        private final String pkceMode;

        /**
         * Defines from which OIDC claim the username of the user stored in JATOS' database should be taken from.
         */
        private final String usernameFrom;

        /**
         * OIDC token signing algorithm
         */
        private final String idTokenSigningAlgorithm;

        /**
         * A message that is shown to the signing-in user in the browser.
         */
        private final String successMsg;

        OidcConfig(User.AuthMethod authMethod, String discoveryUrl, String callbackUrlPath, String clientId,
                   String clientSecret, List<String> scope, String pkceMode, String usernameFrom,
                   String idTokenSigningAlgorithm, String successMsg) {
            this.authMethod = authMethod;
            this.discoveryUrl = discoveryUrl;
            this.callbackUrlPath = callbackUrlPath;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.scope = scope.toArray(new String[0]);
            this.pkceMode = pkceMode;
            this.usernameFrom = usernameFrom;
            this.idTokenSigningAlgorithm = idTokenSigningAlgorithm;
            this.successMsg = successMsg;
        }
    }

    SigninOidc(OidcConfig oidcConfig) {
        this.oidcConfig = oidcConfig;
    }

    /**
     * Initiates the OpenID Connect (OIDC) authentication process by constructing a URI for an authentication request.
     * This URI is returned in the response and can then be used by the GUI in the browser to start the authentication.
     * The method stores the OIDC state, nonce, and a flag indicating whether to keep the user signed in, in the Play
     * session.
     *
     * @param request      the HTTP request received from the client
     * @param realHostUrl  the real host URL to be used for constructing the callback URL
     * @param keepSignedin a flag indicating whether the user should remain signed in
     * @return the authentication request URI as String
     * @throws URISyntaxException if an invalid URI is encountered during the process
     * @throws ParseException     if parsing operations fail while working with OIDC configurations
     * @throws AuthException      if an authentication-related error occurs
     */
    @GuiAccessLogging
    @Transactional
    public final Result signin(Http.Request request, String realHostUrl, boolean keepSignedin)
            throws URISyntaxException, ParseException, AuthException {
        String callbackUrl = Helpers.urlDecode(realHostUrl) + oidcConfig.callbackUrlPath;
        ClientID clientID = new ClientID(oidcConfig.clientId);
        URI callback = new URI(callbackUrl);
        State state = new State();
        Nonce nonce = new Nonce();
        CodeVerifier verifier = usePkce() ? new CodeVerifier() : null;
        AuthenticationRequest authRequest = new AuthenticationRequest.Builder(
                new ResponseType("code"),
                new Scope(oidcConfig.scope),
                clientID,
                callback
        ).endpointURI(getProviderInfo().getAuthorizationEndpointURI())
                .state(state)
                .nonce(nonce)
                .codeChallenge(verifier, CodeChallengeMethod.S256)
                .build();

        Http.Session loginSession = new Http.Session(request.session().data());
        loginSession.put(sessionKey("state"), state.getValue());
        loginSession.put(sessionKey("nonce"), nonce.getValue());
        loginSession.put(sessionKey("callback"), callbackUrl);
        loginSession.put(sessionKey("keepSignedin"), String.valueOf(keepSignedin));
        loginSession.put(sessionKey("pkce"), String.valueOf(verifier != null));
        loginSession.remove(sessionKey("verifier"));
        if (verifier != null) {
            loginSession.put(sessionKey("verifier"), verifier.getValue());
        }
        return ok(authRequest.toURI().toString()).withSession(loginSession);
    }

    /**
     * Callback handed to the OIDC provider to be called after authentication
     */
    @Transactional
    public final Result callback(Http.Request request) {
        try {
            AuthorizationCode authorizationCode = getAuthorisationCode(request);

            OIDCTokens oidcTokens = requestToken(request, authorizationCode);

            IDTokenClaimsSet idTokenClaims = verifyIdToken(request, oidcTokens.getIDToken(), getProviderInfo());

            BearerAccessToken bearerAccessToken = (BearerAccessToken) oidcTokens.getAccessToken();
            UserInfo userInfo = getUserInfo(bearerAccessToken);
            verifyUserInfoSubject(idTokenClaims, userInfo);

            User user = getOrRegisterUser(userInfo);

            String normalizedUsername = getNormalizedUsername(userInfo);
            boolean keepSignedin = Boolean.parseBoolean(request.session().getOptional(sessionKey("keepSignedin")).orElse("false"));
            authService.writeSessionCookie(session(), normalizedUsername, keepSignedin);
            userService.setLastSignin(normalizedUsername);

            if (!Strings.isNullOrEmpty(oidcConfig.successMsg)) {
                FlashScopeMessaging.success(oidcConfig.successMsg);
            }
            return redirect(authService.getRedirectPageAfterSignin(user));
        } catch (AuthException | ValidationException | ForbiddenException e) {
            LOGGER.warn(".callback: " + e.getMessage());
            FlashScopeMessaging.error(e.getMessage());
            return redirect(auth.gui.routes.Signin.signin());
        } catch (Exception e) {
            LOGGER.error(".callback: " + e.getMessage());
            FlashScopeMessaging.error("OIDC error - contact your admin and check the logs for more information.");
            return redirect(auth.gui.routes.Signin.signin());
        } finally {
            clearLoginSession(session());
        }
    }

    String sessionKey(String name) {
        return "oidc." + oidcConfig.authMethod.name() + "." + name;
    }

    void clearLoginSession(Http.Session session) {
        for (String name : List.of("state", "nonce", "callback", "keepSignedin", "pkce", "verifier")) {
            session.remove(sessionKey(name));
        }
    }

    private boolean usePkce() throws ParseException, URISyntaxException, AuthException {
        if (oidcConfig.pkceMode.equals("required")) return true;
        if (oidcConfig.pkceMode.equals("off")) return false;
        List<CodeChallengeMethod> methods = getProviderInfo().getCodeChallengeMethods();
        return methods != null && methods.contains(CodeChallengeMethod.S256);
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
        Optional<String> state = request.session().getOptional(sessionKey("state"));
        if (state.isEmpty() || response.getState() == null || !response.getState().getValue().equals(state.get())) {
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
    private OIDCTokens requestToken(Http.Request request, AuthorizationCode authorizationCode)
            throws AuthException, URISyntaxException, ParseException, IOException {
        TokenRequest tokenRequest = buildTokenRequest(request, authorizationCode);
        TokenResponse tokenResponse = OIDCTokenResponseParser.parse(tokenRequest.toHTTPRequest().send());
        if (!tokenResponse.indicatesSuccess()) {
            throw new AuthException("OIDC error requesting access token - "
                    + tokenResponse.toErrorResponse().getErrorObject().getDescription());
        }
        OIDCTokenResponse successResponse = (OIDCTokenResponse) tokenResponse.toSuccessResponse();
        return successResponse.getOIDCTokens();
    }

    TokenRequest buildTokenRequest(Http.Request request, AuthorizationCode authorizationCode)
            throws ParseException, URISyntaxException, AuthException {
        URI callback = new URI(request.session().getOptional(sessionKey("callback"))
                .orElseThrow(() -> new AuthException("OIDC error - Missing login transaction")));
        String pkce = request.session().getOptional(sessionKey("pkce"))
                .orElseThrow(() -> new AuthException("OIDC error - Missing PKCE transaction state"));
        CodeVerifier verifier = null;
        if (pkce.equals("true")) {
            String value = request.session().getOptional(sessionKey("verifier"))
                    .orElseThrow(() -> new AuthException("OIDC error - Missing PKCE verifier"));
            try {
                verifier = new CodeVerifier(value);
            } catch (IllegalArgumentException e) {
                throw new AuthException("OIDC error - Invalid PKCE verifier");
            }
        } else if (!pkce.equals("false") || oidcConfig.pkceMode.equals("required")) {
            throw new AuthException("OIDC error - PKCE required or invalid transaction state");
        }
        AuthorizationGrant authorizationCodeGrant = new AuthorizationCodeGrant(authorizationCode, callback, verifier);
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

    private IDTokenClaimsSet verifyIdToken(Http.Request request, JWT idToken, OIDCProviderMetadata providerMetadata)
            throws AuthException, MalformedURLException {
        Issuer issuer = providerMetadata.getIssuer();
        ClientID clientID = new ClientID(oidcConfig.clientId);
        JWSAlgorithm jwsAlg = JWSAlgorithm.parse(oidcConfig.idTokenSigningAlgorithm);
        URL jwkSetURL = providerMetadata.getJWKSetURI().toURL();
        IDTokenValidator validator = new IDTokenValidator(issuer, clientID, jwsAlg, jwkSetURL);
        Nonce expectedNonce = request.session().getOptional(sessionKey("nonce")).map(Nonce::new).orElse(null);
        try {
            return validator.validate(idToken, expectedNonce);
        } catch (BadJOSEException | JOSEException e) {
            throw new AuthException("OIDC token validation failed");
        }
    }

    /**
     * OIDC Core 5.3.2 requires UserInfo to identify the same subject as the validated ID token. Compare the original,
     * case-sensitive subjects before any username normalization.
     */
    static void verifyUserInfoSubject(IDTokenClaimsSet idTokenClaims, UserInfo userInfo) throws AuthException {
        if (idTokenClaims.getSubject() == null || !idTokenClaims.getSubject().equals(userInfo.getSubject())) {
            throw new AuthException("OIDC UserInfo subject does not match ID token subject");
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

    private User getOrRegisterUser(UserInfo userInfo) throws AuthException, ValidationException, ForbiddenException {
        String normalizedUsername = getNormalizedUsername(userInfo);
        User user = userDao.findByUsername(normalizedUsername);
        if (user != null && user.getAuthMethod() != oidcConfig.authMethod) {
            throw new AuthException("User exists - but does not use OIDC sign in");
        } else if (user != null) {
            return user;
        } else {
            NewUserProperties newUserProperties = new NewUserProperties();
            newUserProperties.setUsername(normalizedUsername);
            newUserProperties.setName(getName(userInfo));
            newUserProperties.setEmail(userInfo.getEmailAddress());
            newUserProperties.setAuthMethod(oidcConfig.authMethod);
            List<ValidationError> errors = newUserProperties.validate();
            if (errors != null && !errors.isEmpty()) {
                throw new ValidationException(errors.get(0).message());
            }
            return userService.registerUser(newUserProperties);
        }
    }

    protected String getUsername(UserInfo userInfo, String usernameFrom) throws AuthException {
        switch (usernameFrom) {
            case "email":
                return userInfo.getEmailAddress();
            case "subject":
                return userInfo.getSubject().getValue();
            default:
                throw new IllegalArgumentException("Unknown value in configuration - usernameFrom: " + oidcConfig.usernameFrom);
        }
    }

    private String getNormalizedUsername(UserInfo userInfo) throws AuthException {
        return User.normalizeUsername(getUsername(userInfo, oidcConfig.usernameFrom));
    }

    private String getName(UserInfo userInfo) throws AuthException {
        if (!Strings.isNullOrEmpty(userInfo.getName())) {
            return userInfo.getName();
        }
        if (!Strings.isNullOrEmpty(userInfo.getGivenName()) || !Strings.isNullOrEmpty(userInfo.getFamilyName())) {
            String givenName = userInfo.getGivenName() != null ? userInfo.getGivenName() : "";
            String familyName = userInfo.getFamilyName() != null ? userInfo.getFamilyName() : "";
            return (givenName + " " + familyName).trim();
        }
        return getNormalizedUsername(userInfo);
    }

}
