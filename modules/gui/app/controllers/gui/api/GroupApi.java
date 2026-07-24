package controllers.gui.api;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.GroupResultDao;
import exceptions.common.ForbiddenException;
import general.common.ApiEnvelope;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import models.common.Batch;
import models.common.GroupResult;
import models.common.User;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.BodyParser.Raw;
import play.mvc.Controller;
import play.mvc.Result;
import scala.Option;
import services.gui.ApiService;
import services.gui.AuthorizationService;
import services.gui.BatchService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static actions.common.TransactionalAction.Mode.READ_ONLY;
import static auth.gui.AuthAction.Auth;
import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;

@Singleton
public class GroupApi extends Controller {

    private final BatchService batchService;
    private final ApiService apiService;
    private final AuthorizationService authorizationService;
    private final GroupResultDao groupResultDao;
    private final DomainJsonMapper domainJsonMapper;

    @Inject
    GroupApi(BatchService batchService,
             ApiService apiService,
             AuthorizationService authorizationService,
             GroupResultDao groupResultDao,
             DomainJsonMapper domainJsonMapper) {
        this.batchService = batchService;
        this.apiService = apiService;
        this.authorizationService = authorizationService;
        this.groupResultDao = groupResultDao;
        this.domainJsonMapper = domainJsonMapper;
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result getGroupsOfBatch(String id) {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessBatch(batch, signedinUser);

        List<GroupResult> groups = groupResultDao.findAllByBatch(batch);

        JsonNode groupArray = domainJsonMapper.allGroupResults(groups);
        return ok(ApiEnvelope.wrap(groupArray).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    @TransactionalAction.Transactional(READ_ONLY)
    public Result getGroupSession(Long id, boolean asText) {
        GroupResult groupResult = groupResultDao.findById(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessGroupResult(groupResult, signedinUser);

        ObjectNode session = apiService.getSessionNode(groupResult.getGroupSessionData(), groupResult.getGroupSessionVersion(), asText);
        return ok(ApiEnvelope.wrap(session).asJsonNode());
    }

    /**
     * Updates the group session. Uses `BodyParser.Raw` to handle JSON payloads with potential malformed content
     * gracefully and give detailed error messages to the user.
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @TransactionalAction.Transactional
    @BodyParser.Of(Raw.class)
    public Result updateGroupSession(play.mvc.Http.Request request, Long id, Option<Long> version) {
        GroupResult groupResult = groupResultDao.findById(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessGroupResult(groupResult, signedinUser);

        String sessionData = apiService.getSessionDataFromBody(request);

        Long currentVersion = version.getOrElse(groupResult::getGroupSessionVersion);
        Long newVersion = groupResultDao.updateGroupSession(groupResult.getId(), currentVersion, sessionData);
        if (newVersion == null) {
            throw new ForbiddenException("Group session version conflict");
        }

        ObjectNode data = Json.newObject().put("version", newVersion);
        return ok(ApiEnvelope.wrap("Group session updated successfully", data).asJsonNode());
    }

}
