package auth.gui;

import daos.common.ApiTokenDao;
import general.common.Common;
import http.common.Http.Context;
import http.common.HttpUtils;
import models.common.ApiToken;
import models.common.User;
import models.common.User.Role;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.mvc.Http;
import services.gui.ApiTokenService;
import utils.common.HashUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static auth.gui.AuthAction.AuthMethod.AuthResult.State.DENIED;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static auth.gui.AuthApiToken.API_TOKEN;
import static auth.gui.AuthApiToken.AuthResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthApiToken.
 */
public class AuthApiTokenTest {

    private ApiTokenDao apiTokenDao;
    private AuthApiToken authApiToken;

    private MockedStatic<HttpUtils> httpUtilsMock;
    private MockedStatic<Common> commonMock;

    @Before
    public void setUp() {
        apiTokenDao = mock(ApiTokenDao.class);
        ApiTokenService apiTokenService = new ApiTokenService(apiTokenDao);
        authApiToken = new AuthApiToken(apiTokenService, apiTokenDao);

        httpUtilsMock = Mockito.mockStatic(HttpUtils.class);
        commonMock = Mockito.mockStatic(Common.class);
        // Default: API request, Bearer token, and JATOS API allowed
        httpUtilsMock.when(HttpUtils::isApiRequest).thenReturn(true);
        httpUtilsMock.when(HttpUtils::hasBearerToken).thenReturn(true);
        //noinspection ResultOfMethodCallIgnored
        commonMock.when(Common::isJatosApiAllowed).thenReturn(true);
    }

    @After
    public void tearDown() {
        if (httpUtilsMock != null) httpUtilsMock.close();
        if (commonMock != null) commonMock.close();
    }

    private static String makeTokenWithChecksum(String body31) {
        String checksum = HashUtils.getChecksum(body31, ApiToken.TOKEN_CHECKSUM_LENGTH);
        return "jap_" + body31 + checksum;
    }

    private static void setContextRequestWithAuth(String fullToken) {
        Http.Request request = new Http.RequestBuilder()
                .header("Authorization", "Bearer " + fullToken)
                .build();
        Context context = new Context(request);
        Context.setCurrent(context);
    }

    private static String randomBody31() {
        // Create a deterministic 31-char token body
        String base = UUID.randomUUID().toString().replace("-", "") + "abcdefghijklmnopqrstuvwxyz";
        return base.substring(0, 31);
    }

    @Test
    public void authenticate_wrongMethod_whenNotApiRequest() {
        httpUtilsMock.when(HttpUtils::isApiRequest).thenReturn(false);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(AuthResult.State.WRONG_METHOD);
    }

    @Test
    public void authenticate_wrongMethod_whenNotHasBearerToken() {
        httpUtilsMock.when(HttpUtils::hasBearerToken).thenReturn(false);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(AuthResult.State.WRONG_METHOD);
    }

    @Test
    public void authenticate_denied_whenApiUsageDisabled() {
        //noinspection ResultOfMethodCallIgnored
        commonMock.when(Common::isJatosApiAllowed).thenReturn(false);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));
        assertThat(res.state).isEqualTo(DENIED);
    }

    @Test
    public void authenticate_denied_wrongChecksumLength() {
        String body = randomBody31();
        String token = "jap_" + body + "abc"; // wrong checksum length
        setContextRequestWithAuth(token);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));
        assertThat(res.state).isEqualTo(DENIED);
        verifyNoInteractions(apiTokenDao);
    }

    @Test
    public void authenticate_denied_wrongTokenBodyLength() {
        String body = "abcd1234"; // wrong body length
        String token = makeTokenWithChecksum(body);
        setContextRequestWithAuth(token);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));
        assertThat(res.state).isEqualTo(DENIED);
        verifyNoInteractions(apiTokenDao);
    }

    @Test
    public void authenticate_denied_onBadChecksum() {
        String body = randomBody31();
        String token = "jap_" + body + "abcdef"; // bad checksum
        setContextRequestWithAuth(token);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));
        assertThat(res.state).isEqualTo(DENIED);
        verifyNoInteractions(apiTokenDao);
    }

    @Test
    public void authenticate_denied_whenTokenNotFound() {
        String body = randomBody31();
        String token = makeTokenWithChecksum(body);
        String hash = HashUtils.getHash(token, HashUtils.SHA_256);
        when(apiTokenDao.findByHash(hash)).thenReturn(Optional.empty());

        setContextRequestWithAuth(token);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(DENIED);
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

        setContextRequestWithAuth(token);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(DENIED);
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

        setContextRequestWithAuth(token);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(DENIED);
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

        setContextRequestWithAuth(token);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.ADMIN));

        assertThat(res.state).isEqualTo(DENIED);
    }

    @Test
    public void authenticate_denied_whenTokenExpired() {
        String body = randomBody31();
        String token = makeTokenWithChecksum(body);
        String hash = HashUtils.getHash(token, HashUtils.SHA_256);

        User u = new User();
        u.setActive(true);
        u.updateRoles(Role.USER);
        ApiToken t = new ApiToken();
        t.setActive(true);
        t.setUser(u);
        t.setCreationDate(Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        t.setExpires(60); // Expires in 1 minute
        when(apiTokenDao.findByHash(hash)).thenReturn(Optional.of(t));

        setContextRequestWithAuth(token);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(DENIED);
    }

    @Test
    public void authenticate_success_whenAllChecksPass() {
        String body = randomBody31();
        String token = makeTokenWithChecksum(body);
        String hash = HashUtils.getHash(token, HashUtils.SHA_256);

        User u = new User();
        u.setActive(true);
        u.updateRoles(Role.USER);
        ApiToken t = new ApiToken();
        t.setActive(true);
        t.setUser(u);
        // no expires => never expires
        when(apiTokenDao.findByHash(hash)).thenReturn(Optional.of(t));

        setContextRequestWithAuth(token);

        AuthResult res = authApiToken.authenticate(EnumSet.of(Role.USER));

        assertThat(res.state).isEqualTo(AuthResult.State.AUTHENTICATED);
        assertThat(Context.current().args().get(API_TOKEN)).isSameAs(t);
        assertThat(Context.current().args().get(SIGNEDIN_USER)).isSameAs(u);
    }
}
