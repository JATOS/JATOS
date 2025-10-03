package services.gui;

import daos.common.ApiTokenDao;
import models.common.ApiToken;
import models.common.User;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import utils.common.HashUtils;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiTokenService.
 */
public class ApiTokenServiceTest {

    private MockedStatic<HashUtils> hashUtilsMock;

    @After
    public void tearDown() {
        if (hashUtilsMock != null) hashUtilsMock.close();
    }

    @Test
    public void create_persistsApiToken_andReturnsFormattedToken() {
        // Given
        ApiTokenDao apiTokenDao = Mockito.mock(ApiTokenDao.class);
        ApiTokenService service = new ApiTokenService(apiTokenDao);

        User user = new User("john", "John Doe", "john@example.org");
        String name = "My Token";
        Integer expires = 3600; // 1 hour

        String random31 = "1234567890123456789012345678901"; // 31 chars
        String checksum6 = "abcdef"; // 6 chars
        String expectedToken = "jap_" + random31 + checksum6;
        String expectedHash = "deadbeefcafebabe";

        hashUtilsMock = Mockito.mockStatic(HashUtils.class);
        hashUtilsMock.when(() -> HashUtils.generateSecureRandomString(31)).thenReturn(random31);
        hashUtilsMock.when(() -> HashUtils.getChecksum(random31)).thenReturn(checksum6);
        hashUtilsMock.when(() -> HashUtils.getHash(expectedToken, HashUtils.SHA_256)).thenReturn(expectedHash);

        // When
        String token = service.create(user, name, expires);

        // Then - returned token string
        assertThat(token).isEqualTo(expectedToken);
        assertThat(token).startsWith("jap_");
        assertThat(token.length()).isEqualTo(41); // 4 + 31 + 6
        assertThat(token.endsWith(checksum6)).isTrue();

        // Then - persisted ApiToken
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        verify(apiTokenDao, times(1)).create(captor.capture());
        ApiToken persisted = captor.getValue();
        assertThat(persisted.getTokenHash()).isEqualTo(expectedHash);
        assertThat(persisted.getName()).isEqualTo(name);
        assertThat(persisted.getExpires()).isEqualTo(expires);
        assertThat(persisted.getUser()).isEqualTo(user);
        assertThat(persisted.getCreationDate()).isNotNull();

        // Verify hashing was called with expected inputs
        hashUtilsMock.verify(() -> HashUtils.getHash(expectedToken, HashUtils.SHA_256));
    }

    @Test
    public void create_withNullExpires_persistsWithoutExpiry() {
        // Given
        ApiTokenDao apiTokenDao = Mockito.mock(ApiTokenDao.class);
        ApiTokenService service = new ApiTokenService(apiTokenDao);

        User user = new User("alice", "Alice", "alice@example.org");
        String name = "No Exp Token";
        Integer expires = null; // no expiry

        String random31 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcde".substring(0, 31);
        String checksum6 = "123456";
        String expectedToken = "jap_" + random31 + checksum6;
        String expectedHash = "beadfeedface";

        hashUtilsMock = Mockito.mockStatic(HashUtils.class);
        hashUtilsMock.when(() -> HashUtils.generateSecureRandomString(31)).thenReturn(random31);
        hashUtilsMock.when(() -> HashUtils.getChecksum(random31)).thenReturn(checksum6);
        hashUtilsMock.when(() -> HashUtils.getHash(expectedToken, HashUtils.SHA_256)).thenReturn(expectedHash);

        // When
        String token = service.create(user, name, expires);

        // Then
        assertThat(token).isEqualTo(expectedToken);
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        verify(apiTokenDao).create(captor.capture());
        ApiToken persisted = captor.getValue();
        assertThat(persisted.getExpires()).isNull();
        assertThat(persisted.getUser()).isEqualTo(user);
        assertThat(persisted.getTokenHash()).isEqualTo(expectedHash);
    }
}
