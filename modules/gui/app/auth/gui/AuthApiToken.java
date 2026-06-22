package auth.gui;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.ApiTokenDao;
import general.common.ApiEnvelope;
import general.common.Common;
import http.common.Http.Context;
import http.common.HttpUtils;
import models.common.ApiToken;
import models.common.User;
import models.common.User.Role;
import play.libs.typedmap.TypedKey;
import services.gui.ApiTokenService;
import utils.common.HashUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.Optional;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static general.common.ApiEnvelope.ErrorCode.AUTH_ERROR;
import static general.common.ApiEnvelope.ErrorCode.INVALID_API_TOKEN;
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

    private final ApiTokenService apiTokenService;
    private final ApiTokenDao apiTokenDao;

    @Inject
    AuthApiToken(ApiTokenService apiTokenService,
                 ApiTokenDao apiTokenDao) {
        this.apiTokenService = apiTokenService;
        this.apiTokenDao = apiTokenDao;
    }

    @Override
    public Type type() {
        return Type.TOKEN;
    }

    @Override
    public AuthResult authenticate(EnumSet<Role> allowedRoles) {

        if (!HttpUtils.isApiRequest()) {
            return AuthResult.wrongMethod();
        }

        if (!Common.isJatosApiAllowed()) {
            JsonNode error = ApiEnvelope.wrap("JATOS API is not allowed", AUTH_ERROR).asJsonNode();
            return AuthResult.denied(forbidden(error));
        }

        //noinspection OptionalGetWithoutIsPresent - it's checked in Helpers.isApiRequest
        String authorizationHeader = Context.current().requestHeader().header("Authorization").get();
        String fullTokenStr = authorizationHeader.substring("Bearer ".length()).trim();
        apiTokenService.isValid(fullTokenStr);
        if (!apiTokenService.isValid(fullTokenStr)) {
            JsonNode error = ApiEnvelope.wrap("Invalid api token", INVALID_API_TOKEN).asJsonNode();
            return AuthResult.denied(unauthorized(error));
        }

        // Search token by its hash in the database
        String tokenHash = HashUtils.getHash(fullTokenStr, HashUtils.SHA_256);
        Optional<ApiToken> apiTokenOptional = apiTokenDao.findByHash(tokenHash);
        if (apiTokenOptional.isEmpty()) {
            JsonNode error = ApiEnvelope.wrap("Invalid api token", INVALID_API_TOKEN).asJsonNode();
            return AuthResult.denied(unauthorized(error));
        }
        ApiToken apiToken = apiTokenOptional.get();

        // Tokens can be deactivated
        if (!apiToken.isActive()) {
            JsonNode error = ApiEnvelope.wrap("Invalid api token", INVALID_API_TOKEN).asJsonNode();
            return AuthResult.denied(unauthorized(error));
        }

        // Check the token's user: since these are personal access tokens and the user who belongs to the token can
        // be deactivated too
        User user = apiToken.getUser();

        if (!user.isActive()) {
            JsonNode error = ApiEnvelope.wrap("Invalid api token", INVALID_API_TOKEN).asJsonNode();
            return AuthResult.denied(unauthorized(error));
        }

        // Check authorization
        if (!user.hasRole(allowedRoles)) {
            JsonNode error = ApiEnvelope.wrap("Invalid api token", INVALID_API_TOKEN).asJsonNode();
            return AuthResult.denied(unauthorized(error));
        }

        // Check if the token is expired
        if (apiToken.isExpired()) {
            JsonNode error = ApiEnvelope.wrap("Invalid api token", INVALID_API_TOKEN).asJsonNode();
            return AuthResult.denied(unauthorized(error));
        }

        Context.current().args().put(SIGNEDIN_USER, user);
        Context.current().args().put(API_TOKEN, apiToken);
        return AuthResult.authenticated();
    }

}
