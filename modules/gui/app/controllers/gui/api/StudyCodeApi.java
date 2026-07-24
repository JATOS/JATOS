package controllers.gui.api;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction.Transactional;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import daos.common.StudyLinkDao;
import general.common.ApiEnvelope;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import models.common.Batch;
import models.common.Study;
import models.common.StudyLink;
import models.common.User;
import models.common.workers.WorkerType;
import models.gui.StudyCodeProperties;
import play.mvc.BodyParser;
import play.mvc.BodyParser.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import scala.Option;
import services.gui.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;

@Singleton
public class StudyCodeApi extends Controller {

    private final AuthorizationService authorizationService;
    private final DomainJsonMapper domainJsonMapper;
    private final ApiService apiService;
    private final StudyService studyService;
    private final BatchService batchService;
    private final StudyLinkService studyLinkService;
    private final StudyLinkDao studyLinkDao;

    @Inject
    StudyCodeApi(AuthorizationService authorizationService,
                 DomainJsonMapper domainJsonMapper,
                 ApiService apiService,
                 StudyService studyService,
                 BatchService batchService,
                 StudyLinkService studyLinkService,
                 StudyLinkDao studyLinkDao) {
        this.authorizationService = authorizationService;
        this.domainJsonMapper = domainJsonMapper;
        this.apiService = apiService;
        this.studyService = studyService;
        this.batchService = batchService;
        this.studyLinkService = studyLinkService;
        this.studyLinkDao = studyLinkDao;
    }

    /**
     * Get or generate study codes for the given study, batch, and worker type. Either get the properties from the query
     * parameters or the JSON body.
     *
     * @param studyId       Study's ID or UUID
     * @param batchIdOption Optional specify the batch ID to which the study codes should belong to. If it is not
     *                      specified, the default batch of this study will be used.
     * @param type          Worker type: `PersonalSingle` (or `ps`), `PersonalMultiple` (or `pm`), `GeneralSingle` (or
     *                      `gs`), `GeneralMultiple` (or `gm`), `MTurk` (or `mt`)
     * @param comment       Some comment that will be associated with the worker.
     * @param amountOption  Number of study codes that have to be generated. If empty, 1 is assumed.
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @Transactional
    public Result getOrGenerateStudyCodes(Http.Request request, String studyId, Option<Long> batchIdOption, String type,
                                          String comment, Option<Integer> amountOption) {
        // Get props either from query parameters or JSON body
        JsonNode json = request.body().asJson();
        Long batchId = batchIdOption.nonEmpty()
                ? batchIdOption.get()
                : apiService.getFieldFromJson(json, "batchId", Long.class, null);
        type = type != null
                ? type
                : apiService.getFieldFromJson(json, "type", String.class, null);
        comment = comment != null
                ? comment
                : apiService.getFieldFromJson(json, "comment", String.class, null);
        int amount = amountOption.nonEmpty()
                ? amountOption.get()
                : apiService.getFieldFromJson(json, "amount", Integer.class, 1);

        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Batch batch = batchService.getBatchOrDefaultBatch(batchId, study);
        authorizationService.canUserAccessBatch(batch, signedinUser);

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType(WorkerType.fromWireValue(type));
        props.setComment(comment);
        props.setAmount(amount);
        apiService.validateProps(props);

        List<String> studyCodeList = studyLinkService.getStudyCodes(batch, props);
        return ok(ApiEnvelope.wrap(studyCodeList).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result getStudyCode(String code) {
        StudyLink studyLink = studyLinkDao.findByStudyCode(code);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudyLink(studyLink, signedinUser);

        JsonNode linkNode = domainJsonMapper.getStudyLinkData(studyLink);
        return ok(ApiEnvelope.wrap(linkNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(Json.class)
    public Result toggleStudyCodeActive(Http.Request request, String code) {
        StudyLink studyLink = studyLinkDao.findByStudyCode(code);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudyLink(studyLink, signedinUser);

        JsonNode json = request.body().asJson();
        boolean active = apiService.getActiveFlagFromJson(json);
        studyLink.setActive(active);
        studyLinkDao.merge(studyLink);

        JsonNode linkNode = domainJsonMapper.getStudyLinkData(studyLink);
        return ok(ApiEnvelope.wrap(linkNode).asJsonNode());
    }

}
