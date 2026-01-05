package auth.gui;

import daos.common.ApiTokenDao;
import general.common.Common;
import general.common.Http.Context;
import models.common.ApiToken;
import models.common.User;
import play.libs.typedmap.TypedKey;
import play.mvc.Http;
import utils.common.HashUtils;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static play.mvc.Results.forbidden;
import static play.mvc.Results.unauthorized;

/**
 * Authentication via personal access tokens (API tokens) that can be used with JATOS API. API tokens are associated
 * with a user and have the same access rights as the user. For a successful authentication, the token must be put in
 * the 'Authorization' header with a 'Bearer' prefix. JATOS' API token has a prefex 'jap_'. The {@link User} and the
 * {@link ApiToken} objects are put in the {@link Context} for later use during request processing.
 */
@Singleton
public class AuthApiToken implements AuthAction.AuthMethod {

    public static final TypedKey<ApiToken> API_TOKEN = TypedKey.create("apiToken");

    private final ApiTokenDao apiTokenDao;


    @Inject
    AuthApiToken(ApiTokenDao apiTokenDao) {
        this.apiTokenDao = apiTokenDao;
    }

    @Override
    public AuthResult authenticate(Http.Request request, User.Role role) {

        if (!Helpers.isApiRequest(request)) {
            return AuthResult.wrongMethod(request);
        }

        if (!Common.isJatosApiAllowed()) {
            return AuthResult.denied(request, forbidden("JATOS' current settings do not allow API usage"));
        }

        // Check token checksum
        //noinspection OptionalGetWithoutIsPresent - it's checked in Helpers.isApiRequest
        String headerValue = request.header("Authorization").get();
        String fullTokenStr = headerValue.replace("Bearer", "").trim();
        String cleanedToken = fullTokenStr.replace("jap_", "").substring(0, 31);
        String calculatedChecksum = HashUtils.getChecksum(cleanedToken);
        String givenChecksum = fullTokenStr.substring(fullTokenStr.length() - 6);
        if (!givenChecksum.equals(calculatedChecksum)) {
            return AuthResult.denied(request, unauthorized("Invalid api token"));
        }

        // Search token by its hash in the database
        String tokenHash = HashUtils.getHash(fullTokenStr, HashUtils.SHA_256);
        Optional<ApiToken> apiTokenOptional = apiTokenDao.findByHash(tokenHash);
        if (!apiTokenOptional.isPresent()) {
            return AuthResult.denied(request, unauthorized("Invalid api token"));
        }
        ApiToken apiToken = apiTokenOptional.get();

        // Tokens can be deactivated
        if (!apiToken.isActive()) {
            return AuthResult.denied(request, unauthorized("Invalid api token"));
        }

        // Check the token's user: since these are personal access tokens and the user which belongs to the token can
        // be deactivated too
        User user = apiToken.getUser();
        Context.current().args().put(SIGNEDIN_USER, user);

        if (!user.isActive()) {
            return AuthResult.denied(request, unauthorized("Invalid api token"));
        }

        // Check authorization
        if (!user.hasRole(role)) {
            return AuthResult.denied(request, unauthorized("Invalid api token"));
        }

        // Check if the token is expired
        if (apiToken.isExpired()) {
            return AuthResult.denied(request, unauthorized("Invalid api token"));
        }

        Context.current().args().put(API_TOKEN, apiToken);
        return AuthResult.authenticated(request);
    }

}
