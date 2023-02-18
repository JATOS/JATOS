package auth.gui;

import daos.common.ApiTokenDao;
import general.common.Common;
import general.common.RequestScope;
import models.common.ApiToken;
import models.common.User;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import utils.common.HashUtils;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static play.mvc.Results.forbidden;
import static play.mvc.Results.unauthorized;

/**
 * Authentication via personal access tokens (API tokens) that can be used with JATOS API. API tokens are associated
 * with a user and have the same access rights as the user. For a successful authentication the token must be put in the
 * 'Authorization' header with a 'Bearer' prefix. JATOS' API token have a prefex 'jap_'. The {@link User} and the
 * {@link ApiToken} objects are put in the {@link RequestScope} for later use during request processing.
 */
@SuppressWarnings("deprecation")
@Singleton
public class AuthActionApiToken implements AuthAction.IAuth {

    public static final String API_TOKEN = "apiToken";

    private final ApiTokenDao apiTokenDao;

    private final JPAApi jpa;

    @Inject
    AuthActionApiToken(ApiTokenDao apiTokenDao, JPAApi jpa) {
        this.apiTokenDao = apiTokenDao;
        this.jpa = jpa;
    }

    public AuthResult authenticate(Http.Request request, User.Role role) {

        if (!Helpers.isApiRequest(request)) {
            return AuthResult.of(false);
        }

        if (!Common.isJatosApiAllowed()) {
            return AuthResult.of(forbidden("JATOS' current settings do not allow API usage"));
        }

        // Check token checksum
        //noinspection OptionalGetWithoutIsPresent - it's checked in Helpers.isApiRequest
        String headerValue = request.header("Authorization").get();
        String fullTokenStr = headerValue.replace("Bearer", "").trim();
        String cleanedToken = fullTokenStr.replace("jap_", "").substring(0, 31);
        String calculatedChecksum = HashUtils.getChecksum(cleanedToken);
        String givenChecksum = fullTokenStr.substring(fullTokenStr.length() - 6);
        if (!givenChecksum.equals(calculatedChecksum)) {
            return AuthResult.of(unauthorized("Invalid api token"));
        }

        // Search token by its hash in the database
        String tokenHash = HashUtils.getHash(fullTokenStr, HashUtils.SHA_256);
        Optional<ApiToken> apiTokenOptional = jpa.withTransaction(() -> apiTokenDao.findByHash(tokenHash));
        if (!apiTokenOptional.isPresent()) {
            return AuthResult.of(unauthorized("Invalid api token"));
        }
        ApiToken apiToken = apiTokenOptional.get();

        // Tokens can be deactivated
        if (!apiToken.isActive()) {
            return AuthResult.of(unauthorized("Invalid api token"));
        }

        // Check the token's user: since these are personal access tokens and the user which belongs to the token can
        // be deactivated too
        User user = apiToken.getUser();
        RequestScope.put(AuthService.LOGGED_IN_USER, user);
        if (!user.isActive()) {
            return AuthResult.of(unauthorized("Invalid api token"));
        }

        // Check authorization
        if (!user.hasRole(role)) {
            return AuthResult.of(unauthorized("Invalid api token"));
        }

        // Check if token is expired
        if (apiToken.isExpired()) {
            return AuthResult.of(unauthorized("Invalid api token"));
        }

        RequestScope.put(API_TOKEN, apiToken);
        return AuthResult.of(true);
    }

}
