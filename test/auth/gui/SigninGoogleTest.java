package auth.gui;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import daos.common.UserDao;
import models.common.User;
import models.gui.NewUserModel;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import play.data.Form;
import play.data.FormFactory;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.UserService;
import testutils.ContextMocker;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SigninGoogle controller.
 *
 * @author Kristian Lange
 */
public class SigninGoogleTest {

    private AuthService authService;
    private SigninFormValidation signinFormValidation;
    private FormFactory formFactory;
    private UserDao userDao;
    private UserService userService;
    private SigninGoogle signinGoogleSpy;

    @Before
    public void setup() {
        ContextMocker.mock();
        authService = Mockito.mock(AuthService.class);
        signinFormValidation = Mockito.mock(SigninFormValidation.class);
        formFactory = Mockito.mock(FormFactory.class);
        userDao = Mockito.mock(UserDao.class);
        userService = Mockito.mock(UserService.class);
        // We need a spy of the real SigninGoogle because we have to mock the fetchOAuthGoogleIdToken method
        signinGoogleSpy = Mockito.spy(new SigninGoogle(authService, signinFormValidation, formFactory, userService, userDao));
    }

    private static Http.Request requestWithCredential() {
        return new Http.RequestBuilder()
                .method("POST")
                .bodyForm(java.util.Collections.singletonMap("credential", "token-xyz"))
                .build();
    }

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
        doReturn(null).when(signinGoogleSpy).fetchOAuthGoogleIdToken(anyString());

        Result res = signinGoogleSpy.signin(requestWithCredential());

        assertThat(res.redirectLocation().isPresent()).isTrue();
        assertThat(res.redirectLocation().get()).isEqualTo(auth.gui.routes.Signin.signin().url());
    }

    @Test
    public void signin_emailNotVerified_redirectsToSignin() throws Exception {
        GoogleIdToken token = Mockito.mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload("bob@example.org", false, "Bob", "http://pic"));
        doReturn(token).when(signinGoogleSpy).fetchOAuthGoogleIdToken(anyString());

        Result res = signinGoogleSpy.signin(requestWithCredential());

        assertThat(res.redirectLocation().isPresent()).isTrue();
        assertThat(res.redirectLocation().get()).isEqualTo(auth.gui.routes.Signin.signin().url());
    }

    @Test
    public void signin_existingGoogleUser_success_writesSession_andRedirects() throws Exception {
        GoogleIdToken token = Mockito.mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload("alice@example.org", true, "Alice", "http://pic"));
        doReturn(token).when(signinGoogleSpy).fetchOAuthGoogleIdToken(anyString());

        User user = new User("alice@example.org", "Alice", "alice@example.org");
        user.setAuthMethod(User.AuthMethod.OAUTH_GOOGLE);
        when(userDao.findByUsername("alice@example.org")).thenReturn(user);
        when(authService.getRedirectPageAfterSignin(user)).thenReturn("/home");

        Result res = signinGoogleSpy.signin(requestWithCredential());

        // Verify redirect
        assertThat(res.redirectLocation().orElse(null)).isEqualTo("/home");
        // Session contains googlePictureUrl
        assertThat(res.session().getOptional("googlePictureUrl").orElse("")).isEqualTo("http://pic");
        // Services called
        verify(authService).writeSessionCookie(any(play.mvc.Http.Session.class), eq("alice@example.org"), eq(false));
        verify(userService).setLastSignin("alice@example.org");
    }

    @Test
    public void signin_existingNonGoogleUser_redirectsToSignin() throws Exception {
        GoogleIdToken token = Mockito.mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload("carol@example.org", true, "Carol", "http://pic"));
        doReturn(token).when(signinGoogleSpy).fetchOAuthGoogleIdToken(anyString());

        User user = new User("carol@example.org", "Carol", "carol@example.org");
        user.setAuthMethod(User.AuthMethod.DB); // not Google
        when(userDao.findByUsername("carol@example.org")).thenReturn(user);

        Result res = signinGoogleSpy.signin(requestWithCredential());

        assertThat(res.redirectLocation().isPresent()).isTrue();
        assertThat(res.redirectLocation().get()).isEqualTo(auth.gui.routes.Signin.signin().url());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void signin_newUser_persists_andRedirects() throws Exception {
        GoogleIdToken token = Mockito.mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload("dave@example.org", true, "Dave", "http://pic"));
        doReturn(token).when(signinGoogleSpy).fetchOAuthGoogleIdToken(anyString());

        // No existing user
        when(userDao.findByUsername("dave@example.org")).thenReturn(null);

        // Mock form and validation flow
        Form<NewUserModel> form = Mockito.mock(Form.class);
        when(formFactory.form(NewUserModel.class)).thenReturn(form);
        when(form.fill(any(NewUserModel.class))).thenReturn(form);
        when(signinFormValidation.validateNewUser(form)).thenReturn(form);
        when(form.hasErrors()).thenReturn(false);

        User persisted = new User("dave@example.org", "Dave", "dave@example.org");
        persisted.setAuthMethod(User.AuthMethod.OAUTH_GOOGLE);
        when(userService.bindToUserAndPersist(any(NewUserModel.class))).thenReturn(persisted);
        when(authService.getRedirectPageAfterSignin(persisted)).thenReturn("/welcome");

        Result res = signinGoogleSpy.signin(requestWithCredential());

        assertThat(res.redirectLocation().orElse(null)).isEqualTo("/welcome");
        assertThat(res.session().getOptional("googlePictureUrl").orElse("")).isEqualTo("http://pic");
        verify(userService).setLastSignin("dave@example.org");

        // Verify that NewUserModel was populated
        ArgumentCaptor<NewUserModel> cap = ArgumentCaptor.forClass(NewUserModel.class);
        verify(userService).bindToUserAndPersist(cap.capture());
        NewUserModel num = cap.getValue();
        assertThat(num.getUsername()).isEqualTo("dave@example.org");
        assertThat(num.getName()).isEqualTo("Dave");
        assertThat(num.getEmail()).isEqualTo("dave@example.org");
        assertThat(num.getAuthMethod()).isEqualTo(User.AuthMethod.OAUTH_GOOGLE);
    }
}
