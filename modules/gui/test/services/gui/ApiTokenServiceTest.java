package services.gui;

import daos.common.ApiTokenDao;
import models.common.ApiToken;
import models.common.User;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import utils.common.HashUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
        hashUtilsMock.when(() -> HashUtils.getChecksum(random31, 6)).thenReturn(checksum6);
        hashUtilsMock.when(() -> HashUtils.getHash(expectedToken, HashUtils.SHA_256)).thenReturn(expectedHash);

        // When
        Pair<ApiToken, String> token = service.create(user, name, expires);
        ApiToken persistedToken = token.getLeft();
        String persistedTokenStr = token.getRight();

        // Then - returned token string
        assertThat(persistedTokenStr).isEqualTo(expectedToken);
        assertThat(persistedTokenStr).startsWith("jap_");
        assertThat(persistedTokenStr.length()).isEqualTo(41); // 4 + 31 + 6
        assertThat(persistedTokenStr.endsWith(checksum6)).isTrue();

        // Then - persisted ApiToken
        verify(apiTokenDao, times(1)).persist(persistedToken);
        assertThat(persistedToken.getTokenHash()).isEqualTo(expectedHash);
        assertThat(persistedToken.getName()).isEqualTo(name);
        assertThat(persistedToken.getExpires()).isEqualTo(expires);
        assertThat(persistedToken.getUser()).isEqualTo(user);
        assertThat(persistedToken.getCreationDate()).isNotNull();

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
        hashUtilsMock.when(() -> HashUtils.getChecksum(random31, 6)).thenReturn(checksum6);
        hashUtilsMock.when(() -> HashUtils.getHash(expectedToken, HashUtils.SHA_256)).thenReturn(expectedHash);

        // When
        Pair<ApiToken, String> token = service.create(user, name, expires);
        ApiToken persistedToken = token.getLeft();
        String persistedTokenStr = token.getRight();

        // Then
        assertThat(persistedTokenStr).isEqualTo(expectedToken);
        verify(apiTokenDao, times(1)).persist(persistedToken);
        assertThat(persistedToken.getTokenHash()).isEqualTo(expectedHash);
        assertThat(persistedToken.getName()).isEqualTo(name);
        assertThat(persistedToken.getExpires()).isEqualTo(expires);
        assertThat(persistedToken.getUser()).isEqualTo(user);
        assertThat(persistedToken.getCreationDate()).isNotNull();
    }

    @Test
    public void isValid_returnsTrueOnlyForWellFormattedTokenWithValidChecksum() {
        // Given
        ApiTokenDao apiTokenDao = Mockito.mock(ApiTokenDao.class);
        ApiTokenService service = new ApiTokenService(apiTokenDao);

        String random31 = "1234567890123456789012345678901";
        String checksum6 = "ABC123";
        String validToken = "jap_" + random31 + checksum6;

        hashUtilsMock = Mockito.mockStatic(HashUtils.class);
        hashUtilsMock.when(() -> HashUtils.getChecksum(random31, 6)).thenReturn(checksum6);

        // Then
        assertThat(service.isValid(validToken)).isTrue();
        assertThat(service.isValid("jap_" + random31 + "BAD999")).isFalse();
        assertThat(service.isValid("wrong_" + random31 + checksum6)).isFalse();
        assertThat(service.isValid("jap_" + random31.substring(1) + checksum6)).isFalse();
        assertThat(service.isValid("jap_" + random31 + "ABC12!")).isFalse();

        hashUtilsMock.verify(() -> HashUtils.getChecksum(random31, 6), Mockito.times(2));
    }
}
