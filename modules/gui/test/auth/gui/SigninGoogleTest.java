package auth.gui;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import daos.common.UserDao;
import http.common.Http.Context;
import models.common.User;
import models.gui.NewUserProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SigninGoogle controller.
 */
public class SigninGoogleTest {

    private AuthService authService;
    private UserDao userDao;
    private UserService userService;
    private GoogleIdTokenVerifier googleIdTokenVerifier;
    private SigninGoogle signinGoogle;

    @Before
    public void setup() {
        authService = Mockito.mock(AuthService.class);
        userDao = Mockito.mock(UserDao.class);
        userService = Mockito.mock(UserService.class);
        googleIdTokenVerifier = Mockito.mock(GoogleIdTokenVerifier.class);
        signinGoogle = new SigninGoogle(authService, userService, userDao, googleIdTokenVerifier);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    @After
    public void tearDown() {
        Context.clear();
    }

    private static Http.Request requestWithCredential() {
        return new Http.RequestBuilder()
                .method("POST")
                .bodyForm(java.util.Collections.singletonMap("credential", "token-xyz"))
                .build();
    }

    @SuppressWarnings("SameParameterValue")
    private static GoogleIdToken.Payload payload(String email, boolean verified, String name, String picture) {
        GoogleIdToken.Payload p = new GoogleIdToken.Payload();
        p.setEmail(email);
        p.setEmailVerified(verified);
        if (name != null) p.set("name", name);
        if (picture != null) p.set("picture", picture);
        return p;
    }

    @Test
    public void signin_invalidToken_redirectsToSignin() throws Exception {
        when(googleIdTokenVerifier.verify("token-xyz")).thenReturn(null);

        Result res = signinGoogle.signin(requestWithCredential());

        assertThat(res.redirectLocation().isPresent()).isTrue();
        assertThat(res.redirectLocation().get()).isEqualTo(auth.gui.routes.Signin.signin().url());
    }

    @Test
    public void signin_emailNotVerified_redirectsToSignin() throws Exception {
        GoogleIdToken token = Mockito.mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload("bob@example.org", false, "Bob", "http://pic"));
        when(googleIdTokenVerifier.verify("token-xyz")).thenReturn(token);

        Result res = signinGoogle.signin(requestWithCredential());

        assertThat(res.redirectLocation().isPresent()).isTrue();
        assertThat(res.redirectLocation().get()).isEqualTo(auth.gui.routes.Signin.signin().url());
    }

    @Test
    public void signin_existingGoogleUser_success_writesSession_andRedirects() throws Exception {
        GoogleIdToken token = Mockito.mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload("alice@example.org", true, "Alice", "http://pic"));
        when(googleIdTokenVerifier.verify("token-xyz")).thenReturn(token);

        User user = new User("alice@example.org", "Alice", "alice@example.org");
        user.setAuthMethod(User.AuthMethod.OAUTH_GOOGLE);
        when(userDao.findByUsername("alice@example.org")).thenReturn(user);
        when(authService.getRedirectPageAfterSignin(user)).thenReturn("/home");

        Result res = signinGoogle.signin(requestWithCredential());

        // Verify redirect
        assertThat(res.redirectLocation().orElse(null)).isEqualTo("/home");
        // Session contains googlePictureUrl
        assertThat(Context.current().response().getSession("googlePictureUrl").orElseThrow()).isEqualTo("http://pic");
        // Services called
        verify(authService).writeSessionCookie(eq("alice@example.org"), eq(false));
        verify(userService).setLastSignin("alice@example.org");
    }

    @Test
    public void signin_existingNonGoogleUser_redirectsToSignin() throws Exception {
        GoogleIdToken token = Mockito.mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload("carol@example.org", true, "Carol", "http://pic"));
        when(googleIdTokenVerifier.verify("token-xyz")).thenReturn(token);

        User user = new User("carol@example.org", "Carol", "carol@example.org");
        user.setAuthMethod(User.AuthMethod.DB); // not Google
        when(userDao.findByUsername("carol@example.org")).thenReturn(user);

        Result res = signinGoogle.signin(requestWithCredential());

        assertThat(res.redirectLocation().isPresent()).isTrue();
        assertThat(res.redirectLocation().get()).isEqualTo(auth.gui.routes.Signin.signin().url());
    }

    @Test
    public void signin_newUser_persists_andRedirects() throws Exception {
        GoogleIdToken token = Mockito.mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload("dave@example.org", true, "Dave", "http://pic"));
        when(googleIdTokenVerifier.verify("token-xyz")).thenReturn(token);

        // No existing user
        when(userDao.findByUsername("dave@example.org")).thenReturn(null);

        User persisted = new User("dave@example.org", "Dave", "dave@example.org");
        persisted.setAuthMethod(User.AuthMethod.OAUTH_GOOGLE);
        when(userService.registerUser(any(NewUserProperties.class))).thenReturn(persisted);
        when(authService.getRedirectPageAfterSignin(persisted)).thenReturn("/welcome");

        Result res = signinGoogle.signin(requestWithCredential());

        assertThat(res.redirectLocation().orElse(null)).isEqualTo("/welcome");
        assertThat(Context.current().response().getSession("googlePictureUrl").orElse("")).isEqualTo("http://pic");
        verify(userService).setLastSignin("dave@example.org");

        // Verify that NewUserModel was populated
        ArgumentCaptor<NewUserProperties> cap = ArgumentCaptor.forClass(NewUserProperties.class);
        verify(userService).registerUser(cap.capture());
        NewUserProperties num = cap.getValue();
        assertThat(num.getUsername()).isEqualTo("dave@example.org");
        assertThat(num.getName()).isEqualTo("Dave");
        assertThat(num.getEmail()).isEqualTo("dave@example.org");
        assertThat(num.getAuthMethod()).isEqualTo(User.AuthMethod.OAUTH_GOOGLE);
    }
}
