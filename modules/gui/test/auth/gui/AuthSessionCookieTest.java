package auth.gui;

import auth.gui.AuthAction.AuthMethod.AuthResult;
import controllers.gui.Home;
import models.common.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.UserService;
import testutils.common.ContextMocker;
import utils.common.Helpers;

import javax.inject.Provider;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static play.mvc.Results.forbidden;

/**
 * Unit tests for AuthSessionCookie.
 *
 * @author Kristian Lange
 */
public class AuthSessionCookieTest {

    private AuthService authService;
    private UserService userService;
    private Provider<Home> homeProvider;
    private Home home;

    private AuthSessionCookie authSessionCookie;

    private MockedStatic<Helpers> helpersMock;

    @Before
    public void setUp() {
        // Install a mutable Http.Context for RequestScope and messaging utils
        ContextMocker.mock();

        authService = mock(AuthService.class);
        userService = mock(UserService.class);

        // Mock Home.home() to return a forbidden result
        home = mock(Home.class);
        //noinspection unchecked
        homeProvider = (Provider<Home>) mock(Provider.class);
        when(homeProvider.get()).thenReturn(home);
        when(home.home(any(Http.Request.class), anyInt())).thenAnswer(inv -> forbidden("home-forbidden"));

        authSessionCookie = new AuthSessionCookie(homeProvider, authService, userService);

        helpersMock = Mockito.mockStatic(Helpers.class);
        // Default: this is a session-cookie GUI request and not Ajax
        helpersMock.when(() -> Helpers.isSessionCookieRequest(any())).thenReturn(true);
        helpersMock.when(Helpers::isAjax).thenReturn(false);
    }

    @After
    public void tearDown() {
        if (helpersMock != null) helpersMock.close();
    }

    private static Http.Request emptyRequest() {
        return new Http.RequestBuilder().build();
    }

    private static User makeUser(boolean active, boolean hasRole) {
        User u = mock(User.class);
        when(u.isActive()).thenReturn(active);
        when(u.hasRole(any())).thenReturn(hasRole);
        when(u.getUsername()).thenReturn("bob");
        return u;
    }

    @Test
    public void authenticate_wrongMethod_whenNotSessionCookieRequest() {
        helpersMock.when(() -> Helpers.isSessionCookieRequest(any())).thenReturn(false);

        AuthResult res = authSessionCookie.authenticate(emptyRequest(), User.Role.USER);
        assertThat(res.state).isEqualTo(AuthResult.State.WRONG_METHOD);
    }

    @Test
    public void authenticate_denied_whenNoSignedInUserInSession() {
        when(authService.getSignedinUserBySessionCookie(any())).thenReturn(null);

        AuthResult res = authSessionCookie.authenticate(emptyRequest(), User.Role.USER);

        assertThat(res.state).isEqualTo(AuthResult.State.DENIED);
        assertThat(res.result).isNotNull();
        // Should redirect to Signin (no need to check target here)
    }

    @Test
    public void authenticate_denied_whenSessionTimeout() {
        User u = makeUser(true, true);
        when(authService.getSignedinUserBySessionCookie(any())).thenReturn(u);
        when(authService.isSessionKeepSignedin(any())).thenReturn(false);
        when(authService.isSessionTimeout(any())).thenReturn(true);

        AuthResult res = authSessionCookie.authenticate(emptyRequest(), User.Role.USER);

        assertThat(res.state).isEqualTo(AuthResult.State.DENIED);
        assertThat(res.result).isNotNull();
        verify(userService, never()).setLastSeen(any());
    }

    @Test
    public void authenticate_denied_whenInactivityTimeout() {
        User u = makeUser(true, true);
        when(authService.getSignedinUserBySessionCookie(any())).thenReturn(u);
        when(authService.isSessionKeepSignedin(any())).thenReturn(false);
        when(authService.isSessionTimeout(any())).thenReturn(false);
        when(authService.isInactivityTimeout(any())).thenReturn(true);

        AuthResult res = authSessionCookie.authenticate(emptyRequest(), User.Role.USER);

        assertThat(res.state).isEqualTo(AuthResult.State.DENIED);
        assertThat(res.result).isNotNull();
        verify(userService, never()).setLastSeen(any());
    }

    @Test
    public void authenticate_denied_whenUserDeactivated() {
        User u = makeUser(false, true);
        when(authService.getSignedinUserBySessionCookie(any())).thenReturn(u);
        when(authService.isSessionKeepSignedin(any())).thenReturn(true); // skip timeouts

        AuthResult res = authSessionCookie.authenticate(emptyRequest(), User.Role.USER);

        assertThat(res.state).isEqualTo(AuthResult.State.DENIED);
        assertThat(res.result).isNotNull();
        verify(userService, never()).setLastSeen(any());
    }

    @Test
    public void authenticate_denied_whenInsufficientRole() {
        User u = makeUser(true, false);
        when(authService.getSignedinUserBySessionCookie(any())).thenReturn(u);
        when(authService.isSessionKeepSignedin(any())).thenReturn(true); // skip timeouts

        AuthResult res = authSessionCookie.authenticate(emptyRequest(), User.Role.ADMIN);

        assertThat(res.state).isEqualTo(AuthResult.State.DENIED);
        assertThat(res.result).isNotNull();
        verify(homeProvider, times(1)).get();
        verify(home, times(1)).home(any(Http.Request.class), eq(Http.Status.FORBIDDEN));
        verify(userService, never()).setLastSeen(any());
    }

    @Test
    public void authenticate_authenticated_successPath_setsLastSeen_andAddsPostHook() {
        User u = makeUser(true, true);
        when(authService.getSignedinUserBySessionCookie(any())).thenReturn(u);
        when(authService.isSessionKeepSignedin(any())).thenReturn(true); // skip timeout checks

        AuthResult res = authSessionCookie.authenticate(emptyRequest(), User.Role.USER);

        assertThat(res.state).isEqualTo(AuthResult.State.AUTHENTICATED);
        verify(userService, times(1)).setLastSeen(u);

        // Apply postHook and expect the session to be amended (we can only check it's non-null and applied)
        Result original = forbidden("orig");
        Result amended = res.postHook.apply(original);
        assertThat(amended).isNotNull();
        // Same status as original, but with session touched - we can't directly read session here, but ensure not null
        assertThat(amended.status()).isEqualTo(original.status());
    }
}
