package controllers.gui.api;

import actions.common.TransactionalAction;
import actions.common.TransactionalAction.Transactional;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.BatchDao;
import exceptions.common.ForbiddenException;
import general.common.ApiEnvelope;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import json.common.StrictJson;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.gui.BatchProperties;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.BodyParser.Raw;
import play.mvc.Controller;
import play.mvc.Result;
import scala.Option;
import services.gui.ApiService;
import services.gui.AuthorizationService;
import services.gui.BatchService;
import services.gui.StudyService;

import javax.inject.Inject;
import javax.inject.Singleton;

import static actions.common.AsyncAction.Async;
import static actions.common.AsyncAction.Executor;
import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;

@Singleton
public class BatchApi extends Controller {

    private final StudyService studyService;
    private final AuthorizationService authorizationService;
    private final BatchService batchService;
    private final ApiService apiService;
    private final DomainJsonMapper domainJsonMapper;
    private final StrictJson strictJson;
    private final BatchDao batchDao;

    @Inject
    BatchApi(StudyService studyService,
             AuthorizationService authorizationService,
             BatchService batchService,
             ApiService apiService,
             DomainJsonMapper domainJsonMapper,
             StrictJson strictJson,
             BatchDao batchDao) {
        this.studyService = studyService;
        this.authorizationService = authorizationService;
        this.batchService = batchService;
        this.apiService = apiService;
        this.domainJsonMapper = domainJsonMapper;
        this.strictJson = strictJson;
        this.batchDao = batchDao;
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    @Transactional
    public Result getBatchesByStudy(String studyId) {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        ArrayNode batchArray = Json.mapper().createArrayNode();
        for (Batch b : study.getBatchList()) {
            batchArray.add(domainJsonMapper.batchAsJsonForApi(b));
        }
        return ok(ApiEnvelope.wrap(batchArray).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    @Transactional
    public Result getBatch(String id) {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessBatch(batch, signedinUser);

        JsonNode batchNode = domainJsonMapper.batchAsJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(Raw.class)
    @Transactional
    public Result createBatch(play.mvc.Http.Request request, String studyId) {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        JsonNode json = apiService.getJsonFromBody(request);
        ObjectNode jsonObj = apiService.normalizeJsonInputField(json, "batchInput");
        BatchProperties props = strictJson.jsonNodeAsObj(jsonObj, BatchProperties.class);
        apiService.validateProps(props);

        Batch batch = batchService.bindToBatch(props);
        batchService.initAndPersistBatch(batch, study);

        JsonNode batchNode = domainJsonMapper.batchAsJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(Raw.class)
    public Result updateBatch(play.mvc.Http.Request request, String id) {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessBatch(batch, signedinUser, true);

        BatchProperties props = batchService.bindToProperties(batch);
        JsonNode json = apiService.getJsonFromBody(request);
        ObjectNode jsonObj = apiService.normalizeJsonInputField(json, "batchInput");
        props = strictJson.updateFromJson(props, jsonObj);
        apiService.validateProps(props);

        batchService.updateBatch(batch, props);
        batchDao.merge(batch);

        JsonNode batchNode = domainJsonMapper.batchAsJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @Transactional
    public Result deleteBatch(String id) {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessBatch(batch, signedinUser, true);
        batchService.remove(batch);
        return ok(ApiEnvelope.wrap("Batch deleted successfully").asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result getBatchSession(String id, boolean asText) {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessBatch(batch, signedinUser, true);

        ObjectNode session = apiService.getSessionNode(batch.getBatchSessionData(), batch.getBatchSessionVersion(), asText);
        return ok(ApiEnvelope.wrap(session).asJsonNode());
    }

    /**
     * Updates the batch session. Uses `BodyParser.Raw` to handle JSON payloads with potential malformed content
     * gracefully and give detailed error messages to the user.
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(Raw.class)
    public Result updateBatchSession(play.mvc.Http.Request request, String id, Option<Long> version) {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessBatch(batch, signedinUser);

        String sessionData = apiService.getSessionDataFromBody(request);

        Long currentVersion = version.getOrElse(batch::getBatchSessionVersion);
        Long newVersion = batchDao.updateBatchSession(batch.getId(), currentVersion, sessionData);
        if (newVersion == null) {
            throw new ForbiddenException("Batch session version conflict");
        }

        ObjectNode data = Json.newObject().put("version", newVersion);
        return ok(ApiEnvelope.wrap("Batch session updated successfully", data).asJsonNode());
    }

}
