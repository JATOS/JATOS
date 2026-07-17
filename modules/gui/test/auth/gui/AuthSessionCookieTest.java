package auth.gui;

import auth.gui.AuthAction.AuthMethod.AuthResult;
import http.common.Http.Context;
import http.common.HttpUtils;
import models.common.User;
import models.common.User.Role;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.test.Helpers;
import services.gui.UserService;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;

import static auth.gui.AuthAction.AuthMethod.AuthResult.State.DENIED;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static auth.gui.AuthService.SESSION_USERNAME;
import static messaging.common.FlashMessagingHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static play.mvc.Http.Status.FORBIDDEN;

/**
 * Unit tests for AuthSessionCookie.
 */
public class AuthSessionCookieTest {

    private AuthService authService;
    private UserService userService;
    private AuthSessionCookie authSessionCookie;

    private MockedStatic<HttpUtils> httpUtilsMocked;

    @Before
    public void setUp() {
        authService = mock(AuthService.class);
        userService = mock(UserService.class);
        authSessionCookie = new AuthSessionCookie(authService, userService);

        httpUtilsMocked = Mockito.mockStatic(HttpUtils.class);
        httpUtilsMocked.when(HttpUtils::isSessionCookieRequest).thenReturn(true);
        httpUtilsMocked.when(HttpUtils::isHtmlRequest).thenReturn(true);

        setCurrentContextPath("/");
    }

    @After
    public void tearDown() {
        if (httpUtilsMocked != null) httpUtilsMocked.close();
        Context.clear();
    }

    private static User makeUser(boolean active, boolean hasRole) {
        User u = mock(User.class);
        when(u.isActive()).thenReturn(active);
        when(u.hasRole(Collections.singleton(any()))).thenReturn(hasRole);
        when(u.getUsername()).thenReturn("bob");

        Context.current().args().put(SIGNEDIN_USER, u);
        return u;
    }

    private static void putSessionMarker() {
        Context.current().response().putSession(SESSION_USERNAME, "bob");
    }

    private static void setCurrentContextPath(String path) {
        Context.setCurrent(new Context(Helpers.fakeRequest("GET", path).build()));
    }

    @Test
    public void authenticate_wrongMethod_whenNotSessionCookieRequest() {
        httpUtilsMocked.when(HttpUtils::isSessionCookieRequest).thenReturn(false);

        AuthResult res = authSessionCookie.authenticate(EnumSet.of(Role.USER));
        assertThat(res.state).isEqualTo(AuthResult.State.WRONG_METHOD);
    }

    @Test
    public void authenticate_denied_whenNoSignedInUserInSession() {
        when(authService.getSignedinUserBySessionCookie()).thenReturn(null);
        putSessionMarker();

        AuthResult res = authSessionCookie.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(DENIED);
        assertThat(res.result).isNotNull();
        assertThat(res.result.header("Location").orElse("")).isEqualTo("/jatos/signin");
        assertThat(Context.current().response().session().data()).isEmpty();
        assertThat(Context.current().response().flash().data().containsKey(ERROR)).isTrue();
    }

    @Test
    public void authenticate_denied_whenSessionTimeout() {
        User u = makeUser(true, true);
        when(authService.getSignedinUserBySessionCookie()).thenReturn(u);
        when(authService.isSessionKeepSignedin()).thenReturn(false);
        when(authService.isSessionTimeout()).thenReturn(true);
        putSessionMarker();

        AuthResult res = authSessionCookie.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(DENIED);
        assertThat(res.result).isNotNull();
        assertThat(Context.current().response().session().data()).isEmpty();
        assertThat(Context.current().response().flash().data().containsKey(SUCCESS)).isTrue();
        verify(userService, never()).setLastSeen(any());
    }

    @Test
    public void authenticate_denied_whenInactivityTimeout() {
        User u = makeUser(true, true);
        when(authService.getSignedinUserBySessionCookie()).thenReturn(u);
        when(authService.isSessionKeepSignedin()).thenReturn(false);
        when(authService.isSessionTimeout()).thenReturn(false);
        when(authService.isInactivityTimeout()).thenReturn(true);
        putSessionMarker();

        AuthResult res = authSessionCookie.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(DENIED);
        assertThat(res.result).isNotNull();
        assertThat(Context.current().response().session().data()).isEmpty();
        assertThat(Context.current().response().flash().data().containsKey(SUCCESS)).isTrue();
        verify(userService, never()).setLastSeen(any());
    }

    @Test
    public void authenticate_denied_whenUserDeactivated() {
        User u = makeUser(false, true);
        when(authService.getSignedinUserBySessionCookie()).thenReturn(u);
        when(authService.isSessionKeepSignedin()).thenReturn(true); // skip timeouts
        putSessionMarker();

        AuthResult res = authSessionCookie.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(DENIED);
        assertThat(res.result).isNotNull();
        assertThat(Context.current().response().session().data()).isEmpty();
        assertThat(Context.current().response().flash().data().containsKey(WARNING)).isTrue();
        verify(userService, never()).setLastSeen(any());
    }

    @Test
    public void authenticate_denied_whenInsufficientRole_onNonGuiHtmlUrl_redirectsToSignin() {
        setCurrentContextPath("/publix/run");
        User u = makeUser(true, false);
        when(authService.getSignedinUserBySessionCookie()).thenReturn(u);
        when(authService.isSessionKeepSignedin()).thenReturn(true); // skip timeouts
        httpUtilsMocked.when(() -> HttpUtils.isGuiUrl("/publix/run")).thenReturn(false);
        putSessionMarker();

        AuthResult res = authSessionCookie.authenticate(EnumSet.of(Role.ADMIN));

        assertThat(res.state).isEqualTo(DENIED);
        assertThat(res.result).isNotNull();
        assertThat(res.result.header("Location").orElse("")).isEqualTo("/jatos/signin");
        assertThat(Context.current().response().getSession(SESSION_USERNAME).orElse(""))
                .isEqualTo("bob");
        assertThat(Context.current().response().flash().data().containsKey(ERROR)).isTrue();
        verify(userService, never()).setLastSeen(any());
    }

    @Test
    public void authenticate_denied_whenInsufficientRole_onNonHtmlRequest_returnsForbidden() {
        setCurrentContextPath("/jatos/api/v1/studies");
        User u = makeUser(true, false);
        when(authService.getSignedinUserBySessionCookie()).thenReturn(u);
        when(authService.isSessionKeepSignedin()).thenReturn(true); // skip timeouts
        httpUtilsMocked.when(HttpUtils::isHtmlRequest).thenReturn(false);
        httpUtilsMocked.when(() -> HttpUtils.isGuiUrl("/jatos/api/v1/studies")).thenReturn(false);
        putSessionMarker();

        AuthResult res = authSessionCookie.authenticate(EnumSet.of(Role.ADMIN));

        assertThat(res.state).isEqualTo(DENIED);
        assertThat(res.result).isNotNull();
        assertThat(res.result.status()).isEqualTo(FORBIDDEN);
        assertThat(Context.current().response().getSession(SESSION_USERNAME).orElse(""))
                .isEqualTo("bob");
        assertThat(Context.current().response().flash().data()).isEmpty();
        verify(userService, never()).setLastSeen(any());
    }

    @Test
    public void authenticate_authenticated_whenKeepSignedin_skipsTimeoutChecks() {
        User u = makeUser(true, true);
        when(authService.getSignedinUserBySessionCookie()).thenReturn(u);
        when(authService.isSessionKeepSignedin()).thenReturn(true);

        long before = Instant.now().toEpochMilli();
        AuthResult res = authSessionCookie.authenticate(EnumSet.of(Role.USER));
        long after = Instant.now().toEpochMilli();

        assertSuccessfulAuthentication(res, u, before, after);
        verify(authService, never()).isSessionTimeout();
        verify(authService, never()).isInactivityTimeout();
        verify(userService, times(1)).setLastSeen(u);
    }

    @Test
    public void authenticate_authenticated_whenNotKeepSignedin_andNoTimeouts() {
        User u = makeUser(true, true);
        when(authService.getSignedinUserBySessionCookie()).thenReturn(u);
        when(authService.isSessionKeepSignedin()).thenReturn(false);
        when(authService.isSessionTimeout()).thenReturn(false);
        when(authService.isInactivityTimeout()).thenReturn(false);

        long before = Instant.now().toEpochMilli();
        AuthResult res = authSessionCookie.authenticate(EnumSet.of(Role.USER));
        long after = Instant.now().toEpochMilli();

        assertSuccessfulAuthentication(res, u, before, after);
        verify(authService, times(1)).isSessionTimeout();
        verify(authService, times(1)).isInactivityTimeout();
        verify(userService, times(1)).setLastSeen(u);
    }

    private static void assertSuccessfulAuthentication(AuthResult res, User user, long before, long after) {
        assertThat(res.state).isEqualTo(AuthResult.State.AUTHENTICATED);
        assertThat(Context.current().response().isSessionChanged()).isTrue();
        assertThat(Context.current().response().getSession(AuthService.SESSION_LAST_ACTIVITY_TIME).isPresent())
                .isTrue();

        long refreshedLastActivityTime = Long.parseLong(Context.current().response().session()
                .get(AuthService.SESSION_LAST_ACTIVITY_TIME)
                .orElse("-1"));
        assertThat(refreshedLastActivityTime).isGreaterThanOrEqualTo(before);
        assertThat(refreshedLastActivityTime).isLessThanOrEqualTo(after);
        assertThat(Context.current().args().get(SIGNEDIN_USER)).isSameAs(user);
    }
}
