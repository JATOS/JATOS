package services.gui;

import daos.common.ApiTokenDao;
import models.common.ApiToken;
import models.common.User;
import utils.common.HashUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Kristian Lange
 */
@Singleton
public class ApiTokenService {

    private final ApiTokenDao apiTokenDao;

    @Inject
    ApiTokenService(ApiTokenDao apiTokenDao) {
        this.apiTokenDao = apiTokenDao;
    }

    /**
     * Generates a new api token and persists it in the database. Returns the hash of the token.
     */
    public String create(User user, String name, Integer expires) {
        String randomStr = HashUtils.generateSecureRandomString(31);
        String checksum = HashUtils.getChecksum(randomStr);
        String apiTokenStr = "jap_" + randomStr + checksum;
        String tokenHash = HashUtils.getHash(apiTokenStr, HashUtils.SHA_256);
        ApiToken at = new ApiToken(tokenHash, name, expires, user);
        apiTokenDao.create(at);
        return apiTokenStr;
    }

}
