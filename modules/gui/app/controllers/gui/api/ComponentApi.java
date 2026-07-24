package controllers.gui.api;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import general.common.ApiEnvelope;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import json.common.StrictJson;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.gui.ComponentProperties;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.BodyParser.Raw;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.ApiService;
import services.gui.AuthorizationService;
import services.gui.ComponentService;
import services.gui.StudyService;

import javax.inject.Inject;
import javax.inject.Singleton;

import static actions.common.TransactionalAction.Transactional;
import static auth.gui.AuthAction.Auth;
import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;

@Singleton
public class ComponentApi extends Controller {

    private final StudyService studyService;
    private final AuthorizationService authorizationService;
    private final ApiService apiService;
    private final ComponentService componentService;
    private final DomainJsonMapper domainJsonMapper;
    private final StrictJson strictJson;

    @Inject
    ComponentApi(StudyService studyService,
                 AuthorizationService authorizationService,
                 ApiService apiService,
                 ComponentService componentService,
                 DomainJsonMapper domainJsonMapper,
                 StrictJson strictJson) {
        this.studyService = studyService;
        this.authorizationService = authorizationService;
        this.apiService = apiService;
        this.componentService = componentService;
        this.domainJsonMapper = domainJsonMapper;
        this.strictJson = strictJson;
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    @Transactional
    public Result getComponentsByStudy(String studyIdOrUuid) {
        Study study = studyService.getStudyFromIdOrUuid(studyIdOrUuid);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        ArrayNode componentArray = Json.mapper().createArrayNode();
        for (Component c : study.getComponentList()) {
            componentArray.add(domainJsonMapper.componentAsJsonNodeForApi(c));
        }
        return ok(ApiEnvelope.wrap(componentArray).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result getComponent(String id) {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessComponent(component, signedinUser);

        JsonNode componentNode = domainJsonMapper.componentAsJsonNodeForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    /**
     * Creates a component within the specified study
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @Transactional
    @BodyParser.Of(Raw.class)
    public Result createComponent(Http.Request request, String studyId) {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        JsonNode json = apiService.getJsonFromBody(request);
        ObjectNode jsonObj = apiService.normalizeJsonInputField(json, "componentInput");
        ComponentProperties props = strictJson.jsonNodeAsObj(jsonObj, ComponentProperties.class);
        apiService.validateProps(props);

        Component component = componentService.createAndPersistComponent(study, props);

        JsonNode componentNode = domainJsonMapper.componentAsJsonNodeForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(Raw.class)
    public Result updateComponent(Http.Request request, String id) {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessComponent(component, signedinUser, true);

        JsonNode json = apiService.getJsonFromBody(request);
        ObjectNode jsonObj = apiService.normalizeJsonInputField(json, "componentInput");
        ComponentProperties props = componentService.bindToProperties(component);
        props = strictJson.updateFromJson(props, jsonObj);
        apiService.validateProps(props);

        componentService.updateComponentAfterEdit(component, props);

        JsonNode componentNode = domainJsonMapper.componentAsJsonNodeForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @Transactional
    public Result deleteComponent(String id) {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessComponent(component, signedinUser, true);
        componentService.remove(component);
        return ok(ApiEnvelope.wrap("Component deleted successfully").asJsonNode());
    }

}
