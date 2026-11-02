package auth.gui;

import exceptions.common.AuthException;
import http.common.Http.Context;
import http.common.HttpUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static auth.gui.AuthAction.AuthMethod.AuthResult;
import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static messaging.common.FlashMessagingHelper.ERROR;
import static models.common.User.Role.USER;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static play.mvc.Results.forbidden;
import static play.mvc.Results.ok;

public class AuthActionTest {

    private AuthSessionCookie session;
    private AuthApiToken token;
    private Action.Simple delegate;
    private Http.Request request;

    @Before
    public void setUp() {
        session = mock(AuthSessionCookie.class);
        token = mock(AuthApiToken.class);
        delegate = mock(Action.Simple.class);
        when(session.type()).thenReturn(SESSION);
        when(token.type()).thenReturn(TOKEN);
        request = new Http.RequestBuilder().build();
        Context.setCurrent(new Context(request));
    }

    @After
    public void tearDown() {
        Context.clear();
    }

    private AuthAction action(String method) throws Exception {
        AuthAction action = new AuthAction(session, token);
        action.configuration = getClass().getDeclaredMethod(method).getAnnotation(AuthAction.Auth.class);
        action.delegate = delegate;
        return action;
    }

    @AuthAction.Auth(roles = USER, types = TOKEN)
    private void tokenOnly() {
    }

    @Test
    public void successfulAuthenticationDelegatesOnceWithoutTryingSession() throws Exception {
        when(token.authenticate(EnumSet.of(USER))).thenReturn(AuthResult.authenticated());
        Result expected = ok("controller response");
        when(delegate.call(request)).thenReturn(CompletableFuture.completedFuture(expected));

        assertSame(expected, action("both").call(request).toCompletableFuture().join());

        verify(token).authenticate(EnumSet.of(USER));
        verify(session, never()).authenticate(any());
        verify(delegate).call(request);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    public void wrongMethodTriesNextPermittedMethod() throws Exception {
        when(token.authenticate(any())).thenReturn(AuthResult.wrongMethod());
        when(session.authenticate(EnumSet.of(USER))).thenReturn(AuthResult.authenticated());
        Result expected = ok();
        when(delegate.call(request)).thenReturn(CompletableFuture.completedFuture(expected));

        assertSame(expected, action("both").call(request).toCompletableFuture().join());

        var order = inOrder(token, session, delegate);
        order.verify(token).authenticate(EnumSet.of(USER));
        order.verify(session).authenticate(EnumSet.of(USER));
        order.verify(delegate).call(request);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    public void deniedAuthenticationStopsWithoutFallbackOrDelegation() throws Exception {
        Result expected = forbidden("denied");
        when(token.authenticate(any())).thenReturn(AuthResult.denied(expected));

        assertSame(expected, action("both").call(request).toCompletableFuture().join());

        verify(session, never()).authenticate(any());
        verifyNoInteractions(delegate);
    }

    @Test
    public void sessionOnlyDoesNotTryTokenAuthentication() throws Exception {
        when(session.authenticate(any())).thenReturn(AuthResult.authenticated());
        when(delegate.call(request)).thenReturn(CompletableFuture.completedFuture(ok()));

        action("sessionOnly").call(request).toCompletableFuture().join();

        verify(token, never()).authenticate(any());
        verify(session).authenticate(EnumSet.of(USER));
        verify(delegate).call(request);
    }

    @Test
    public void tokenOnlyDoesNotFallBackToSession() throws Exception {
        when(token.authenticate(any())).thenReturn(AuthResult.wrongMethod());
        try (var http = mockStatic(HttpUtils.class)) {
            http.when(HttpUtils::isHtmlRequest).thenReturn(false);
            assertAuthenticationFailure(action("tokenOnly"), request);
        }
        verify(token).authenticate(EnumSet.of(USER));
        verify(session, never()).authenticate(any());
        verifyNoInteractions(delegate);
    }

    @Test
    public void noSuccessfulMethodRedirectsHtmlRequestToSignin() throws Exception {
        when(token.authenticate(any())).thenReturn(AuthResult.wrongMethod());
        when(session.authenticate(any())).thenReturn(AuthResult.wrongMethod());
        try (var http = mockStatic(HttpUtils.class)) {
            http.when(HttpUtils::isHtmlRequest).thenReturn(true);
            Result result = action("both").call(request).toCompletableFuture().join();
            assertEquals(303, result.status());
            assertEquals("/jatos/signin", result.header("Location").orElse(""));
            assertEquals("Failed authentication", Context.current().response().flash().get(ERROR).orElse(""));
        }
        verifyNoInteractions(delegate);
    }

    @Test
    public void noSuccessfulMethodFailsNonHtmlRequestWithAuthException() throws Exception {
        when(token.authenticate(any())).thenReturn(AuthResult.wrongMethod());
        when(session.authenticate(any())).thenReturn(AuthResult.wrongMethod());
        try (var http = mockStatic(HttpUtils.class)) {
            http.when(HttpUtils::isHtmlRequest).thenReturn(false);
            assertAuthenticationFailure(action("both"), request);
        }
        verify(token).authenticate(EnumSet.of(USER));
        verify(session).authenticate(EnumSet.of(USER));
        verifyNoInteractions(delegate);
    }

    private static void assertAuthenticationFailure(AuthAction action, Http.Request request) {
        CompletionException exception = assertThrows(CompletionException.class,
                () -> action.call(request).toCompletableFuture().join());
        assertTrue(exception.getCause() instanceof AuthException);
        assertEquals("Failed authentication", exception.getCause().getMessage());
    }


    @AuthAction.Auth(roles = USER, types = {TOKEN, SESSION})
    private void both() {
    }

    @AuthAction.Auth(roles = USER)
    private void sessionOnly() {
    }

    @Test
    public void authorizationHeaderNeverFallsBackToSession() throws Exception {
        for (String method : new String[]{"both", "sessionOnly"}) {
            AuthSessionCookie session = mock(AuthSessionCookie.class);
            AuthApiToken token = mock(AuthApiToken.class);
            when(session.type()).thenReturn(SESSION);
            when(token.type()).thenReturn(TOKEN);
            when(token.authenticate(any())).thenReturn(AuthResult.wrongMethod());
            AuthAction action = new AuthAction(session, token);
            action.configuration = getClass().getDeclaredMethod(method).getAnnotation(AuthAction.Auth.class);
            try (var http = mockStatic(HttpUtils.class)) {
                http.when(HttpUtils::isHtmlRequest).thenReturn(false);
                var result = action.call(new Http.RequestBuilder().header("Authorization", "Basic unsupported").build());
                assertTrue(result.toCompletableFuture().isCompletedExceptionally());
                verify(session, never()).authenticate(any());
                if (method.equals("both")) verify(token).authenticate(any());
                else verify(token, never()).authenticate(any());
            }
        }
    }
}
