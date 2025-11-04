package auth.gui;

import daos.common.ApiTokenDao;
import general.common.Common;
import general.common.RequestScope;
import models.common.ApiToken;
import models.common.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import utils.common.HashUtils;
import utils.common.Helpers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthApiToken.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
public class AuthApiTokenTest {

    private ApiTokenDao apiTokenDao;
    private AuthApiToken authApiToken;

    private MockedStatic<Helpers> helpersMock;
    private MockedStatic<Common> commonMock;

    @Before
    public void setUp() {
        // Install a mutable Http.Context for RequestScope
        testutils.gui.ContextMocker.mock();

        apiTokenDao = mock(ApiTokenDao.class);
        JPAApi jpa = mock(JPAApi.class);
        // Make withTransaction execute the provided Supplier
        //noinspection unchecked
        when(jpa.withTransaction(any(Supplier.class))).thenAnswer(inv -> {
            Supplier<?> s = inv.getArgument(0);
            return s.get();
        });
        authApiToken = new AuthApiToken(apiTokenDao, jpa);

        helpersMock = Mockito.mockStatic(Helpers.class);
        commonMock = Mockito.mockStatic(Common.class);
        // Default: API request and JATOS API allowed
        helpersMock.when(() -> Helpers.isApiRequest(any())).thenReturn(true);
        //noinspection ResultOfMethodCallIgnored
        commonMock.when(Common::isJatosApiAllowed).thenReturn(true);
    }

    @After
    public void tearDown() {
        if (helpersMock != null) helpersMock.close();
        if (commonMock != null) commonMock.close();
    }

    private static String makeTokenWithChecksum(String body31) {
        String checksum = HashUtils.getChecksum(body31);
        return "jap_" + body31 + checksum;
    }

    private static Http.Request requestWithAuth(String fullToken) {
        return new Http.RequestBuilder()
                .header("Authorization", "Bearer " + fullToken)
                .build();
    }

    private static String randomBody31() {
        // Create a deterministic 31-char token body
        String base = UUID.randomUUID().toString().replace("-", "") + "abcdefghijklmnopqrstuvwxyz";
        return base.substring(0, 31);
    }

    @Test
    public void authenticate_wrongMethod_whenNotApiRequest() {
        helpersMock.when(() -> Helpers.isApiRequest(any())).thenReturn(false);

        Http.Request req = new Http.RequestBuilder().build();
        AuthAction.AuthMethod.AuthResult res = authApiToken.authenticate(req, User.Role.USER);

        assertThat(res.state).isEqualTo(AuthAction.AuthMethod.AuthResult.State.WRONG_METHOD);
    }

    @Test
    public void authenticate_denied_whenApiUsageDisabled() {
        //noinspection ResultOfMethodCallIgnored
        commonMock.when(Common::isJatosApiAllowed).thenReturn(false);

        String body = randomBody31();
        String token = makeTokenWithChecksum(body);
        Http.Request req = requestWithAuth(token);

        AuthAction.AuthMethod.AuthResult res = authApiToken.authenticate(req, User.Role.USER);
        assertThat(res.state).isEqualTo(AuthAction.AuthMethod.AuthResult.State.DENIED);
    }

    @Test
    public void authenticate_denied_onBadChecksum() {
        String body = randomBody31();
        String token = "jap_" + body + "ABCDEF"; // wrong checksum
        Http.Request req = requestWithAuth(token);

        AuthAction.AuthMethod.AuthResult res = authApiToken.authenticate(req, User.Role.USER);
        assertThat(res.state).isEqualTo(AuthAction.AuthMethod.AuthResult.State.DENIED);
    }

    @Test
    public void authenticate_denied_whenTokenNotFound() {
        String body = randomBody31();
        String token = makeTokenWithChecksum(body);
        String hash = HashUtils.getHash(token, HashUtils.SHA_256);
        when(apiTokenDao.findByHash(hash)).thenReturn(Optional.empty());

        Http.Request req = requestWithAuth(token);
        AuthAction.AuthMethod.AuthResult res = authApiToken.authenticate(req, User.Role.USER);

        assertThat(res.state).isEqualTo(AuthAction.AuthMethod.AuthResult.State.DENIED);
        verify(apiTokenDao).findByHash(hash);
    }

    @Test
    public void authenticate_denied_whenTokenInactive() {
        String body = randomBody31();
        String token = makeTokenWithChecksum(body);
        String hash = HashUtils.getHash(token, HashUtils.SHA_256);

        ApiToken t = new ApiToken();
        t.setActive(false);
        t.setUser(new User());
        when(apiTokenDao.findByHash(hash)).thenReturn(Optional.of(t));

        Http.Request req = requestWithAuth(token);
        AuthAction.AuthMethod.AuthResult res = authApiToken.authenticate(req, User.Role.USER);

        assertThat(res.state).isEqualTo(AuthAction.AuthMethod.AuthResult.State.DENIED);
    }

    @Test
    public void authenticate_denied_whenUserInactive() {
        String body = randomBody31();
        String token = makeTokenWithChecksum(body);
        String hash = HashUtils.getHash(token, HashUtils.SHA_256);

        User u = new User();
        u.setActive(false);
        ApiToken t = new ApiToken();
        t.setActive(true);
        t.setUser(u);
        when(apiTokenDao.findByHash(hash)).thenReturn(Optional.of(t));

        Http.Request req = requestWithAuth(token);
        AuthAction.AuthMethod.AuthResult res = authApiToken.authenticate(req, User.Role.USER);

        assertThat(res.state).isEqualTo(AuthAction.AuthMethod.AuthResult.State.DENIED);
        // User should still be placed in RequestScope before check
        assertThat(RequestScope.get(AuthService.SIGNEDIN_USER)).isSameAs(u);
    }

    @Test
    public void authenticate_denied_whenUserLacksRole() {
        String body = randomBody31();
        String token = makeTokenWithChecksum(body);
        String hash = HashUtils.getHash(token, HashUtils.SHA_256);

        User u = new User();
        u.setActive(true);
        // user.hasRole will return false if no Role is added
        ApiToken t = new ApiToken();
        t.setActive(true);
        t.setUser(u);
        when(apiTokenDao.findByHash(hash)).thenReturn(Optional.of(t));

        Http.Request req = requestWithAuth(token);
        AuthAction.AuthMethod.AuthResult res = authApiToken.authenticate(req, User.Role.ADMIN);

        assertThat(res.state).isEqualTo(AuthAction.AuthMethod.AuthResult.State.DENIED);
    }

    @Test
    public void authenticate_denied_whenTokenExpired() {
        String body = randomBody31();
        String token = makeTokenWithChecksum(body);
        String hash = HashUtils.getHash(token, HashUtils.SHA_256);

        User u = new User();
        u.setActive(true);
        u.addRole(User.Role.USER);
        ApiToken t = new ApiToken();
        t.setActive(true);
        t.setUser(u);
        t.setCreationDate(Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        t.setExpires(60); // Expires in 1 minute
        when(apiTokenDao.findByHash(hash)).thenReturn(Optional.of(t));

        Http.Request req = requestWithAuth(token);
        AuthAction.AuthMethod.AuthResult res = authApiToken.authenticate(req, User.Role.USER);

        assertThat(res.state).isEqualTo(AuthAction.AuthMethod.AuthResult.State.DENIED);
    }

    @Test
    public void authenticate_success_whenAllChecksPass() {
        String body = randomBody31();
        String token = makeTokenWithChecksum(body);
        String hash = HashUtils.getHash(token, HashUtils.SHA_256);

        User u = new User();
        u.setActive(true);
        u.addRole(User.Role.USER);
        ApiToken t = new ApiToken();
        t.setActive(true);
        t.setUser(u);
        // no expires => never expires
        when(apiTokenDao.findByHash(hash)).thenReturn(Optional.of(t));

        Http.Request req = requestWithAuth(token);
        AuthAction.AuthMethod.AuthResult res = authApiToken.authenticate(req, User.Role.USER);

        assertThat(res.state).isEqualTo(AuthAction.AuthMethod.AuthResult.State.AUTHENTICATED);
        assertThat(RequestScope.get(AuthApiToken.API_TOKEN)).isSameAs(t);
        assertThat(RequestScope.get(AuthService.SIGNEDIN_USER)).isSameAs(u);
    }
}
