package controllers.gui.api;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction.Transactional;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.UserDao;
import exceptions.common.BadRequestException;
import general.common.ApiEnvelope;
import http.common.Http.Context;
import json.common.DefaultJson;
import json.common.StrictJson;
import models.common.User;
import models.gui.NewUserProperties;
import models.gui.UserProperties;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.ApiService;
import services.gui.AuthorizationService;
import services.gui.UserService;
import services.gui.importexport.GitCredentialsService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.User.Role.*;
import static play.mvc.Http.Request;

@Singleton
public class UserApi extends Controller {

    private final AuthorizationService authorizationService;
    private final ApiService apiService;
    private final UserService userService;
    private final GitCredentialsService gitCredentialsService;
    private final UserDao userDao;
    private final DefaultJson defaultJson;
    private final StrictJson strictJson;

    @Inject
    public UserApi(AuthorizationService authorizationService,
                   ApiService apiService,
                   UserService userService,
                   GitCredentialsService gitCredentialsService,
                   UserDao userDao,
                   DefaultJson defaultJson,
                   StrictJson strictJson) {
        this.authorizationService = authorizationService;
        this.apiService = apiService;
        this.userService = userService;
        this.gitCredentialsService = gitCredentialsService;
        this.userDao = userDao;
        this.defaultJson = defaultJson;
        this.strictJson = strictJson;
    }

    /**
     * Get information about all users. Only with admin tokens.
     */
    @Async(Executor.IO)
    @Auth(roles = ADMIN, types = {TOKEN, SESSION})
    public Result allUsers() {
        List<User> userList = userDao.findAll();
        Map<String, List<Long>> studyIdsByUsername = userDao.findAllUsersAndTheirStudyIds();

        ArrayNode allUserData = Json.mapper().createArrayNode();
        for (User user : userList) {
            ObjectNode userNode = (ObjectNode) defaultJson.asJsonForApi(user);
            List<Long> studyIds = studyIdsByUsername.getOrDefault(user.getUsername(), Collections.emptyList());
            userNode.putPOJO("studyIds", studyIds);
            allUserData.add(userNode);
        }

        return ok(ApiEnvelope.wrap(allUserData).asJsonNode());
    }

    /**
     * HEAD requests: checks if a user exists
     */
    @Async(Executor.IO)
    @Auth(roles = ADMIN, types = {TOKEN, SESSION})
    public Result checkUserExists(Long id) {
        User user = userDao.findById(id);
        return user != null ? noContent() : notFound();
    }

    /**
     * Get info of a user.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN, SUPERUSER}, types = {TOKEN, SESSION})
    public Result getUser(Long id) {
        User user = userDao.findById(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkAdminOrSelf(signedinUser, user);

        ObjectNode userNode = (ObjectNode) defaultJson.asJsonForApi(user);
        if (user.equals(signedinUser)) {
            userNode.set("gitCredentials", Json.toJson(gitCredentialsService.getUserGitCredentialsConfigStatus()));
        }
        return ok(ApiEnvelope.wrap(userNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = ADMIN, types = {TOKEN, SESSION})
    @BodyParser.Of(ApiJsonBodyParser.class)
    public Result createUser(Request request) {
        JsonNode json = apiService.getJsonFromBody(request);
        NewUserProperties props = strictJson.jsonNodeAsObj(json, NewUserProperties.class);
        apiService.validateProps(props);
        authorizationService.checkAuthMethodIsDbOrLdap(props);

        User user = userService.registerUser(props);
        JsonNode userJson = defaultJson.asJsonForApi(user);
        return created(ApiEnvelope.wrap(userJson).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN, SUPERUSER}, types = {TOKEN, SESSION})
    @BodyParser.Of(ApiJsonBodyParser.class)
    @Transactional
    public Result updateUser(Request request, Long id) {
        JsonNode json = apiService.getJsonFromBody(request);
        if (!json.isObject()) throw new BadRequestException("Request body is not a JSON object");
        if (json.has("gitCredentials") && json.get("gitCredentials").isNull())
            throw new BadRequestException("Use removeCredentials to remove Git credentials.");

        User user = userDao.findById(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkAdminOrSelf(signedinUser, user);
        user = userDao.findByUsernameWithGitCredentialsAndLock(user.getUsername());
        UserProperties props = strictJson.updateFromJson(userService.bindToProperties(user), json);
        apiService.validateProps(props);
        authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, user);
        userService.updateUserSettings(user, props);

        ObjectNode userJson = (ObjectNode) defaultJson.asJsonForApi(user);
        if (user.equals(signedinUser)) {
            userJson.set("gitCredentials", Json.toJson(gitCredentialsService.getUserGitCredentialsConfigStatus()));
        }
        return ok(ApiEnvelope.wrap(userJson).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = ADMIN, types = {TOKEN, SESSION})
    public Result changeUserRole(Request request, Long id) {
        User user = userDao.findById(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkNotUserAdmin(user);
        authorizationService.checkNotYourself(signedinUser, user);

        JsonNode json = request.body().asJson();
        User.Role role = apiService.getFieldFromJson(json, "role", User.Role.class);
        if (!Arrays.asList(VIEWER, USER).contains(role)) {
            throw new BadRequestException("Invalid role: " + role);
        }

        user.updateRoles(role);
        userDao.merge(user);

        JsonNode userNode = defaultJson.asJsonForApi(user);
        return ok(ApiEnvelope.wrap(userNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN, SUPERUSER}, types = {TOKEN, SESSION})
    public Result deleteUser(Long id) {
        User user = userDao.findById(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkAdminOrSelf(signedinUser, user);
        authorizationService.checkNotUserAdmin(user);

        var reassignments = userService.removeUser(user.getId());
        JsonNode data = Json.toJson(Map.of("ownershipReassignments", reassignments));
        return ok(ApiEnvelope.wrap("User deleted successfully", data).asJsonNode());
    }

}
