package auth.gui;

import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
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
            super(new OidcConfig(
                    models.common.User.AuthMethod.OIDC,
                    "https://discovery.example/.well-known/openid-configuration",
                    "/callback",
                    "client-123",
                    "",
                    Arrays.asList("openid", "profile", "email"),
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
        assertThat(session.getOptional("oidcState").isPresent()).isTrue();
        assertThat(session.getOptional("oidcNonce").isPresent()).isTrue();
        assertThat(session.getOptional("keepSignedin").orElse("false")).isEqualTo("true");
    }

    private static void injectProviderMetadata(SigninOidc so, OIDCProviderMetadata meta) throws Exception {
        Field f = SigninOidc.class.getDeclaredField("oidcProviderMetadata");
        f.setAccessible(true);
        f.set(so, meta);
    }
}
