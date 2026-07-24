package controllers.gui.api;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction.Transactional;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.ApiTokenDao;
import daos.common.UserDao;
import exceptions.common.ForbiddenException;
import general.common.ApiEnvelope;
import general.common.Common;
import http.common.Http.Context;
import json.common.DefaultJson;
import models.common.ApiToken;
import models.common.User;
import org.apache.commons.lang3.tuple.Pair;
import play.mvc.BodyParser;
import play.mvc.BodyParser.Json;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.ApiService;
import services.gui.ApiTokenService;
import services.gui.AuthorizationService;

import javax.inject.Inject;
import javax.inject.Singleton;

import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static auth.gui.AuthApiToken.API_TOKEN;
import static general.common.ApiEnvelope.ErrorCode.CONFIG_ERROR;
import static models.common.User.Role.*;

@Singleton
public class ApiTokenApi extends Controller {

    private final AuthorizationService authorizationService;
    private final ApiService apiService;
    private final ApiTokenService apiTokenService;
    private final UserDao userDao;
    private final ApiTokenDao apiTokenDao;
    private final DefaultJson defaultJson;

    @Inject
    ApiTokenApi(AuthorizationService authorizationService,
                ApiService apiService,
                ApiTokenService apiTokenService,
                UserDao userDao,
                ApiTokenDao apiTokenDao,
                DefaultJson defaultJson) {
        this.authorizationService = authorizationService;
        this.apiService = apiService;
        this.apiTokenService = apiTokenService;
        this.userDao = userDao;
        this.apiTokenDao = apiTokenDao;
        this.defaultJson = defaultJson;
    }

    /**
     * Returns metadata of the API token used in this request
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = TOKEN)
    public Result currentApiTokenMetadata() {
        Object token = Context.current().args().get(API_TOKEN);
        return ok(ApiEnvelope.wrap(token).asJsonNode());
    }


    /**
     * Generate API tokens. It returns the token and the token metadata.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    @BodyParser.Of(Json.class)
    public Result generateApiToken(play.mvc.Http.Request request, Long userId) {
        if (!Common.isJatosApiTokensApiGenerationAllowed()) {
            throw new ForbiddenException("API token generation is not allowed", CONFIG_ERROR);
        }

        User user = userDao.findById(userId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkSignedinUserAllowedToAccessUser(user, signedinUser);

        JsonNode json = request.body().asJson();
        String name = apiService.getFieldFromJson(json, "name", String.class);

        int expires = (int) Common.getJatosApiTokensApiGenerationExpiresAfter().getSeconds();

        Pair<ApiToken, String> apiTokenPair = apiTokenService.create(user, name, expires);
        ApiToken apiToken = apiTokenPair.getLeft();
        String apiTokenStr = apiTokenPair.getRight();

        ObjectNode tokenJson = defaultJson.objAsObjectNode(apiToken);
        tokenJson.put("token", apiTokenStr);
        return created(ApiEnvelope.wrap(tokenJson).asJsonNode());
    }

    /**
     * List the metadata of all tokens that belong to a user.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    public Result allApiTokenMetadataByUser(Long userId) {
        User user = userDao.findById(userId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkAdminOrSelf(signedinUser, user);

        ArrayNode tokens = defaultJson.mapper().createArrayNode();
        apiTokenDao.findByUser(user).forEach(token -> tokens.add(defaultJson.objAsJsonNode(token)));
        return ok(ApiEnvelope.wrap(tokens).asJsonNode());
    }

    /**
     * Get the metadata of an API token specified by its ID.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    public Result apiTokenMetadata(Long id) {
        ApiToken apiToken = apiTokenDao.find(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkAdminOrSelf(signedinUser, apiToken);

        return ok(ApiEnvelope.wrap(apiToken).asJsonNode());
    }

    /**
     * Activate or deactivate a token specified by its ID. Admins can update tokens of non-admin users. Users (including
     * admins) can update their own tokens.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    @BodyParser.Of(Json.class)
    public Result toggleApiTokenActive(play.mvc.Http.Request request, Long id) {
        ApiToken token = apiTokenDao.find(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkUserAllowedToAccessApiToken(token, signedinUser);

        JsonNode json = request.body().asJson();
        boolean active = apiService.getActiveFlagFromJson(json);
        token.setActive(active);
        apiTokenDao.merge(token);

        ObjectNode responseJson = defaultJson.mapper().createObjectNode();
        responseJson.put("id", token.getId());
        responseJson.put("active", token.isActive());
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

    /**
     * Admins can delete tokens of non-admin users. Users (including admins) can delete their own tokens.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    @Transactional
    public Result deleteApiToken(Long id) {
        ApiToken token = apiTokenDao.find(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkUserAllowedToAccessApiToken(token, signedinUser);

        apiTokenDao.remove(token);

        return ok(ApiEnvelope.wrap("Token deleted successfully").asJsonNode());
    }

}
