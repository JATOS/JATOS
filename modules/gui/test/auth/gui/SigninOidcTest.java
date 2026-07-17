package auth.gui;

import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import daos.common.UserDao;
import exceptions.common.AuthException;
import http.common.Http.Context;
import models.common.User;
import models.gui.NewUserProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.UserService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static play.test.Helpers.contentAsString;

/**
 * Unit tests for SigninOidc base class.
 */
public class SigninOidcTest {

    private AuthService authService;
    private UserDao userDao;
    private UserService userService;

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
        authService = Mockito.mock(AuthService.class);
        userDao = Mockito.mock(UserDao.class);
        userService = Mockito.mock(UserService.class);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    @After
    public void tearDown() {
        Context.clear();
    }

    @Test
    public void getUsername_returnsEmail_orSubject() {
        TestSigninOidc tso = new TestSigninOidc();
        UserInfo info = new UserInfo(new Subject("sub-1"));
        info.setEmailAddress("Alice@example.org");

        assertThat(tso.getUsername(info, "email")).isEqualTo("Alice@example.org");
        assertThat(tso.getUsername(info, "subject")).isEqualTo("sub-1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getUsername_throwsOnInvalidConfig() {
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
        Result res = tso.signin("https%3A%2F%2Fapp.example.com", true);

        // Assert
        String url = contentAsString(res);
        assertThat(url).startsWith("https://auth.example/authorize");
        // Should contain client_id and redirect_uri and scope
        assertThat(url).contains("client_id=client-123");
        assertThat(url).contains("redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback");
        assertThat(url).contains("scope=openid+profile+email");

        // Session flags present
        play.mvc.Http.Session session = Context.current().response().session();
        assertThat(session.get("oidcState").isPresent()).isTrue();
        assertThat(session.get("oidcNonce").isPresent()).isTrue();
        assertThat(session.get("keepSignedin").orElse("false")).isEqualTo("true");
    }

    @Test
    public void signin_withKeepSignedinFalse_setsSessionFlagToFalse() throws Exception {
        TestSigninOidc tso = new TestSigninOidc();
        OIDCProviderMetadata meta = Mockito.mock(OIDCProviderMetadata.class);
        when(meta.getAuthorizationEndpointURI()).thenReturn(URI.create("https://auth.example/authorize"));
        injectProviderMetadata(tso, meta);

        tso.signin("https%3A%2F%2Fapp.example.com", false);

        assertThat(Context.current().response().getSession("keepSignedin").orElse("true")).isEqualTo("false");
    }

    @Test
    public void getOrRegisterUser_existingOidcUser_returnsUser() throws Exception {
        TestSigninOidc tso = new TestSigninOidc();
        injectServices(tso);

        UserInfo info = new UserInfo(new Subject("sub-3"));
        info.setEmailAddress("Alice@example.org");

        User user = new User("alice@example.org", "Alice", "alice@example.org");
        user.setAuthMethod(User.AuthMethod.OIDC);
        when(userDao.findByUsername("alice@example.org")).thenReturn(user);

        User result = invokeGetOrRegisterUser(tso, info);

        assertThat(result).isSameAs(user);
    }

    @Test(expected = AuthException.class)
    public void getOrRegisterUser_existingNonOidcUser_throwsAuthException() throws Throwable {
        TestSigninOidc tso = new TestSigninOidc();
        injectServices(tso);

        UserInfo info = new UserInfo(new Subject("sub-4"));
        info.setEmailAddress("Bob@example.org");

        User user = new User("bob@example.org", "Bob", "bob@example.org");
        user.setAuthMethod(User.AuthMethod.DB);
        when(userDao.findByUsername("bob@example.org")).thenReturn(user);

        try {
            invokeGetOrRegisterUser(tso, info);
        } catch (ReflectiveOperationException e) {
            throw e.getCause();
        }
    }

    @Test
    public void getOrRegisterUser_newUser_registersNormalizedOidcUser() throws Exception {
        TestSigninOidc tso = new TestSigninOidc();
        injectServices(tso);

        UserInfo info = new UserInfo(new Subject("sub-5"));
        info.setEmailAddress("Carol@example.org");
        info.setName("Carol Example");

        when(userDao.findByUsername("carol@example.org")).thenReturn(null);

        User persisted = new User("carol@example.org", "Carol Example", "Carol@example.org");
        persisted.setAuthMethod(User.AuthMethod.OIDC);
        when(userService.registerUser(any(NewUserProperties.class))).thenReturn(persisted);

        User result = invokeGetOrRegisterUser(tso, info);

        assertThat(result).isSameAs(persisted);

        org.mockito.ArgumentCaptor<NewUserProperties> captor =
                org.mockito.ArgumentCaptor.forClass(NewUserProperties.class);
        verify(userService).registerUser(captor.capture());

        NewUserProperties newUserProperties = captor.getValue();
        assertThat(newUserProperties.getUsername()).isEqualTo("carol@example.org");
        assertThat(newUserProperties.getName()).isEqualTo("Carol Example");
        assertThat(newUserProperties.getEmail()).isEqualTo("Carol@example.org");
        assertThat(newUserProperties.getAuthMethod()).isEqualTo(User.AuthMethod.OIDC);
    }

    @Test
    public void getOrRegisterUser_newUser_usesGivenAndFamilyNameWhenNameMissing() throws Exception {
        TestSigninOidc tso = new TestSigninOidc();
        injectServices(tso);

        UserInfo info = new UserInfo(new Subject("sub-6"));
        info.setEmailAddress("Dana@example.org");
        info.setGivenName("Dana");
        info.setFamilyName("Example");

        when(userDao.findByUsername("dana@example.org")).thenReturn(null);

        User persisted = new User("dana@example.org", "Dana Example", "Dana@example.org");
        persisted.setAuthMethod(User.AuthMethod.OIDC);
        when(userService.registerUser(any(NewUserProperties.class))).thenReturn(persisted);

        invokeGetOrRegisterUser(tso, info);

        org.mockito.ArgumentCaptor<NewUserProperties> captor =
                org.mockito.ArgumentCaptor.forClass(NewUserProperties.class);
        verify(userService).registerUser(captor.capture());

        assertThat(captor.getValue().getName()).isEqualTo("Dana Example");
    }

    @Test
    public void getOrRegisterUser_newUser_usesNormalizedUsernameWhenNameMissing() throws Exception {
        TestSigninOidc tso = new TestSigninOidc();
        injectServices(tso);

        UserInfo info = new UserInfo(new Subject("sub-7"));
        info.setEmailAddress("Erin@example.org");

        when(userDao.findByUsername("erin@example.org")).thenReturn(null);

        User persisted = new User("erin@example.org", "erin@example.org", "Erin@example.org");
        persisted.setAuthMethod(User.AuthMethod.OIDC);
        when(userService.registerUser(any(NewUserProperties.class))).thenReturn(persisted);

        invokeGetOrRegisterUser(tso, info);

        org.mockito.ArgumentCaptor<NewUserProperties> captor =
                org.mockito.ArgumentCaptor.forClass(NewUserProperties.class);
        verify(userService).registerUser(captor.capture());

        assertThat(captor.getValue().getName()).isEqualTo("erin@example.org");
    }

    private void injectServices(SigninOidc so) throws Exception {
        injectField(so, "authService", authService);
        injectField(so, "userDao", userDao);
        injectField(so, "userService", userService);
    }

    private static User invokeGetOrRegisterUser(SigninOidc so, UserInfo userInfo) throws Exception {
        Method method = SigninOidc.class.getDeclaredMethod("getOrRegisterUser", UserInfo.class);
        method.setAccessible(true);
        return (User) method.invoke(so, userInfo);
    }

    private static void injectProviderMetadata(SigninOidc so, OIDCProviderMetadata meta) throws Exception {
        injectField(so, "oidcProviderMetadata", meta);
    }

    private static void injectField(SigninOidc so, String fieldName, Object value) throws Exception {
        Field f = SigninOidc.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(so, value);
    }
}
