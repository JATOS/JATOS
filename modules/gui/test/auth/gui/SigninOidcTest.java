package auth.gui;

import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import exceptions.gui.AuthException;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import play.mvc.Http;
import play.mvc.Result;
import testutils.gui.ContextMocker;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static play.test.Helpers.contentAsString;

/**
 * Unit tests for SigninOidc base class.
 *
 * @author Kristian Lange
 */
public class SigninOidcTest {

    private static class TestSigninOidc extends SigninOidc {
        TestSigninOidc() {
            this("off", "");
        }

        TestSigninOidc(String pkceMode, String clientSecret) {
            super(new OidcConfig(
                    models.common.User.AuthMethod.OIDC,
                    "https://discovery.example/.well-known/openid-configuration",
                    "/callback",
                    "client-123",
                    clientSecret,
                    Arrays.asList("openid", "profile", "email"),
                    pkceMode,
                    "email",
                    "RS256",
                    ""
            ));
        }
    }

    @Before
    public void setup() {
        ContextMocker.mock();
    }

    private static Http.Request emptyRequest() {
        return new Http.RequestBuilder().build();
    }

    @Test
    public void getUsername_returnsEmail_orSubject() throws Exception {
        TestSigninOidc tso = new TestSigninOidc();
        UserInfo info = new UserInfo(new Subject("sub-1"));
        info.setEmailAddress("Alice@example.org");

        assertThat(tso.getUsername(info, "email")).isEqualTo("Alice@example.org");
        assertThat(tso.getUsername(info, "subject")).isEqualTo("sub-1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getUsername_throwsOnInvalidConfig() throws Exception {
        TestSigninOidc tso = new TestSigninOidc();
        UserInfo info = new UserInfo(new Subject("sub-2"));
        tso.getUsername(info, "unknown");
    }

    @Test
    public void signin_buildsAuthUrl_andSetsSessionFlags() throws Exception {
        // Arrange controller and inject mocked provider metadata to avoid network
        TestSigninOidc tso = new TestSigninOidc();
        OIDCProviderMetadata meta = Mockito.mock(OIDCProviderMetadata.class);
        when(meta.getAuthorizationEndpointURI()).thenReturn(URI.create("https://auth.example/authorize"));
        injectProviderMetadata(tso, meta);

        // Act
        Result res = tso.signin(emptyRequest(), "https%3A%2F%2Fapp.example.com", true);

        // Assert
        String url = contentAsString(res);
        assertThat(url).startsWith("https://auth.example/authorize");
        // Should contain client_id and redirect_uri and scope
        assertThat(url).contains("client_id=client-123");
        assertThat(url).contains("redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback");
        assertThat(url).contains("scope=openid+profile+email");

        // Session flags present
        play.mvc.Http.Session session = res.session();
        assertThat(session.getOptional(tso.sessionKey("state")).isPresent()).isTrue();
        assertThat(session.getOptional(tso.sessionKey("nonce")).isPresent()).isTrue();
        assertThat(session.getOptional(tso.sessionKey("keepSignedin")).orElse("false")).isEqualTo("true");
    }

    @Test
    public void verifyUserInfoSubject_acceptsMatchingSubjects() throws Exception {
        SigninOidc.verifyUserInfoSubject(idTokenClaims("Subject-A"), new UserInfo(new Subject("Subject-A")));
    }

    @Test(expected = AuthException.class)
    public void verifyUserInfoSubject_rejectsDifferentSubjects() throws Exception {
        SigninOidc.verifyUserInfoSubject(idTokenClaims("Subject-A"), new UserInfo(new Subject("Subject-B")));
    }

    @Test(expected = AuthException.class)
    public void verifyUserInfoSubject_rejectsCaseDifference() throws Exception {
        SigninOidc.verifyUserInfoSubject(idTokenClaims("Subject-A"), new UserInfo(new Subject("subject-a")));
    }

    @Test(expected = AuthException.class)
    public void verifyUserInfoSubject_rejectsMissingUserInfoSubject() throws Exception {
        SigninOidc.verifyUserInfoSubject(idTokenClaims("Subject-A"), Mockito.mock(UserInfo.class));
    }

    @Test(expected = AuthException.class)
    public void verifyUserInfoSubject_rejectsMissingIdTokenSubject() throws Exception {
        SigninOidc.verifyUserInfoSubject(idTokenClaims(null), new UserInfo(new Subject("Subject-A")));
    }

    private static TestSigninOidc provider(String mode, String secret, String metadata) throws Exception {
        TestSigninOidc controller = new TestSigninOidc(mode, secret);
        OIDCProviderMetadata provider = OIDCProviderMetadata.parse("{"
                + "\"issuer\":\"https://auth.example\","
                + "\"authorization_endpoint\":\"https://auth.example/authorize\","
                + "\"token_endpoint\":\"https://auth.example/token\","
                + "\"jwks_uri\":\"https://auth.example/jwks\","
                + "\"response_types_supported\":[\"code\"],"
                + "\"subject_types_supported\":[\"public\"],"
                + "\"id_token_signing_alg_values_supported\":[\"RS256\"]"
                + metadata + "}");
        injectProviderMetadata(controller, provider);
        return controller;
    }

    private static Result start(TestSigninOidc controller) throws Exception {
        return controller.signin(emptyRequest(), "https%3A%2F%2Fapp.example.com", false);
    }

    private static Http.Request callbackRequest(Result result) {
        return new Http.RequestBuilder().session(result.session().data()).build();
    }

    @Test
    public void pkce_offPreservesRequestsEvenWhenProviderSupportsS256() throws Exception {
        TestSigninOidc controller = provider("off", "secret", ",\"code_challenge_methods_supported\":[\"S256\"]");
        Result result = start(controller);
        assertThat(contentAsString(result)).doesNotContain("code_challenge");
        assertThat(result.session().getOptional(controller.sessionKey("verifier")).isPresent()).isFalse();
        TokenRequest token = controller.buildTokenRequest(callbackRequest(result), new AuthorizationCode("code"));
        assertThat(token.toHTTPRequest().getQuery()).doesNotContain("code_verifier");
        assertThat(token.getClientAuthentication()).isInstanceOf(ClientSecretBasic.class);
    }

    @Test
    public void pkce_requiredSendsMatchingS256AndVerifierWithoutMetadata() throws Exception {
        TestSigninOidc controller = provider("required", "secret", "");
        Result result = start(controller);
        String verifier = result.session().getOptional(controller.sessionKey("verifier")).get();
        // Compute independently of Nimbus to verify the actual S256 wire value.
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        AuthenticationRequest auth = AuthenticationRequest.parse(URI.create(contentAsString(result)));
        assertThat(auth.getCodeChallenge().getValue()).isEqualTo(challenge);
        assertThat(auth.getCodeChallengeMethod()).isEqualTo(CodeChallengeMethod.S256);
        assertThat(contentAsString(result)).doesNotContain(verifier);
        TokenRequest token = controller.buildTokenRequest(callbackRequest(result), new AuthorizationCode("code"));
        assertThat(token.toHTTPRequest().getQuery()).contains("code_verifier=" + verifier);
        assertThat(token.getClientAuthentication()).isInstanceOf(ClientSecretBasic.class);
    }

    @Test
    public void pkce_autoUsesOnlyAdvertisedS256() throws Exception {
        for (String metadata : Arrays.asList("", ",\"code_challenge_methods_supported\":[]",
                ",\"code_challenge_methods_supported\":[\"plain\"]")) {
            TestSigninOidc controller = provider("auto", "", metadata);
            Result result = start(controller);
            assertThat(contentAsString(result)).doesNotContain("code_challenge");
            assertThat(controller.buildTokenRequest(callbackRequest(result), new AuthorizationCode("code"))
                    .toHTTPRequest().getQuery()).doesNotContain("code_verifier");
        }
        TestSigninOidc controller = provider("auto", "", ",\"code_challenge_methods_supported\":[\"plain\",\"S256\"]");
        Result result = start(controller);
        assertThat(contentAsString(result)).contains("code_challenge_method=S256");
        TokenRequest token = controller.buildTokenRequest(callbackRequest(result), new AuthorizationCode("code"));
        assertThat(token.toHTTPRequest().getQuery()).contains("code_verifier=");
        assertThat(token.getClientAuthentication()).isNull();
        assertThat(token.getClientID().getValue()).isEqualTo("client-123");
    }

    @Test
    public void pkce_autoRemembersDecisionFromAuthorizationRequest() throws Exception {
        TestSigninOidc controller = provider("auto", "", ",\"code_challenge_methods_supported\":[\"S256\"]");
        Result result = start(controller);
        OIDCProviderMetadata changedMetadata = Mockito.mock(OIDCProviderMetadata.class);
        when(changedMetadata.getTokenEndpointURI()).thenReturn(URI.create("https://auth.example/token"));
        injectProviderMetadata(controller, changedMetadata);
        assertThat(controller.buildTokenRequest(callbackRequest(result), new AuthorizationCode("code"))
                .toHTTPRequest().getQuery()).contains("code_verifier=");
    }

    @Test
    public void pkce_parallelBrowsersKeepTheirOwnVerifierAndCallback() throws Exception {
        TestSigninOidc controller = provider("required", "", "");
        Result first = start(controller);
        Result second = controller.signin(emptyRequest(), "https%3A%2F%2Fsecond.example.com", false);
        String firstVerifier = first.session().getOptional(controller.sessionKey("verifier")).get();
        String secondVerifier = second.session().getOptional(controller.sessionKey("verifier")).get();
        assertThat(firstVerifier).isNotEqualTo(secondVerifier);
        AuthorizationCodeGrant grant = (AuthorizationCodeGrant) controller.buildTokenRequest(
                callbackRequest(first), new AuthorizationCode("code")).getAuthorizationGrant();
        assertThat(grant.getCodeVerifier().getValue()).isEqualTo(firstVerifier);
        assertThat(grant.getRedirectionURI()).isEqualTo(URI.create("https://app.example.com/callback"));
    }

    @Test(expected = AuthException.class)
    public void pkce_missingVerifierFailsClosed() throws Exception {
        TestSigninOidc controller = provider("required", "", "");
        Http.Session session = start(controller).session();
        session.removing(controller.sessionKey("verifier"));
        controller.buildTokenRequest(new Http.RequestBuilder().session(session.data()).build(), new AuthorizationCode("code"));
    }

    @Test(expected = AuthException.class)
    public void pkce_invalidVerifierFailsClosed() throws Exception {
        TestSigninOidc controller = provider("required", "", "");
        Http.Session session = start(controller).session();
        session.adding(controller.sessionKey("verifier"), "too-short");
        controller.buildTokenRequest(new Http.RequestBuilder().session(session.data()).build(), new AuthorizationCode("code"));
    }

    @Test(expected = AuthException.class)
    public void pkce_missingDecisionFailsClosedInAutoMode() throws Exception {
        TestSigninOidc controller = provider("auto", "", "");
        Http.Session session = start(controller).session();
        session.removing(controller.sessionKey("pkce"));
        controller.buildTokenRequest(new Http.RequestBuilder().session(session.data()).build(), new AuthorizationCode("code"));
    }

    @Test(expected = AuthException.class)
    public void pkce_requiredRejectsNonPkceTransaction() throws Exception {
        TestSigninOidc controller = provider("required", "", "");
        Http.Session session = start(controller).session();
        session.adding(controller.sessionKey("pkce"), "false");
        controller.buildTokenRequest(new Http.RequestBuilder().session(session.data()).build(), new AuthorizationCode("code"));
    }

    @Test
    public void pkce_offRemovesOldVerifierAndPreservesOtherSessionData() throws Exception {
        TestSigninOidc controller = provider("off", "", "");
        Http.Request request = new Http.RequestBuilder()
                .session(controller.sessionKey("verifier"), "old-value")
                .session("unrelated", "keep").build();
        Result result = controller.signin(request, "https%3A%2F%2Fapp.example.com", false);
        assertThat(result.session().getOptional(controller.sessionKey("verifier")).isPresent()).isFalse();
        assertThat(result.session().getOptional("unrelated").get()).isEqualTo("keep");
    }

    @Test
    public void clearLoginSessionRemovesOnlyThisProvidersTransaction() throws Exception {
        TestSigninOidc controller = provider("required", "", "");
        Http.Session session = start(controller).session();
        session.adding("username", "alice");
        session.adding("oidc.ORCID.verifier", "other-provider");
        controller.clearLoginSession(session);
        assertThat(session.data()).hasSize(2);
        assertThat(session.getOptional("username").get()).isEqualTo("alice");
        assertThat(session.getOptional("oidc.ORCID.verifier").get()).isEqualTo("other-provider");
    }

    @Test
    public void pkce_providerRejectionDoesNotRetryAndClearsLoginTransaction() throws Exception {
        AtomicInteger tokenCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            tokenCalls.incrementAndGet();
            byte[] response = "{\"error\":\"invalid_grant\",\"error_description\":\"PKCE verification failed\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            TestSigninOidc controller = provider("required", "secret", "");
            OIDCProviderMetadata metadata = Mockito.mock(OIDCProviderMetadata.class);
            when(metadata.getAuthorizationEndpointURI()).thenReturn(URI.create("https://auth.example/authorize"));
            when(metadata.getTokenEndpointURI()).thenReturn(URI.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/token"));
            injectProviderMetadata(controller, metadata);
            Result login = start(controller);
            Http.Context.current().session().adding(login.session().data());
            Http.Context.current().session().adding("unrelated", "keep");
            String state = login.session().getOptional(controller.sessionKey("state")).get();
            Http.Request request = new Http.RequestBuilder().uri("/callback?code=code&state=" + state)
                    .session(login.session().data()).build();
            Result result = controller.callback(request);
            assertThat(result.status()).isEqualTo(303);
            assertThat(tokenCalls.get()).isEqualTo(1);
            assertThat(Http.Context.current().session().data()).hasSize(1);
            assertThat(Http.Context.current().session().getOptional("unrelated").get()).isEqualTo("keep");
        } finally {
            server.stop(0);
        }
    }

    private static IDTokenClaimsSet idTokenClaims(String subject) {
        IDTokenClaimsSet claims = Mockito.mock(IDTokenClaimsSet.class);
        when(claims.getSubject()).thenReturn(subject == null ? null : new Subject(subject));
        return claims;
    }

    private static void injectProviderMetadata(SigninOidc so, OIDCProviderMetadata meta) throws Exception {
        Field f = SigninOidc.class.getDeclaredField("oidcProviderMetadata");
        f.setAccessible(true);
        f.set(so, meta);
    }
}
