package services.gui;

import daos.common.ApiTokenDao;
import models.common.ApiToken;
import models.common.User;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import utils.common.HashUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

import static models.common.ApiToken.*;

/**
 * Service class for API tokens.
 */
@Singleton
public class ApiTokenService {

    public final static String TOKEN_REGEX = "^" + TOKEN_PREFIX
            + "[A-Za-z0-9]{" + TOKEN_RANDOM_LENGTH + "}[A-Za-z0-9]{" + TOKEN_CHECKSUM_LENGTH + "}$";

    private final ApiTokenDao apiTokenDao;

    @Inject
    ApiTokenService(ApiTokenDao apiTokenDao) {
        this.apiTokenDao = apiTokenDao;
    }

    /**
     * Generates a new api token and persists its hash in the database. Returns the token and the token string. A token
     * consists of a prefix, a random string, and a checksum.
     */
    public Pair<ApiToken, String> create(User user, String name, Integer expires) {
        String randomStr = HashUtils.generateSecureRandomString(TOKEN_RANDOM_LENGTH);
        String checksum = HashUtils.getChecksum(randomStr, TOKEN_CHECKSUM_LENGTH);
        String apiTokenStr = TOKEN_PREFIX + randomStr + checksum;
        String tokenHash = HashUtils.getHash(apiTokenStr, HashUtils.SHA_256);
        ApiToken at = new ApiToken(tokenHash, name, expires, user);
        apiTokenDao.persist(at);
        return new ImmutablePair<>(at, apiTokenStr);
    }

    /**
     * Validates a token string to ensure it matches the expected structure and that its checksum is correct.
     *
     * @param tokenStr the token string to be validated. The token must match the predefined format and include
     *                 a valid checksum.
     * @return {@code true} if the token is valid and its checksum is correct;
     *         {@code false} otherwise.
     */
    public boolean isValid(String tokenStr) {
        if (!tokenStr.matches(TOKEN_REGEX)) {
            return false;
        }
        String cleanedToken = tokenStr.replace(TOKEN_PREFIX, "").substring(0, TOKEN_RANDOM_LENGTH);
        String calculatedChecksum = HashUtils.getChecksum(cleanedToken, TOKEN_CHECKSUM_LENGTH);
        String givenChecksum = tokenStr.substring(tokenStr.length() - TOKEN_CHECKSUM_LENGTH);
        return givenChecksum.equals(calculatedChecksum);
    }

}
