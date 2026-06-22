package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.*;
import daos.common.worker.WorkerType;
import exceptions.common.*;
import general.common.*;
import general.common.ApiEnvelope.ErrorCode;
import http.common.Http.Context;
import http.common.HttpUtils;
import json.common.DefaultJson;
import json.common.DirectoryStructureToJson;
import json.common.JsonUtils;
import json.common.StrictJson;
import play.libs.Files.TemporaryFile;
import utils.common.*;
import models.common.*;
import models.common.User.Role;
import models.gui.*;
import org.apache.commons.lang3.tuple.Pair;
import play.Logger;
import play.core.utils.HttpHeaderParameterEncoding;
import play.http.HttpEntity;
import play.libs.Json;
import play.mvc.*;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import scala.Option;
import services.gui.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

import static auth.gui.AuthAction.Auth;
import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static auth.gui.AuthApiToken.API_TOKEN;
import static general.common.ApiEnvelope.ErrorCode.*;
import static models.common.User.Role.*;


/**
 * JATOS API Controller
 */
@Singleton
public class Api extends Controller {

    private static final Logger.ALogger LOGGER = Logger.of(Api.class);

    private final ApiService apiService;
    private final AdminService adminService;
    private final ComponentResultIdsExtractor componentResultIdsExtractor;
    private final StudyDao studyDao;
    private final ComponentResultDao componentResultDao;
    private final UserDao userDao;
    private final BatchDao batchDao;
    private final ApiTokenDao apiTokenDao;
    private final StudyLinkDao studyLinkDao;
    private final GroupResultDao groupResultDao;
    private final StudyService studyService;
    private final ComponentService componentService;
    private final StudyLinkService studyLinkService;
    private final BatchService batchService;
    private final ImportExport importExport;
    private final ImportExportService importExportService;
    private final ResultRemover resultRemover;
    private final ResultStreamer resultStreamer;
    private final AuthorizationService authorizationService;
    private final JsonUtils jsonUtils;
    private final LogFileReader logFileReader;
    private final StudyLogger studyLogger;
    private final IOUtils ioUtils;
    private final UserService userService;
    private final ApiTokenService apiTokenService;
    private final StrictJson strictJson;
    private final DefaultJson defaultJson;

    @Inject
    Api(ApiService apiService,
        AdminService adminService,
        ComponentResultIdsExtractor componentResultIdsExtractor,
        StudyDao studyDao,
        ComponentResultDao componentResultDao,
        UserDao userDao,
        BatchDao batchDao,
        ApiTokenDao apiTokenDao,
        StudyLinkDao studyLinkDao,
        GroupResultDao groupResultDao,
        StudyService studyService,
        ComponentService componentService,
        StudyLinkService studyLinkService,
        BatchService batchService,
        ImportExport importExport,
        ImportExportService importExportService,
        ResultRemover resultRemover,
        ResultStreamer resultStreamer,
        AuthorizationService authorizationService,
        JsonUtils jsonUtils,
        LogFileReader logFileReader,
        StudyLogger studyLogger,
        IOUtils ioUtils,
        UserService userService,
        ApiTokenService apiTokenService,
        StrictJson strictJson,
        DefaultJson defaultJson
    ) {
        this.apiService = apiService;
        this.adminService = adminService;
        this.componentResultIdsExtractor = componentResultIdsExtractor;
        this.studyDao = studyDao;
        this.componentResultDao = componentResultDao;
        this.userDao = userDao;
        this.batchDao = batchDao;
        this.apiTokenDao = apiTokenDao;
        this.studyLinkDao = studyLinkDao;
        this.groupResultDao = groupResultDao;
        this.studyService = studyService;
        this.componentService = componentService;
        this.studyLinkService = studyLinkService;
        this.batchService = batchService;
        this.importExport = importExport;
        this.importExportService = importExportService;
        this.resultRemover = resultRemover;
        this.resultStreamer = resultStreamer;
        this.authorizationService = authorizationService;
        this.jsonUtils = jsonUtils;
        this.logFileReader = logFileReader;
        this.studyLogger = studyLogger;
        this.ioUtils = ioUtils;
        this.userService = userService;
        this.apiTokenService = apiTokenService;
        this.strictJson = strictJson;
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
     * Returns admin status information in JSON. Only with admin tokens.
     */
    @Async(Executor.IO)
    @Auth(roles = ADMIN, types = {TOKEN, SESSION})
    public Result status() {
        JsonNode status = adminService.getAdminStatus();
        return ok(ApiEnvelope.wrap(status).asJsonNode());
    }

    /**
     * Returns the content of the logs directory as JSON
     */
    @Async(Executor.IO)
    @Auth(roles = ADMIN, types = {TOKEN, SESSION})
    public Result listLogs() {
        Path base = Path.of(Common.getLogsPath());
        JsonNode structure = DirectoryStructureToJson.get(base, true);
        return ok(ApiEnvelope.wrap(structure).asJsonNode());
    }

    /**
     * Returns the log file specified by 'filename'. If 'reverse' is true, it returns the content of the file in reverse
     * order and as 'Transfer-Encoding:chunked'. It limits the number of lines to the given lineLimit. If 'reverse' is
     * false, it returns the file for download.
     */
    @Async(Executor.IO)
    @Auth(roles = ADMIN, types = {TOKEN, SESSION})
    public Result logs(String filename, Integer lineLimit, boolean reverse) {
        filename = HttpUtils.urlDecode(filename);
        if (!ioUtils.existsAndSecure(Common.getLogsPath(), filename)) {
            throw new NotFoundException("Log file not found");
        }

        if (reverse) {
            return ok().chunked(logFileReader.read(filename, lineLimit)).as("text/plain; charset=UTF-8");
        } else {
            Path logPath = Path.of(Common.getLogsPath(), filename);
            Source<ByteString, ?> source = FileIO.fromPath(logPath);
            Optional<Long> contentLength = Optional.of(logPath.toFile().length());
            String cdHeader = "attachment; "
                    + HttpHeaderParameterEncoding.encode("filename", "jatos_logs_" + filename);
            // We need the "Content-Disposition" header for API calls (not for the GUI)
            Context.current().response().setHeader(Http.HeaderNames.CONTENT_DISPOSITION, cdHeader);
            return new Result(
                    new ResponseHeader(200, Collections.emptyMap()),
                    new HttpEntity.Streamed(source, contentLength, Optional.of("application/octet-stream")));
        }
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
            ObjectNode userNode = (ObjectNode) defaultJson.asJsonWithStrictViewInclusion(user);
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
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    public Result getUser(Long id) {
        User user = userDao.findById(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkAdminOrSelf(signedinUser, user);

        JsonNode userNode = defaultJson.asJsonWithStrictViewInclusion(user);
        return ok(ApiEnvelope.wrap(userNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = ADMIN, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Raw.class)
    public Result createUser(Http.Request request) throws JsonProcessingException {
        JsonNode json = apiService.getJsonFromBody(request);
        NewUserProperties props = strictJson.jsonNodeAsObj(json, NewUserProperties.class);
        apiService.validateProps(props);
        authorizationService.checkAuthMethodIsDbOrLdap(props);

        User user = userService.registerUser(props);
        JsonNode userJson = defaultJson.asJsonWithStrictViewInclusion(user);
        return created(ApiEnvelope.wrap(userJson).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateUser(Http.Request request, Long id) {
        User user = userDao.findById(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkAuthMethodIsDbOrLdap(user);
        authorizationService.checkAdminOrSelf(signedinUser, user);

        UserProperties props = userService.bindToProperties(user);
        JsonNode json = apiService.getJsonFromBody(request);
        props = strictJson.updateFromJson(props, json);
        apiService.validateProps(props);
        authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, user);

        userService.updateUser(user, props);

        JsonNode userNode = defaultJson.asJsonWithStrictViewInclusion(user);
        return ok(ApiEnvelope.wrap(userNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = ADMIN, types = {TOKEN, SESSION})
    public Result changeUserRole(Http.Request request, Long id) {
        User user = userDao.findById(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkNotUserAdmin(user);
        authorizationService.checkNotYourself(signedinUser, user);

        JsonNode json = request.body().asJson();
        Role role = apiService.getFieldFromJson(json, "role", Role.class);
        if (!Arrays.asList(VIEWER, USER).contains(role)) {
            throw new BadRequestException("Invalid role: " + role);
        }

        user.updateRoles(role);
        userDao.merge(user);

        JsonNode userNode = defaultJson.asJsonWithStrictViewInclusion(user);
        return ok(ApiEnvelope.wrap(userNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    public Result deleteUser(Long id) {
        User user = userDao.findById(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkAdminOrSelf(signedinUser, user);
        authorizationService.checkNotUserAdmin(user);

        userService.removeUser(user);

        return ok(ApiEnvelope.wrap("User deleted successfully").asJsonNode());
    }

    /**
     * Generate API tokens. It returns the token and the token metadata.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Json.class)
    public Result generateApiToken(Http.Request request, Long userId) {
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

        ArrayNode tokens = Json.mapper().createArrayNode();
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
        authorizationService.checkAdminOrSelf(signedinUser, apiToken.getUser());

        return ok(ApiEnvelope.wrap(apiToken).asJsonNode());
    }

    /**
     * Activate or deactivate a token specified by its ID. Admins can update tokens of non-admin users. Users (including
     * admins) can update their own tokens.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Json.class)
    public Result toggleApiTokenActive(Http.Request request, Long id) {
        ApiToken token = apiTokenDao.find(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkUserAllowedToAccessApiToken(token, signedinUser);

        JsonNode json = request.body().asJson();
        boolean active = apiService.getActiveFlagFromJson(json);
        token.setActive(active);
        apiTokenDao.merge(token);

        ObjectNode responseJson = Json.mapper().createObjectNode();
        responseJson.put("id", token.getId());
        responseJson.put("active", token.isActive());
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

    /**
     * Admins can delete tokens of non-admin users. Users (including admins) can delete their own tokens.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN}, types = {TOKEN, SESSION})
    public Result deleteApiToken(Long id) {
        ApiToken token = apiTokenDao.find(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkUserAllowedToAccessApiToken(token, signedinUser);

        apiTokenDao.remove(token);

        return ok(ApiEnvelope.wrap("Token deleted successfully").asJsonNode());
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
    public Result getAllStudyPropertiesOfSignedinUser(Boolean withComponentProperties, Boolean withBatchProperties) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        List<Study> studies = studyDao.findAllByUser(signedinUser);

        ArrayNode studiesArray = Json.mapper().createArrayNode();
        for (Study s : studies) {
            studiesArray.add(jsonUtils.studyAsJsonForApi(s, withComponentProperties, withBatchProperties));
        }
        return ok(ApiEnvelope.wrap(studiesArray).asJsonNode());
    }

    /**
     * Handels deprecated endpoint to create a new study
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Raw.class)
    public Result createStudy(Http.Request request) {
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
    public Result importOrCreateStudy(Http.Request request, boolean keepProperties, boolean keepAssets,
                                      boolean keepCurrentAssetsName, boolean renameAssets) {
        String contentType = request.getHeaders().get(Http.HeaderNames.CONTENT_TYPE).orElse("");

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

        JsonNode studyNode = jsonUtils.studyAsJsonForApi(study, false, false);
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
    public Result importStudy(Http.Request request, boolean keepProperties, boolean keepAssets,
                              boolean keepCurrentAssetsName, boolean renameAssets) {
        List<String> allowedContentTypes = Arrays.asList("application/zip", "application/jzip", "application/octet-stream");
        Path file = apiService.extractFile(request, Study.STUDY, allowedContentTypes);

        try {
            Map<String, Object> importInfo = importExportService.importStudy(file);

            Study study = importExportService.importStudyConfirmed(keepProperties, keepAssets,
                    keepCurrentAssetsName, renameAssets);

            JsonNode studyNode = jsonUtils.studyAsJsonForApi(study, false, false);
            JsonNode envelope = ApiEnvelope.wrap(studyNode).asJsonNode();
            boolean wasOverwritten = Boolean.TRUE.equals(importInfo.get("studyExists"));
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
        return importExport.exportStudy(id);
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
    public Result getStudyProperties(String id, Boolean withComponentProperties, Boolean withBatchProperties) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        JsonNode studiesNode = jsonUtils.studyAsJsonForApi(study, withComponentProperties, withBatchProperties);
        return ok(ApiEnvelope.wrap(studiesNode).asJsonNode());
    }


    /**
     * Updates the study properties. Regular users may update only studies they own. Admins may update only the
     * activation status of any study.
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateStudyProperties(Http.Request request, String id) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Study study = studyService.getStudyFromIdOrUuid(id);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        boolean isMemberOrSuperuser = study.hasUser(signedinUser) || UserService.isAllowedSuperuser(signedinUser);
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

        JsonNode studyNode = jsonUtils.studyAsJsonForApi(study, false, false);
        return ok(ApiEnvelope.wrap(studyNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result deleteStudy(String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        studyService.removeStudyInclAssets(study);
        return ok(ApiEnvelope.wrap("Study deleted successfully").asJsonNode());
    }

    /**
     * Gets the study assets directory structure as JSON
     *
     * @param id      Study's ID or UUID
     * @param flatten Flag, if set to `true` the returned JSON will be a flat list of files (no tree, no directories).
     *                If `false`, the returned JSON will have a tree-like structure and include directories. Default is
     *                `false`.
     * @return JSON with study assets directory structure
     */
    @Async(Executor.STUDY_ASSETS)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result getStudyAssetsStructure(String id, boolean flatten) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Path base;
        try {
            base = ioUtils.getStudyAssetsDir(study.getDirName());
        } catch (IOException e) {
            throw new NotFoundException("Study assets directory couldn't be found");
        }
        JsonNode structure = DirectoryStructureToJson.get(base, flatten);
        return ok(ApiEnvelope.wrap(structure).asJsonNode());
    }

    /**
     * Download a file from a study assets folder.
     *
     * @param id       Study's ID or UUID
     * @param filepath Path to the file in the study assets directory that is supposed to be downloaded. The path can be
     *                 URL encoded but doesn't have to be. Directories cannot be downloaded.
     */
    @Async(Executor.STUDY_ASSETS)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result downloadStudyAssetsFile(String id, String filepath) throws IOException {
        String filepathUrlDecoded = HttpUtils.urlDecode(filepath);
        String finalFilepath = filepathUrlDecoded.startsWith("/")
                ? filepathUrlDecoded.substring(1)
                : filepathUrlDecoded;

        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Path file = ioUtils.getFileInStudyAssetsDir(study.getDirName(), finalFilepath);
        if (!Files.isRegularFile(file)) throw new NotFoundException("File '" + finalFilepath + "' couldn't be found.");
        String cdHeader = "attachment; " + HttpHeaderParameterEncoding.encode("filename", file.getFileName().toString());
        Context.current().response().setHeader(Http.HeaderNames.CONTENT_DISPOSITION, cdHeader);
        return ok().sendPath(file);
    }

    /**
     * Upload a file to a study assets folder.
     *
     * @param id       Study's ID or UUID
     * @param filepath Supposed path of the uploaded file in the study assets directory. If it is null, "", "/" or ", ."
     *                 it will be ignored and the uploaded file saved in the top-level of the assets under the uploaded
     *                 file's name. If it is a directory, the filename is taken from the uploaded file. If it ends with
     *                 a filename, the uploaded file will be renamed to this name. All non-existing subdirectories will
     *                 be created. Existing files will be overwritten. The path can be URL encoded but doesn't have to
     *                 be.
     */
    @Async(Executor.STUDY_ASSETS)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result uploadStudyAssetsFile(Http.Request request, String id, String filepath) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        Http.MultipartFormData<TemporaryFile> body = request.body().asMultipartFormData();
        if (body == null) {
            throw new BadRequestException("File missing", MISSING_FILE);
        }
        FilePart<TemporaryFile> filePart = body.getFile("studyAssetsFile");
        if (filePart == null) {
            throw new BadRequestException("File missing", MISSING_FILE);
        }
        Path uploadedFile = filePart.getRef().path();

        try {
            Path assetsFilePath = apiService.getAssetsFilePath(filepath, filePart.getFilename(), study);
            Path parent = assetsFilePath.getParent();
            if (parent != null) {
                // Make sure the directory that will contain the uploaded file exists.
                Files.createDirectories(parent);
            }

            boolean overwritten = IOUtils.moveFileAndDetectOverwrite(uploadedFile, assetsFilePath);
            String msg = overwritten ? "File overwritten successfully" : "File uploaded successfully";
            JsonNode envelope = ApiEnvelope.wrap(msg).asJsonNode();
            return overwritten ? ok(envelope) : created(envelope);
        } catch (FileAlreadyExistsException e) {
            throw new BadRequestException("File already exists but is of a different type", FILE_ALREADY_EXISTS);
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
        }
    }

    /**
     * Deletes a file in the study assets directory.
     *
     * @param id       Study's ID or UUID
     * @param filepath Path to the file in the study assets directory that is supposed to be deleted. The path can be
     *                 URL encoded but doesn't have to be. Directories cannot be deleted.
     */
    @Async(Executor.STUDY_ASSETS)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result deleteStudyAssetsFile(String id, String filepath) {
        String filepathUrlDecoded = HttpUtils.urlDecode(filepath);
        String finalFilepath = filepathUrlDecoded.startsWith("/")
                ? filepathUrlDecoded.substring(1)
                : filepathUrlDecoded;

        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        try {
            Path file = ioUtils.getFileInStudyAssetsDir(study.getDirName(), finalFilepath);
            if (Files.isDirectory(file)) throw new JatosException("Directories can't be deleted.", ErrorCode.IO_ERROR);
            Files.delete(file);
        } catch (NoSuchFileException e) {
            throw new NotFoundException("File '" + finalFilepath + "' couldn't be found.");
        } catch (IOException e) {
            LOGGER.info(".deleteStudyAssetsFile: " + e.getLocalizedMessage());
            throw new InternalServerErrorException("Error writing file");
        }
        return ok(ApiEnvelope.wrap("File deleted successfully").asJsonNode());
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
    public Result addMemberToStudy(String id, Long userId) {
        return changeMemberOfStudy(id, userId, true);
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
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
            Context.current().response().setHeader(Http.HeaderNames.CONTENT_DISPOSITION, cdHeader);
            return new Result(
                    new ResponseHeader(200, Collections.emptyMap()),
                    new HttpEntity.Streamed(source, contentLength, Optional.of("application/octet-stream")));
        } else {
            return ok().chunked(studyLogger.readLogFile(study, entryLimit)).as("application/x-ndjson");
        }
    }

    /**
     * Creates a component within the specified study
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Raw.class)
    public Result createComponent(Http.Request request, String studyId) {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        JsonNode json = apiService.getJsonFromBody(request);
        ObjectNode jsonObj = apiService.normalizeJsonInputField(json, "componentInput");
        ComponentProperties props = strictJson.jsonNodeAsObj(jsonObj, ComponentProperties.class);
        apiService.validateProps(props);

        Component component = componentService.createAndPersistComponent(study, props);

        JsonNode componentNode = jsonUtils.componentAsJsonNodeForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result getComponentsByStudy(String studyIdOrUuid) {
        Study study = studyService.getStudyFromIdOrUuid(studyIdOrUuid);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        ArrayNode componentArray = Json.mapper().createArrayNode();
        for (Component c : study.getComponentList()) {
            componentArray.add(jsonUtils.componentAsJsonNodeForApi(c));
        }
        return ok(ApiEnvelope.wrap(componentArray).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result getComponent(String id) {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessComponent(component, signedinUser);

        JsonNode componentNode = jsonUtils.componentAsJsonNodeForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateComponent(Http.Request request, String id) {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessComponent(component, signedinUser, true);

        JsonNode json = apiService.getJsonFromBody(request);
        ObjectNode jsonObj = apiService.normalizeJsonInputField(json, "componentInput");
        ComponentProperties props = componentService.bindToProperties(component);
        props = strictJson.updateFromJson(props, jsonObj);
        apiService.validateProps(props);

        componentService.renameHtmlFilePath(component, props.getHtmlFilePath(), props.isHtmlFileRename());
        componentService.updateComponentAfterEdit(component, props);

        JsonNode componentNode = jsonUtils.componentAsJsonNodeForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result deleteComponent(String id) {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessComponent(component, signedinUser, true);
        componentService.remove(component);
        return ok(ApiEnvelope.wrap("Component deleted successfully").asJsonNode());
    }


    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result getBatchesByStudy(String studyId) {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        ArrayNode batchArray = Json.mapper().createArrayNode();
        for (Batch b : study.getBatchList()) {
            batchArray.add(jsonUtils.batchAsJsonForApi(b));
        }
        return ok(ApiEnvelope.wrap(batchArray).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result getBatch(String id) {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessBatch(batch, signedinUser);

        JsonNode batchNode = jsonUtils.batchAsJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Raw.class)
    public Result createBatch(Http.Request request, String studyId) {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        JsonNode json = apiService.getJsonFromBody(request);
        ObjectNode jsonObj = apiService.normalizeJsonInputField(json, "batchInput");
        BatchProperties props = strictJson.jsonNodeAsObj(jsonObj, BatchProperties.class);
        apiService.validateProps(props);

        Batch batch = batchService.bindToBatch(props);
        batchService.initAndPersistBatch(batch, study);

        JsonNode batchNode = jsonUtils.batchAsJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateBatch(Http.Request request, String id) {
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

        JsonNode batchNode = jsonUtils.batchAsJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
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
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateBatchSession(Http.Request request, String id, Option<Long> version) {
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

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result getGroupsOfBatch(String id) {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessBatch(batch, signedinUser);

        List<GroupResult> groups = groupResultDao.findAllByBatch(batch);

        JsonNode groupArray = jsonUtils.allGroupResults(groups);
        return ok(ApiEnvelope.wrap(groupArray).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
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
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateGroupSession(Http.Request request, Long id, Option<Long> version) {
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

        JsonNode linkNode = jsonUtils.getStudyLinkData(studyLink);
        return ok(ApiEnvelope.wrap(linkNode).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    @BodyParser.Of(BodyParser.Json.class)
    public Result toggleStudyCodeActive(Http.Request request, String code) {
        StudyLink studyLink = studyLinkDao.findByStudyCode(code);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudyLink(studyLink, signedinUser);

        JsonNode json = request.body().asJson();
        boolean active = apiService.getActiveFlagFromJson(json);
        studyLink.setActive(active);
        studyLinkDao.merge(studyLink);

        JsonNode linkNode = jsonUtils.getStudyLinkData(studyLink);
        return ok(ApiEnvelope.wrap(linkNode).asJsonNode());
    }

    /**
     * Returns results (including metadata, data, and files) in a zip file. The results are specified by IDs (can be
     * nearly any kind) in the request's body or as query parameters. Streaming is used to reduce memory and disk
     * usage.
     *
     * @param isApiCall If true, the response JSON gets an additional 'apiVersion' field
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result exportResults(Http.Request request, Boolean isApiCall) {
        Map<String, Object> wrapperObject = isApiCall
                ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
                : Collections.emptyMap();

        // The check if the signedin user is a member of the study or a superuser is done in the ResultStreamer
        Source<ByteString, ?> dataSource = resultStreamer.streamResults(request, ResultStreamer.ResultType.COMBINED,
                wrapperObject);

        String cdHeader = "attachment; "
                + HttpHeaderParameterEncoding.encode("filename", "jatos_results_"
                + StringUtils.getDateTimeYyyyMMddHHmmss() + "." + Common.getResultsArchiveSuffix());
        Context.current().response().setHeader(Http.HeaderNames.CONTENT_DISPOSITION, cdHeader);
        return ok().chunked(dataSource).as("application/zip");
    }

    /**
     * Returns all result's metadata (but not result files and not metadata) in a zip file. The results are specified by
     * IDs (can be any kind) in the request's body or query parameters. Streaming is used to reduce memory and disk
     * usage.
     *
     * @param isApiCall If true, the response JSON gets an additional 'apiVersion' field
     * @param download  If true, the response JSON is in a file, otherwise in the response body
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result exportResultMetadata(Http.Request request, boolean download, Boolean isApiCall) throws IOException {
        Map<String, Object> wrapperObject = isApiCall
                ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
                : Collections.emptyMap();

        // The check if the signedin user is a member of the study or a superuser is done in the ResultStreamer
        Path file = resultStreamer.writeResultMetadata(request, wrapperObject);
        Result result = ok().streamed(
                IOUtils.okFileStreamed(file, IOUtils.deleteFile(file)),
                Optional.of(Files.size(file)),
                Optional.of("application/json"));
        if (download) {
            String cdHeader = "attachment; "
                    + HttpHeaderParameterEncoding.encode("filename", "jatos_results_metadata_"
                    + StringUtils.getDateTimeYyyyMMddHHmmss() + ".json");
            Context.current().response().setHeader(Http.HeaderNames.CONTENT_DISPOSITION, cdHeader);
        }
        return result;
    }

    /**
     * Returns result data only (not the result files, not the metadata). Data is stored in ComponentResults. Returns
     * the result data as plain text (each result data in a new line) or in a zip file (each result data in its own
     * file). The results are specified by IDs (can be any kind) in the request's body or as query parameters. Both
     * options use streaming to reduce memory and disk usage.
     *
     * @param asPlainText If true, the results will be returned in one single text file, each result in a new line.
     * @param isApiCall   If true, the response JSON gets an additional 'apiVersion' field
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result exportResultData(Http.Request request, boolean asPlainText, boolean download, boolean isApiCall) {
        // The check if the signedin user is a member of the study or a superuser is done in the ResultStreamer
        if (asPlainText) {
            Source<ByteString, ?> dataSource = resultStreamer.streamComponentResultData(request);
            Result result = ok().chunked(dataSource).as("text/plain; charset=UTF-8");
            if (download) {
                String cdHeader = "attachment; "
                        + HttpHeaderParameterEncoding.encode("filename", "jatos_results_data_"
                        + StringUtils.getDateTimeYyyyMMddHHmmss() + ".txt");
                Context.current().response().setHeader(Http.HeaderNames.CONTENT_DISPOSITION, cdHeader);
            }
            return result;
        } else {
            Map<String, Object> wrapperObject = isApiCall
                    ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
                    : Collections.emptyMap();
            Source<ByteString, ?> dataSource = resultStreamer.streamResults(request, ResultStreamer.ResultType.DATA_ONLY,
                    wrapperObject);
            String cdHeader = "attachment; "
                    + HttpHeaderParameterEncoding.encode("filename", "jatos_results_data_"
                    + StringUtils.getDateTimeYyyyMMddHHmmss() + ".zip");
            Context.current().response().setHeader(Http.HeaderNames.CONTENT_DISPOSITION, cdHeader);
            return ok().chunked(dataSource).as("application/zip");
        }
    }

    /**
     * Returns all result files (not result data and not metadata) belonging to results in a zip. The results are
     * specified by IDs (can be any kind) in the request's body or as query parameters. Streaming is used to reduce
     * memory and disk usage.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result exportResultFiles(Http.Request request) {
        // The check if the signedin user is a member of the study or a superuser is done in the ResultStreamer
        Source<ByteString, ?> dataSource = resultStreamer.streamResults(request, ResultStreamer.ResultType.FILES_ONLY);
        String cdHeader = "attachment; "
                + HttpHeaderParameterEncoding.encode("filename", "jatos_results_files_"
                + StringUtils.getDateTimeYyyyMMddHHmmss() + ".zip");
        Context.current().response().setHeader(Http.HeaderNames.CONTENT_DISPOSITION, cdHeader);
        return ok().chunked(dataSource).as("application/zip");
    }

    /**
     * Exports a single result file.
     *
     * @param componentResultId ID of the component result that the file belongs to
     * @param filename          Filename of the file to be exported
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER}, types = {TOKEN, SESSION})
    public Result exportSingleResultFile(Long componentResultId, String filename) {
        ComponentResult componentResult = componentResultDao.findById(componentResultId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessComponentResult(componentResult, signedinUser, false);

        Study study = componentResult.getComponent().getStudy();
        Path file = ioUtils.getResultUploadFileSecurely(componentResult.getStudyResult().getId(), componentResultId, filename);
        if (!Files.exists(file)) throw new NotFoundException("File doesn't exist");
        studyLogger.log(study, signedinUser, "Exported single result file");
        return ok(file);
    }

    /**
     * Removes results from the database (ComponentResults and StudyResults) and result files from the file system.
     * Which results are to be removed are indicated by query parameters and/or JSON in the request's body. Different
     * IDs can be used, e.g. study ID (to delete all results of this study), component results (all of this component),
     * batch ID (all of this batch). Of course, component result IDs or study result IDs can be specified directly. It
     * primarily removes the ComponentResults since results are associated with them, but if in the process a
     * StudyResult becomes empty (no more ComponentResults), it will be deleted too.
     */
    @Async(Executor.IO)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result removeResults(Http.Request request) {
        List<Long> crids = componentResultIdsExtractor.extract(request.body().asJson());
        crids.addAll(componentResultIdsExtractor.extract(request.queryString()));

        // The check, that the user is a member of the study or a superuser, and that the study is not locked, is done
        // in the ResultRemover`
        resultRemover.removeComponentResults(crids, true);

        return ok(ApiEnvelope.wrap(crids).asJsonNode());
    }

}
