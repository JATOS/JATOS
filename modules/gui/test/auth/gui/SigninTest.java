package auth.gui;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.LoginAttemptDao;
import daos.common.UserDao;
import general.common.MessagesStrings;
import models.common.User;
import org.junit.Before;
import org.junit.Test;
import play.data.Form;
import play.data.FormFactory;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.UserService;
import testutils.gui.ContextMocker;

import javax.naming.NamingException;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.contentAsString;

/**
 * Unit tests for Signin controller.
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
public class SigninTest {

    private AuthService authService;
    private FormFactory formFactory;
    private UserDao userDao;
    private LoginAttemptDao loginAttemptDao;
    private UserService userService;

    private Signin controller;

    @Before
    public void setUp() {
        ContextMocker.mock();

        authService = mock(AuthService.class);
        formFactory = mock(FormFactory.class);
        userDao = mock(UserDao.class);
        loginAttemptDao = mock(LoginAttemptDao.class);
        userService = mock(UserService.class);

        controller = new Signin(authService, formFactory, userDao, loginAttemptDao, userService);
    }

    private Signin.SigninData makeSigninData(String username, String password, boolean keepSignedin) {
        Signin.SigninData d = new Signin.SigninData();
        d.setUsername(username);
        d.setPassword(password);
        d.setKeepSignedin(keepSignedin);
        return d;
    }

    @SuppressWarnings("unchecked")
    private void mockFormBinding(Signin.SigninData data) {
        Form<Signin.SigninData> emptyForm = (Form<Signin.SigninData>) mock(Form.class);
        Form<Signin.SigninData> boundForm = (Form<Signin.SigninData>) mock(Form.class);
        when(formFactory.form(Signin.SigninData.class)).thenReturn(emptyForm);
        when(emptyForm.bindFromRequest(any(Http.Request.class))).thenReturn(boundForm);
        when(boundForm.withDirectFieldAccess(eq(true))).thenReturn(boundForm);
        when(boundForm.get()).thenReturn(data);
    }

    private static Http.Request emptyRequest() {
        return new Http.RequestBuilder().build();
        // Controller methods use Context.current() for request/session, so the argument isn't heavily used.
    }

    @Test
    public void authenticate_unauthorized_onRepeatedSigninAttempt_beforeAuth() {
        Signin.SigninData data = makeSigninData("Bob", "pwd", false);
        mockFormBinding(data);
        when(authService.isRepeatedSigninAttempt(eq("bob"), eq("1.2.3.4"))).thenReturn(true);

        Result res = controller.authenticate(emptyRequest());

        assertThat(res.status()).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(res)).isEqualTo(MessagesStrings.FAILED_THREE_TIMES);
        verifyNoInteractions(userDao);
        verify(loginAttemptDao, never()).create(any());
    }

    @Test
    public void authenticate_internalServerError_onLdapException() throws Exception {
        Signin.SigninData data = makeSigninData("Bob", "pwd", false);
        mockFormBinding(data);
        when(authService.isRepeatedSigninAttempt(eq("bob"), eq("1.2.3.4"))).thenReturn(false);
        User user = mock(User.class);
        when(userDao.findByUsername("bob")).thenReturn(user);
        when(authService.authenticate(user, "pwd")).thenThrow(new NamingException("ldap-down"));

        Result res = controller.authenticate(emptyRequest());

        assertThat(res.status()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(contentAsString(res)).isEqualTo(MessagesStrings.LDAP_PROBLEMS);
    }

    @Test
    public void authenticate_unauthorized_onFailedAuth_thenNotRepeatedAfterCreate() throws Exception {
        Signin.SigninData data = makeSigninData("Bob", "pwd", false);
        mockFormBinding(data);
        when(authService.isRepeatedSigninAttempt(eq("bob"), eq("1.2.3.4"))).thenReturn(false, false);
        User user = mock(User.class);
        when(userDao.findByUsername("bob")).thenReturn(user);
        when(authService.authenticate(user, "pwd")).thenReturn(false);

        Result res = controller.authenticate(emptyRequest());

        assertThat(res.status()).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(res)).isEqualTo(MessagesStrings.INVALID_USER_OR_PASSWORD);
        verify(loginAttemptDao, times(1)).create(any());
    }

    @Test
    public void authenticate_unauthorized_onFailedAuth_thenRepeatedAfterCreate() throws Exception {
        Signin.SigninData data = makeSigninData("Bob", "pwd", false);
        mockFormBinding(data);
        // The first isRepeatedSigninAttempt is false, second is true
        when(authService.isRepeatedSigninAttempt(eq("bob"), eq("1.2.3.4"))).thenReturn(false, true);
        User user = mock(User.class);
        when(userDao.findByUsername("bob")).thenReturn(user);
        when(authService.authenticate(user, "pwd")).thenReturn(false);

        Result res = controller.authenticate(emptyRequest());

        assertThat(res.status()).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(res)).isEqualTo(MessagesStrings.FAILED_THREE_TIMES);
        verify(loginAttemptDao, times(1)).create(any());
    }

    @Test
    public void authenticate_success_writesSession_setsLastSignin_removesAttempts_andReturnsJson() throws Exception {
        // Arrange
        Signin.SigninData data = makeSigninData("Bob", "pwd", true);
        mockFormBinding(data);
        when(authService.isRepeatedSigninAttempt(eq("bob"), eq("1.2.3.4"))).thenReturn(false);
        User user = mock(User.class);
        when(userDao.findByUsername("bob")).thenReturn(user);
        when(authService.authenticate(user, "pwd")).thenReturn(true);
        when(authService.getRedirectPageAfterSignin(user)).thenReturn("/home");
        // When writeSessionCookie is called, populate signinTime so the controller can return it as JSON
        doAnswer(inv -> {
            Http.Session s = inv.getArgument(0);
            s.put(AuthService.SESSION_SIGNIN_TIME, String.valueOf(System.currentTimeMillis()));
            s.put(AuthService.SESSION_USERNAME, "bob");
            return null;
        }).when(authService).writeSessionCookie(any(Http.Session.class), eq("bob"), eq(true));

        // Act
        Result res = controller.authenticate(emptyRequest());

        // Assert
        assertThat(res.status()).isEqualTo(OK);
        String body = contentAsString(res);
        JsonNode json = Json.parse(body);
        assertThat(json.get("redirectUrl").asText()).isEqualTo("/home");
        assertThat(json.get("userSigninTime").isNumber()).isTrue();

        verify(authService, times(1)).writeSessionCookie(any(Http.Session.class), eq("bob"), eq(true));
        verify(userService, times(1)).setLastSignin("bob");
        verify(loginAttemptDao, times(1)).removeByUsername("bob");
    }
}
