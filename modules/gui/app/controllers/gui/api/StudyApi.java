package controllers.gui.api;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction.Transactional;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.common.BadRequestException;
import exceptions.common.JatosException;
import exceptions.common.NotFoundException;
import general.common.ApiEnvelope;
import general.common.Common;
import general.common.StudyLogger;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import json.common.StrictJson;
import models.common.Study;
import models.common.User;
import models.gui.StudyProperties;
import play.core.utils.HttpHeaderParameterEncoding;
import play.http.HttpEntity;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.BodyParser.Raw;
import play.mvc.Controller;
import play.mvc.ResponseHeader;
import play.mvc.Result;
import services.gui.ApiService;
import services.gui.AuthorizationService;
import services.gui.ImportExportService;
import services.gui.StudyService;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static general.common.ApiEnvelope.ErrorCode.INVALID_JSON;
import static general.common.ApiEnvelope.ErrorCode.WRONG_CONTENT_TYPE;
import static java.lang.Boolean.TRUE;
import static models.common.User.Role.*;

@Singleton
public class StudyApi extends Controller {

    private final AuthorizationService authorizationService;
    private final ApiService apiService;
    private final StudyService studyService;
    private final ImportExportService importExportService;
    private final UserDao userDao;
    private final StudyDao studyDao;
    private final DomainJsonMapper domainJsonMapper;
    private final StrictJson strictJson;
    private final StudyLogger studyLogger;

    @Inject
    StudyApi(AuthorizationService authorizationService,
             ApiService apiService,
             StudyService studyService,
             ImportExportService importExportService,
             UserDao userDao,
             StudyDao studyDao,
             DomainJsonMapper domainJsonMapper,
             StrictJson strictJson,
             StudyLogger studyLogger) {
        this.authorizationService = authorizationService;
        this.apiService = apiService;
        this.studyService = studyService;
        this.importExportService = importExportService;
        this.userDao = userDao;
        this.studyDao = studyDao;
        this.domainJsonMapper = domainJsonMapper;
        this.strictJson = strictJson;
        this.studyLogger = studyLogger;
    }

    /**
     * HEAD requests: checks if a study exists in the system by its ID or UUID. Only with admin tokens.
     */
    @Async(Executor.IO)
    @Auth(roles = ADMIN, types = {TOKEN, SESSION})
    public Result checkStudyExists(String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        return study != null ? noContent() : notFound();
    }


    /**
     * Returns all study properties a user can access.
     *
     * @param withComponentProperties Flag if true, all component properties of the study will be included
     * @param withBatchProperties     Flag if true, all batch properties will be included
     * @return All study properties the user has access to (is member of) in JSON
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    @Transactional
    public Result getAllStudyPropertiesOfSignedinUser(Boolean withComponentProperties, Boolean withBatchProperties) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        List<Study> studies = studyDao.findAllByUser(signedinUser);

        ArrayNode studiesArray = Json.mapper().createArrayNode();
        for (Study s : studies) {
            studiesArray.add(domainJsonMapper.studyAsJsonForApi(s, withComponentProperties, withBatchProperties));
        }
        return ok(ApiEnvelope.wrap(studiesArray).asJsonNode());
    }

    /**
     * Handels deprecated endpoint to create a new study
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(Raw.class)
    public Result createStudy(play.mvc.Http.Request request) {
        JsonNode json = apiService.getJsonFromBody(request);
        return createStudyFromJson(json, false);
    }

    /**
     * Dispatches the request based on the {@code Content-Type} header: 1) ZIP / multipart upload → import a study
     * archive 2) JSON → create a new study from the request body.
     *
     * This endpoint supports multiple, mutually exclusive body formats, so it cannot be handled by a single fixed
     * {@link play.mvc.BodyParser} annotation. Body parsing is therefore performed explicitly in the respective
     * branches.
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result importOrCreateStudy(play.mvc.Http.Request request, boolean keepProperties, boolean keepAssets,
                                      boolean keepCurrentAssetsName, boolean renameAssets) {
        String contentType = request.getHeaders().get(play.mvc.Http.HeaderNames.CONTENT_TYPE).orElse("");

        if (contentType.startsWith("multipart/form-data")
                || contentType.startsWith("application/zip")
                || contentType.startsWith("application/jzip")
                || contentType.startsWith("application/octet-stream")
                || contentType.isEmpty()) {
            return importStudy(request, keepProperties, keepAssets, keepCurrentAssetsName, renameAssets);
        }

        if (contentType.startsWith("application/json")) {
            JsonNode json = request.body().asJson();
            if (json == null) {
                throw new BadRequestException("Request body is empty or not valid JSON", INVALID_JSON);
            }
            return createStudyFromJson(json, renameAssets);
        }

        return status(415, ApiEnvelope.wrap("Wrong 'Content-Type' " + contentType, WRONG_CONTENT_TYPE).asJsonNode());
    }

    private Result createStudyFromJson(JsonNode json, boolean renameAssets) {
        ObjectNode jsonObj = apiService.normalizeJsonInputField(json, "studyInput");
        StudyProperties props = strictJson.jsonNodeAsObj(jsonObj, StudyProperties.class);
        apiService.validateProps(props);

        Study study = studyService.createAndPersistStudyAndAssetsDir(props, renameAssets);

        JsonNode studyNode = domainJsonMapper.studyAsJsonForApi(study, false, false);
        return created(ApiEnvelope.wrap(studyNode).asJsonNode());
    }

    /**
     * Imports a JATOS study archive
     *
     * @param keepProperties        If true and the study exists already in JATOS, the current properties are kept.
     *                              Default is `false` (properties are overwritten by default). If the study doesn't
     *                              already exist, this parameter has no effect.
     * @param keepAssets            If true and the study exists already in JATOS, the current study assets directory is
     *                              kept. Default is `false` (assets are overwritten by default). If the study doesn't
     *                              already exist, this parameter has no effect.
     * @param keepCurrentAssetsName If the assets are going to be overwritten (`keepAssets=false`), this flag indicates
     *                              if the study assets directory name is taken from the current or the uploaded one. In
     *                              the common case that both names are the same, this has no effect. But if the current
     *                              asset directory name is different from the uploaded one, a
     *                              `keepCurrentAssetsName=true` indicates that the name of the currently installed
     *                              assets directory should be kept. A `false` indicates that the name should be taken
     *                              from the uploaded one. Default is `true`.
     * @param renameAssets          If the study assets directory already exists in JATOS but belongs to a different
     *                              study, it cannot be overwritten. In this case you can set `renameAssets=true` to let
     *                              JATOS add a suffix to the assets directory name (original name + "_" + a number).
     *                              Default is `true`.
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result importStudy(play.mvc.Http.Request request, boolean keepProperties, boolean keepAssets,
                              boolean keepCurrentAssetsName, boolean renameAssets) {
        List<String> allowedContentTypes = Arrays.asList("application/zip", "application/jzip", "application/octet-stream");
        Path file = apiService.extractFile(request, Study.STUDY, allowedContentTypes);

        try {
            Map<String, Object> importInfo = importExportService.importStudy(file);

            Study study = importExportService.importStudyConfirmed(keepProperties, keepAssets,
                    keepCurrentAssetsName, renameAssets);

            JsonNode studyNode = domainJsonMapper.studyAsJsonForApi(study, false, false);
            JsonNode envelope = ApiEnvelope.wrap(studyNode).asJsonNode();
            boolean wasOverwritten = TRUE.equals(importInfo.get("studyExists"));
            return wasOverwritten ? ok(envelope) : created(envelope);
        } finally {
            importExportService.cleanupAfterStudyImport();
        }
    }

    /**
     * Returns the study archive (.jzip) as a file
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result exportStudy(String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Path zipFile;
        try {
            zipFile = importExportService.createStudyExportZipFile(study.getId());
        } catch (Exception e) {
            String errorMsg = "Export of study \"" + study.getTitle() + "\" (ID " + study.getId() + ") failed.";
            throw new JatosException(errorMsg, e);
        }

        String cdHeader = "attachment; "
                + HttpHeaderParameterEncoding.encode("filename", "jatos_study_"
                + study.getUuid() + "." + Common.getStudyArchiveSuffix());
        try {
            // We need the "Content-Disposition" header for API calls (not for the GUI)
            Context.current().response().setHeader(CONTENT_DISPOSITION, cdHeader);
            return ok().streamed(
                    IOUtils.okFileStreamed(zipFile, IOUtils.deleteFile(zipFile)),
                    Optional.of(Files.size(zipFile)),
                    Optional.of("application/zip"));
        } catch (Exception e) {
            IOUtils.deleteFile(zipFile).run();
            throw new JatosException(e);
        }
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @Transactional
    public Result deleteStudy(String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        studyService.removeStudyInclAssets(study);
        return ok(ApiEnvelope.wrap("Study deleted successfully").asJsonNode());
    }

    /**
     * Get study properties
     *
     * @param id                      Study's ID or UUID
     * @param withComponentProperties Flag if true, all component properties of the study will be included
     * @param withBatchProperties     Flag if true, all batch properties will be included
     * @return The study properties in JSON
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    @Transactional
    public Result getStudyProperties(String id, Boolean withComponentProperties, Boolean withBatchProperties) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        JsonNode studiesNode = domainJsonMapper.studyAsJsonForApi(study, withComponentProperties, withBatchProperties);
        return ok(ApiEnvelope.wrap(studiesNode).asJsonNode());
    }


    /**
     * Updates the study properties. Regular users may update only studies they own. Admins may update only the
     * activation status of any study.
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(Raw.class)
    @Transactional
    public Result updateStudyProperties(play.mvc.Http.Request request, String id) throws IOException {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Study study = studyService.getStudyFromIdOrUuid(id);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        boolean isMemberOrSuperuser = authorizationService.isMemberOrSuperuser(study, signedinUser);
        boolean isAdminNonMember = signedinUser.isAdmin() && !isMemberOrSuperuser;

        JsonNode json = apiService.getJsonFromBody(request);
        ObjectNode jsonObj = apiService.normalizeJsonInputField(json, "studyInput");

        // Admins who are not members: only allow toggling "active"
        if (isAdminNonMember) {
            boolean active = apiService.getActiveFlagFromJson(jsonObj);
            study.setActive(active);
            studyDao.merge(study);

            ObjectNode responseJson = Json.mapper().createObjectNode();
            responseJson.put("id", study.getId());
            responseJson.put("uuid", study.getUuid());
            responseJson.put("active", study.isActive());
            return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
        }

        authorizationService.canUserAccessStudy(study, signedinUser, true);

        StudyProperties props = studyService.bindToProperties(study);
        props = strictJson.updateFromJson(props, jsonObj);
        apiService.validateProps(props);

        studyService.updateStudyAndRenameAssets(study, props);

        JsonNode studyNode = domainJsonMapper.studyAsJsonForApi(study, false, false);
        return ok(ApiEnvelope.wrap(studyNode).asJsonNode());
    }

    /**
     * Get user IDs of all members of a study.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result allMembersOfStudy(String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        List<Long> userIds = studyDao.findAllMembersByStudyId(study.getId());

        ObjectNode responseJson = Json.mapper().createObjectNode();
        responseJson.put("id", study.getId());
        responseJson.put("uuid", study.getUuid());
        responseJson.putPOJO("members", userIds);
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @Transactional
    public Result addMemberToStudy(String id, Long userId) {
        return changeMemberOfStudy(id, userId, true);
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @Transactional
    public Result removeMemberFromStudy(String id, Long userId) {
        return changeMemberOfStudy(id, userId, false);
    }

    public Result changeMemberOfStudy(String id, Long userId, boolean isMember) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        User user = userDao.findById(userId);
        authorizationService.checkUserExists(user);

        studyService.changeUserMember(study, user, isMember);

        List<Long> userIds = studyDao.findAllMembersByStudyId(study.getId());
        ObjectNode responseJson = Json.mapper().createObjectNode();
        responseJson.put("id", study.getId());
        responseJson.put("uuid", study.getUuid());
        responseJson.putPOJO("members", userIds);
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

    /**
     * Returns a study log.
     *
     * @param id         Study's ID or UUID
     * @param entryLimit It cuts the log after the number of lines given in entryLimit. Only if 'download' is false.
     * @param download   If true streams the whole study log file - if not only until entryLimit
     * @return Depending on the 'download' flag returns the whole study log file - or only part of it (until entryLimit)
     * in reverse order and 'Transfer-Encoding:chunked'
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result studyLog(String id, int entryLimit, boolean download) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        if (download) {
            Path studyLogPath = Path.of(studyLogger.getPath(study));
            if (Files.notExists(studyLogPath)) throw new NotFoundException("Study log file doesn't exist");

            Source<ByteString, ?> source = FileIO.fromPath(studyLogPath);
            Optional<Long> contentLength = Optional.of(studyLogPath.toFile().length());
            String cdHeader = "attachment; "
                    + HttpHeaderParameterEncoding.encode("filename", "jatos_studylog_"
                    + studyLogger.getFilename(study));
            Context.current().response().setHeader(CONTENT_DISPOSITION, cdHeader);
            return new Result(
                    new ResponseHeader(200, Collections.emptyMap()),
                    new HttpEntity.Streamed(source, contentLength, Optional.of("application/octet-stream")));
        } else {
            return ok().chunked(studyLogger.readLogFile(study, entryLimit)).as("application/x-ndjson");
        }
    }


}
