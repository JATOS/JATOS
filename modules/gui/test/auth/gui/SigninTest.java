package auth.gui;

import auth.gui.Signin.SigninData;
import com.fasterxml.jackson.databind.JsonNode;
import daos.common.LoginAttemptDao;
import daos.common.UserDao;
import exceptions.common.JatosException;
import general.common.ApiEnvelope.ErrorCode;
import general.common.MessagesStrings;
import http.common.Http.Context;
import json.common.DefaultJson;
import models.common.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.data.Form;
import play.data.FormFactory;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.UserService;

import javax.naming.NamingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.UNAUTHORIZED;
import static play.test.Helpers.contentAsString;

/**
 * Unit tests for Signin controller.
 */
public class SigninTest {

    private AuthService authService;
    private FormFactory formFactory;
    private UserDao userDao;
    private LoginAttemptDao loginAttemptDao;
    private UserService userService;

    private Signin controller;

    @Before
    public void setUp() {
        authService = mock(AuthService.class);
        formFactory = mock(FormFactory.class);
        userDao = mock(UserDao.class);
        loginAttemptDao = mock(LoginAttemptDao.class);
        userService = mock(UserService.class);
        DefaultJson defaultJson = new DefaultJson();

        controller = new Signin(authService, formFactory, userDao, loginAttemptDao, userService, defaultJson);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    @After
    public void tearDown() {
        Context.clear();
    }

    @SuppressWarnings("SameParameterValue")
    private SigninData makeSigninData(String username, String password, boolean keepSignedin) {
        SigninData d = new SigninData();
        d.setUsername(username);
        d.setPassword(password);
        d.setKeepSignedin(keepSignedin);
        return d;
    }

    @SuppressWarnings("unchecked")
    private void mockFormBinding(SigninData data) {
        Form<SigninData> emptyForm = (Form<SigninData>) mock(Form.class);
        Form<SigninData> boundForm = (Form<SigninData>) mock(Form.class);
        when(formFactory.form(SigninData.class)).thenReturn(emptyForm);
        when(emptyForm.bindFromRequest(any(Http.Request.class))).thenReturn(boundForm);
        when(boundForm.withDirectFieldAccess(eq(true))).thenReturn(boundForm);
        when(boundForm.get()).thenReturn(data);
    }

    private static Http.Request emptyRequest() {
        return Helpers.fakeRequest().remoteAddress("1.2.3.4").build();
    }

    @Test
    public void authenticate_unauthorized_onRepeatedSigninAttempt_beforeAuth() {
        SigninData data = makeSigninData("Bob", "pwd", false);
        mockFormBinding(data);
        when(authService.isRepeatedSigninAttempt(eq("bob"), eq("1.2.3.4"))).thenReturn(true);

        Result res = controller.authenticate(emptyRequest());

        assertThat(res.status()).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(res)).isEqualTo(MessagesStrings.FAILED_THREE_TIMES);
        verifyNoInteractions(userDao);
        verify(loginAttemptDao, never()).persist(any());
    }

    @Test
    public void authenticate_withLdapException() {
        SigninData data = makeSigninData("Bob", "pwd", false);
        mockFormBinding(data);
        when(authService.isRepeatedSigninAttempt(eq("bob"), eq("1.2.3.4"))).thenReturn(false);
        User user = mock(User.class);
        when(userDao.findByUsername("bob")).thenReturn(user);
        when(authService.authenticate(user, "pwd"))
                .thenThrow(new JatosException("ldap down", new NamingException("ldap-down"), ErrorCode.LDAP_ERROR));

        try {
            controller.authenticate(emptyRequest());
            fail("Expected JatosException");
        } catch (JatosException e) {
            assertThat(e.getCause()).isInstanceOf(NamingException.class);
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LDAP_ERROR);
        }
    }

    @Test
    public void authenticate_unauthorized_onFailedAuth_thenNotRepeatedAfterCreate() {
        SigninData data = makeSigninData("Bob", "pwd", false);
        mockFormBinding(data);
        when(authService.isRepeatedSigninAttempt(eq("bob"), eq("1.2.3.4"))).thenReturn(false, false);
        User user = mock(User.class);
        when(userDao.findByUsername("bob")).thenReturn(user);
        when(authService.authenticate(user, "pwd")).thenReturn(false);

        Result res = controller.authenticate(emptyRequest());

        assertThat(res.status()).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(res)).isEqualTo(MessagesStrings.INVALID_USER_OR_PASSWORD);
        verify(loginAttemptDao, times(1)).persist(any());
    }

    @Test
    public void authenticate_unauthorized_onFailedAuth_thenRepeatedAfterCreate() {
        SigninData data = makeSigninData("Bob", "pwd", false);
        mockFormBinding(data);
        // The first isRepeatedSigninAttempt is false, second is true
        when(authService.isRepeatedSigninAttempt(eq("bob"), eq("1.2.3.4"))).thenReturn(false, true);
        User user = mock(User.class);
        when(userDao.findByUsername("bob")).thenReturn(user);
        when(authService.authenticate(user, "pwd")).thenReturn(false);

        Result res = controller.authenticate(emptyRequest());

        assertThat(res.status()).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(res)).isEqualTo(MessagesStrings.FAILED_THREE_TIMES);
        verify(loginAttemptDao, times(1)).persist(any());
    }

    @Test
    public void authenticate_success_writesSession_setsLastSignin_removesAttempts_andReturnsJson() {
        // Arrange
        SigninData data = makeSigninData("Bob", "pwd", true);
        mockFormBinding(data);
        when(authService.isRepeatedSigninAttempt(eq("bob"), eq("1.2.3.4"))).thenReturn(false);
        User user = mock(User.class);
        when(userDao.findByUsername("bob")).thenReturn(user);
        when(authService.authenticate(user, "pwd")).thenReturn(true);
        when(authService.getRedirectPageAfterSignin(user)).thenReturn("/home");

        // Act
        Result res = controller.authenticate(emptyRequest());

        // Assert
        assertThat(res.status()).isEqualTo(OK);
        String body = contentAsString(res);
        JsonNode json = Json.parse(body);
        assertThat(json.get("redirectUrl").asText()).isEqualTo("/home");
        assertThat(json.get("userSigninTime").isNumber()).isTrue();

        verify(authService, times(1)).writeSessionCookie(eq("bob"), eq(true));
        verify(userService, times(1)).setLastSignin("bob");
        verify(loginAttemptDao, times(1)).removeByUsername("bob");
    }
}
