package controllers.gui;

import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import auth.gui.AuthApiToken;
import auth.gui.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.*;
import exceptions.gui.*;
import general.common.Common;
import general.common.RequestScope;
import general.common.StudyLogger;
import general.gui.StrictJsonMapper;
import models.common.*;
import models.gui.*;
import models.gui.ApiEnvelope.ErrorCode;
import org.apache.commons.lang3.tuple.Pair;
import play.Logger;
import play.core.utils.HttpHeaderParameterEncoding;
import play.db.jpa.Transactional;
import play.http.HttpEntity;
import play.libs.Json;
import play.mvc.*;
import scala.Option;
import services.gui.*;
import utils.common.DirectoryStructureToJson;
import utils.common.Helpers;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static auth.gui.AuthAction.Auth;
import static controllers.gui.actionannotations.ApiAccessLoggingAction.ApiAccessLogging;
import static models.common.User.Role.*;

/**
 * JATOS API Controller
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@ApiAccessLogging
@Singleton
public class Api extends Controller {

    private static final Logger.ALogger LOGGER = Logger.of(Api.class);

    private final Admin admin;
    private final AdminService adminService;
    private final AuthService authService;
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
    private final StudyLogger studyLogger;
    private final IOUtils ioUtils;
    private final UserService userService;
    private final ApiTokenService apiTokenService;
    private final StrictJsonMapper strictJsonMapper;

    @Inject
    Api(Admin admin, AdminService adminService, AuthService authService,
        ComponentResultIdsExtractor componentResultIdsExtractor,
        StudyDao studyDao, ComponentResultDao componentResultDao, UserDao userDao, ApiTokenDao apiTokenDao,
        BatchDao batchDao, StudyLinkDao studyLinkDao, GroupResultDao groupResultDao, StudyService studyService,
        ComponentService componentService, StudyLinkService studyLinkService, BatchService batchService,
        ImportExport importExport, ImportExportService importExportService,
        ResultRemover resultRemover, ResultStreamer resultStreamer, AuthorizationService authorizationService,
        JsonUtils jsonUtils, StudyLogger studyLogger, IOUtils ioUtils, UserService userService,
        ApiTokenService apiTokenService,
        StrictJsonMapper strictJsonMapper) {
        this.admin = admin;
        this.adminService = adminService;
        this.authService = authService;
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
        this.studyLogger = studyLogger;
        this.ioUtils = ioUtils;
        this.userService = userService;
        this.apiTokenService = apiTokenService;
        this.strictJsonMapper = strictJsonMapper;
    }

    /**
     * Returns metadata of the API token used in this request
     */
    @Auth({VIEWER, USER, ADMIN})
    public Result currentApiTokenMetadata() {
        Object token = RequestScope.get(AuthApiToken.API_TOKEN);
        return ok(ApiEnvelope.wrap(token).asJsonNode());
    }

    /**
     * Returns admin status information in JSON. Only with admin tokens.
     */
    @Transactional
    @Auth(ADMIN)
    public Result status() {
        JsonNode status = adminService.getAdminStatus();
        return ok(ApiEnvelope.wrap(status).asJsonNode());
    }

    /**
     * Returns a JATOS application log file. Only with admin tokens.
     *
     * @param filename Log's filename
     * @return Returns the log file
     */
    @Transactional
    @Auth(ADMIN)
    public Result logs(String filename) {
        return admin.logs(filename, -1, false);
    }

    /**
     * Get information about all users. Only with admin tokens.
     */
    @Transactional
    @Auth(ADMIN)
    public Result allUsers() throws IOException {
        List<User> userList = userDao.findAll();
        Map<String, List<Long>> studyIdsByUsername = userDao.findAllUsersAndTheirStudyIds();

        ArrayNode allUserData = Json.mapper().createArrayNode();
        for (User user : userList) {
            ObjectNode userNode = (ObjectNode) jsonUtils.asJsonForApi(user);
            List<Long> studyIds = studyIdsByUsername.getOrDefault(user.getUsername(), Collections.emptyList());
            userNode.putPOJO("studyIds", studyIds);
            allUserData.add(userNode);
        }

        return ok(ApiEnvelope.wrap(allUserData).asJsonNode());
    }

    /**
     * HEAD requests: checks if a user exists
     */
    @Transactional
    @Auth(ADMIN)
    public Result checkUserExists(Long id) {
        User user = userDao.findById(id);
        return user != null ? noContent() : notFound();
    }

    /**
     * Get info of a user.
     */
    @Transactional
    @Auth({VIEWER, USER, ADMIN})
    public Result getUser(Long id) throws HttpException, IOException {
        User user = userDao.findById(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkAdminOrSelf(signedinUser, user);

        JsonNode userNode = jsonUtils.asJsonForApi(user);
        return ok(ApiEnvelope.wrap(userNode).asJsonNode());
    }

    @Transactional
    @Auth(ADMIN)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result createUser(Http.Request request) throws HttpException, IOException, AuthException {
        JsonNode json = ApiService.getJsonFromBody(request);
        NewUserProperties props = strictJsonMapper.getMapper().treeToValue(json, NewUserProperties.class);
        ApiService.validateProps(props);
        authorizationService.checkAuthMethodIsDbOrLdap(props);

        User user = userService.registerUser(props);
        JsonNode userJson = jsonUtils.asJsonForApi(user);
        return created(ApiEnvelope.wrap(userJson).asJsonNode());
    }

    @Transactional
    @Auth({USER, ADMIN})
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateUser(Http.Request request, Long id) throws HttpException, IOException {
        User user = userDao.findById(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkAuthMethodIsDbOrLdap(user);
        authorizationService.checkAdminOrSelf(signedinUser, user);

        UserProperties props = userService.bindToProperties(user);
        JsonNode json = ApiService.getJsonFromBody(request);
        props = strictJsonMapper.updateFromJson(props, json);
        ApiService.validateProps(props);
        authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, user);

        userService.updateUser(user, props);

        JsonNode userNode = jsonUtils.asJsonForApi(user);
        return ok(ApiEnvelope.wrap(userNode).asJsonNode());
    }

    @Transactional
    @Auth({USER, ADMIN})
    public Result deleteUser(Long id) throws IOException, HttpException {
        User user = userDao.findById(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkAdminOrSelf(signedinUser, user);
        authorizationService.checkNotUserAdmin(user);

        userService.removeUser(user);

        return ok(ApiEnvelope.wrap("User deleted successfully").asJsonNode());
    }


    /**
     * Generate API tokens. It returns the token and the token metadata.
     */
    @Transactional
    @Auth({USER, ADMIN})
    @BodyParser.Of(BodyParser.Json.class)
    public Result generateApiToken(Http.Request request, Long userId) throws HttpException {
        if (!Common.isJatosApiTokensApiGenerationAllowed()) {
            throw new ForbiddenException("API token generation is not allowed", ErrorCode.CONFIG_ERROR);
        }

        User user = userDao.findById(userId);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkSignedinUserAllowedToAccessUser(user, signedinUser);

        JsonNode json = request.body().asJson();
        String name = ApiService.getFieldFromJson(json, "name", String.class);

        int expires = (int) Common.getJatosApiTokensApiGenerationExpiresAfter().getSeconds();

        Pair<ApiToken, String> apiTokenPair = apiTokenService.create(user, name, expires);
        ApiToken apiToken = apiTokenPair.getLeft();
        String apiTokenStr = apiTokenPair.getRight();

        ObjectNode tokenJson = JsonUtils.asObjectNode(apiToken);
        tokenJson.put("token", apiTokenStr);
        return created(ApiEnvelope.wrap(tokenJson).asJsonNode());
    }

    /**
     * List the metadata of all tokens that belong to a user.
     */
    @Transactional
    @Auth({VIEWER, USER, ADMIN})
    public Result allApiTokenMetadataByUser(Long userId) throws HttpException {
        User user = userDao.findById(userId);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkAdminOrSelf(signedinUser, user);

        ArrayNode tokens = Json.mapper().createArrayNode();
        apiTokenDao.findByUser(user).forEach(token -> tokens.add(JsonUtils.asJsonNode(token)));
        return ok(ApiEnvelope.wrap(tokens).asJsonNode());
    }

    /**
     * Get the metadata of an API token specified by its ID.
     */
    @Transactional
    @Auth({VIEWER, USER, ADMIN})
    public Result apiTokenMetadata(Long id) throws HttpException {
        ApiToken apiToken = apiTokenDao.find(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkAdminOrSelf(signedinUser, apiToken.getUser());

        return ok(ApiEnvelope.wrap(apiToken).asJsonNode());
    }

    /**
     * Activate or deactivate a token specified by its ID. Admins can update tokens of non-admin users. Users (including
     * admins) can update their own tokens.
     */
    @Transactional
    @Auth({USER, ADMIN})
    @BodyParser.Of(BodyParser.Json.class)
    public Result toggleApiTokenActive(Http.Request request, Long id) throws HttpException {
        ApiToken token = apiTokenDao.find(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkUserAllowedToAccessApiToken(token, signedinUser);

        JsonNode json = request.body().asJson();
        boolean active = ApiService.getActiveFlagFromJson(json);
        token.setActive(active);
        apiTokenDao.update(token);

        ObjectNode responseJson = Json.mapper().createObjectNode();
        responseJson.put("id", token.getId());
        responseJson.put("active", token.isActive());
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

    /**
     * Admins can delete tokens of non-admin users. Users (including admins) can delete their own tokens.
     */
    @Transactional
    @Auth({USER, ADMIN})
    public Result deleteApiToken(Long id) throws NotFoundException, ForbiddenException {
        ApiToken token = apiTokenDao.find(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkUserAllowedToAccessApiToken(token, signedinUser);

        apiTokenDao.remove(token);

        return ok(ApiEnvelope.wrap("Token deleted successfully").asJsonNode());
    }

    /**
     * HEAD requests: checks if a study exists in the system by its ID or UUID. Only with admin tokens.
     */
    @Transactional
    @Auth(ADMIN)
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
    @Transactional
    @Auth({VIEWER, USER})
    public Result getAllStudyPropertiesOfSignedinUser(Boolean withComponentProperties, Boolean withBatchProperties)
            throws IOException {
        User signedinUser = authService.getSignedinUser();
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
    @Transactional
    @Auth(USER)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result createStudy(Http.Request request) throws IOException, HttpException {
        User signedinUser = authService.getSignedinUser();
        JsonNode json = ApiService.getJsonFromBody(request);
        return createStudyFromJson(signedinUser, json, false);
    }

    /**
     * Dispatches the request based on the {@code Content-Type} header: 1) ZIP / multipart upload → import a study
     * archive 2) JSON → create a new study from the request body.
     *
     * This endpoint supports multiple, mutually exclusive body formats, so it cannot be handled by a single fixed
     * {@link play.mvc.BodyParser} annotation. Body parsing is therefore performed explicitly in the respective
     * branches.
     */
    @Transactional
    @Auth(USER)
    public Result importOrCreateStudy(Http.Request request, boolean keepProperties, boolean keepAssets,
                                      boolean keepCurrentAssetsName, boolean renameAssets)
            throws HttpException, IOException, ValidationException, ImportExportException {
        String contentType = request.getHeaders().get(Http.HeaderNames.CONTENT_TYPE).orElse("");

        if (contentType.startsWith("multipart/form-data")
                || contentType.startsWith("application/zip")
                || contentType.startsWith("application/jzip")
                || contentType.startsWith("application/octet-stream")
                || contentType.isEmpty()) {
            return importStudy(request, keepProperties, keepAssets, keepCurrentAssetsName, renameAssets);
        }

        if (contentType.startsWith("application/json")) {
            User signedinUser = authService.getSignedinUser();
            JsonNode json = request.body().asJson();
            if (json == null) {
                throw new BadRequestException("Request body is empty or not valid JSON", ErrorCode.INVALID_JSON);
            }
            return createStudyFromJson(signedinUser, json, renameAssets);
        }

        return status(415, ApiEnvelope.wrap("Wrong 'Content-Type' " + contentType,
                ErrorCode.WRONG_CONTENT_TYPE).asJsonNode());
    }

    private Result createStudyFromJson(User signedinUser, JsonNode json, boolean renameAssets)
            throws IOException, HttpException {
        ObjectNode jsonObj = ApiService.normalizeJsonInputField(json, "studyInput");
        StudyProperties props = strictJsonMapper.getMapper().treeToValue(jsonObj, StudyProperties.class);
        ApiService.validateProps(props);

        Study study = studyService.createAndPersistStudyAndAssetsDir(signedinUser, props, renameAssets);

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
    @Transactional
    @Auth(USER)
    public Result importStudy(Http.Request request, boolean keepProperties, boolean keepAssets,
                              boolean keepCurrentAssetsName, boolean renameAssets)
            throws HttpException, IOException, ValidationException, ImportExportException {
        User signedinUser = authService.getSignedinUser();

        List<String> allowedContentTypes = Arrays.asList("application/zip", "application/jzip", "application/octet-stream");
        File file = ApiService.extractFile(request, Study.STUDY, allowedContentTypes);

        try {
            Map<String, Object> importInfo = importExportService.importStudy(signedinUser, file);

            Study study = importExportService.importStudyConfirmed(
                    signedinUser, keepProperties, keepAssets, keepCurrentAssetsName, renameAssets);

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
    @Transactional
    @Auth(USER)
    public Result exportStudy(String id) throws HttpException {
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
    @Transactional
    @Auth({VIEWER, USER})
    public Result getStudyProperties(String id, Boolean withComponentProperties, Boolean withBatchProperties)
            throws IOException, HttpException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, signedinUser);

        JsonNode studiesNode = jsonUtils.studyAsJsonForApi(study, withComponentProperties, withBatchProperties);
        return ok(ApiEnvelope.wrap(studiesNode).asJsonNode());
    }

    /**
     * Updates the study properties. Regular users may update only studies they own. Admins may update only the
     * activation status of any study.
     */
    @Transactional
    @Auth(USER)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateStudyProperties(Http.Request request, String id) throws IOException, HttpException {
        User signedinUser = authService.getSignedinUser();
        Study study = studyService.getStudyFromIdOrUuid(id);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        boolean isMemberOrSuperuser = study.hasUser(signedinUser) || Helpers.isAllowedSuperuser(signedinUser);
        boolean isAdminNonMember = signedinUser.isAdmin() && !isMemberOrSuperuser;

        JsonNode json = ApiService.getJsonFromBody(request);
        ObjectNode jsonObj = ApiService.normalizeJsonInputField(json, "studyInput");

        // Admins who are not members: only allow toggling "active"
        if (isAdminNonMember) {
            boolean active = ApiService.getActiveFlagFromJson(jsonObj);
            study.setActive(active);
            studyDao.update(study);

            ObjectNode responseJson = Json.mapper().createObjectNode();
            responseJson.put("id", study.getId());
            responseJson.put("uuid", study.getUuid());
            responseJson.put("active", study.isActive());
            return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
        }

        authorizationService.canUserAccessStudy(study, signedinUser, true);

        StudyProperties props = studyService.bindToProperties(study);
        props = strictJsonMapper.updateFromJson(props, jsonObj);
        ApiService.validateProps(props);

        studyService.updateStudyAndRenameAssets(study, props, signedinUser);

        JsonNode studyNode = jsonUtils.studyAsJsonForApi(study, false, false);
        return ok(ApiEnvelope.wrap(studyNode).asJsonNode());
    }

    @Transactional
    @Auth(USER)
    public Result deleteStudy(String id) throws HttpException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user, true);

        studyService.removeStudyInclAssets(study, user);
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
    @Transactional
    @Auth(USER)
    public Result getStudyAssetsStructure(String id, boolean flatten) throws IOException, HttpException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user);

        File base;
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
    @Transactional
    @Auth(USER)
    public Result downloadStudyAssetsFile(String id, String filepath) throws HttpException {
        filepath = Helpers.urlDecode(filepath);
        if (filepath.startsWith("/")) filepath = filepath.substring(1);

        Study study = studyService.getStudyFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user);

        File file;
        try {
            file = ioUtils.getFileInStudyAssetsDir(study.getDirName(), filepath);
            if (!file.isFile()) throw new IOException();
        } catch (IOException e) {
            throw new NotFoundException("File '" + filepath + "' couldn't be found.");
        }
        return ok()
                .sendFile(file)
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"");
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
    @Transactional
    @Auth(USER)
    public Result uploadStudyAssetsFile(Http.Request request, String id, String filepath) throws HttpException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user, true);

        List<String> allowedContentTypes = Collections.singletonList("application/octet-stream");
        File uploadedFile = ApiService.extractFile(request, "studyAssetsFile", allowedContentTypes);

        try {
            Path assetsFilePath = ioUtils.getAssetsFilePath(filepath, uploadedFile.getName(), study);
            Path parent = assetsFilePath.getParent();
            if (parent != null) {
                // Make sure the directory that will contain the uploaded file exists.
                Files.createDirectories(parent);
            }

            boolean overwritten = IOUtils.moveAndDetectOverwrite(uploadedFile.toPath(), assetsFilePath);

            String msg = overwritten ? "File overwritten successfully" : "File uploaded successfully";
            JsonNode envelope = ApiEnvelope.wrap(msg).asJsonNode();
            return overwritten ? ok(envelope) : created(envelope);
        } catch (IOException e) {
            LOGGER.info(".uploadStudyAssetsFile: " + e.getLocalizedMessage());
            throw new InternalServerErrorException("Error writing file");
        }
    }

    /**
     * Deletes a file in the study assets directory.
     *
     * @param id       Study's ID or UUID
     * @param filepath Path to the file in the study assets directory that is supposed to be deleted. The path can be
     *                 URL encoded but doesn't have to be. Directories cannot be deleted.
     */
    @Transactional
    @Auth(USER)
    public Result deleteStudyAssetsFile(String id, String filepath) throws HttpException {
        filepath = Helpers.urlDecode(filepath);
        if (filepath.startsWith("/")) filepath = filepath.substring(1);

        Study study = studyService.getStudyFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user, true);

        try {
            File file = ioUtils.getFileInStudyAssetsDir(study.getDirName(), filepath);
            if (file.isDirectory()) throw new IOException("Directories can't be deleted.");
            Files.delete(file.toPath());
        } catch (NoSuchFileException e) {
            throw new NotFoundException("File '" + filepath + "' couldn't be found.");
        } catch (IOException e) {
            LOGGER.info(".deleteStudyAssetsFile: " + e.getLocalizedMessage());
            throw new InternalServerErrorException("Error writing file");
        }
        return ok(ApiEnvelope.wrap("File deleted successfully").asJsonNode());
    }

    /**
     * Get user IDs of all members of a study.
     */
    @Transactional
    @Auth({VIEWER, USER})
    public Result allMembersOfStudy(String id) throws HttpException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, signedinUser);

        List<Long> userIds = studyDao.findAllMembersByStudyId(study.getId());

        ObjectNode responseJson = Json.mapper().createObjectNode();
        responseJson.put("id", study.getId());
        responseJson.put("uuid", study.getUuid());
        responseJson.putPOJO("members", userIds);
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

    @Transactional
    @Auth(USER)
    public Result addMemberToStudy(String id, Long userId) throws HttpException {
        return changeMemberOfStudy(id, userId, true);
    }

    @Transactional
    @Auth(USER)
    public Result removeMemberFromStudy(String id, Long userId) throws HttpException {
        return changeMemberOfStudy(id, userId, false);
    }

    public Result changeMemberOfStudy(String id, Long userId, boolean isMember) throws HttpException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = authService.getSignedinUser();
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
    @Transactional
    @Auth({VIEWER, USER})
    public Result studyLog(String id, int entryLimit, boolean download) throws HttpException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, signedinUser);

        if (download) {
            Path studyLogPath = Paths.get(studyLogger.getPath(study));
            if (Files.notExists(studyLogPath)) throw new NotFoundException("Study log file doesn't exist");

            Source<ByteString, ?> source = FileIO.fromPath(studyLogPath);
            Optional<Long> contentLength = Optional.of(studyLogPath.toFile().length());
            String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_studylog_" + studyLogger.getFilename(study));
            return new Result(new ResponseHeader(200, Collections.emptyMap()),
                    new HttpEntity.Streamed(source, contentLength, Optional.of("application/octet-stream")))
                    .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
        } else {
            return ok().chunked(studyLogger.readLogFile(study, entryLimit)).as("application/x-ndjson");
        }
    }

    /**
     * Creates a component within the specified study
     */
    @Transactional
    @Auth(USER)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result createComponent(Http.Request request, String studyId) throws IOException, HttpException {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user, true);

        JsonNode json = ApiService.getJsonFromBody(request);
        ObjectNode jsonObj = ApiService.normalizeJsonInputField(json, "componentInput");
        ComponentProperties props = strictJsonMapper.getMapper().treeToValue(jsonObj, ComponentProperties.class);
        ApiService.validateProps(props);

        Component component = componentService.createAndPersistComponent(study, props);

        JsonNode componentNode = jsonUtils.componentAsJsonForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Transactional
    @Auth({VIEWER, USER})
    public Result getComponentsByStudy(String studyIdOrUuid) throws HttpException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(studyIdOrUuid);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user);

        ArrayNode componentArray = Json.mapper().createArrayNode();
        for (Component c : study.getComponentList()) {
            componentArray.add(jsonUtils.componentAsJsonForApi(c));
        }
        return ok(ApiEnvelope.wrap(componentArray).asJsonNode());
    }

    @Transactional
    @Auth({VIEWER, USER})
    public Result getComponent(String id) throws HttpException, IOException {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessComponent(component, user);

        JsonNode componentNode = jsonUtils.componentAsJsonForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Transactional
    @Auth(USER)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateComponent(Http.Request request, String id) throws HttpException, IOException {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessComponent(component, user, true);

        JsonNode json = ApiService.getJsonFromBody(request);
        ObjectNode jsonObj = ApiService.normalizeJsonInputField(json, "componentInput");
        ComponentProperties props = componentService.bindToProperties(component);
        props = strictJsonMapper.updateFromJson(props, jsonObj);
        ApiService.validateProps(props);

        componentService.renameHtmlFilePath(component, props.getHtmlFilePath(), props.isHtmlFileRename());
        componentService.updateComponentAfterEdit(component, props);

        JsonNode componentNode = jsonUtils.componentAsJsonForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Transactional
    @Auth(USER)
    public Result deleteComponent(String id) throws HttpException {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessComponent(component, user, true);
        componentService.remove(component, user);
        return ok(ApiEnvelope.wrap("Component deleted successfully").asJsonNode());
    }

    @Transactional
@Auth({VIEWER, USER})
    public Result getBatchesByStudy(String studyId) throws HttpException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user);

        ArrayNode batchArray = Json.mapper().createArrayNode();
        for (Batch b : study.getBatchList()) {
            batchArray.add(jsonUtils.batchAsJsonForApi(b));
        }
        return ok(ApiEnvelope.wrap(batchArray).asJsonNode());
    }

    @Transactional
    @Auth({VIEWER, USER})
    public Result getBatch(String id) throws HttpException, IOException {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessBatch(batch, user);

        JsonNode batchNode = jsonUtils.batchAsJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Transactional
    @Auth(USER)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result createBatch(Http.Request request, String studyId) throws HttpException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user, true);

        JsonNode json = ApiService.getJsonFromBody(request);
        ObjectNode jsonObj = ApiService.normalizeJsonInputField(json, "batchInput");
        BatchProperties props = strictJsonMapper.getMapper().treeToValue(jsonObj, BatchProperties.class);
        ApiService.validateProps(props);

        Batch batch = batchService.bindToBatch(props);
        batchService.initAndPersistBatch(batch, study, user);

        JsonNode batchNode = jsonUtils.batchAsJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Transactional
    @Auth(USER)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateBatch(Http.Request request, String id) throws HttpException, IOException {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessBatch(batch, user, true);

        BatchProperties props = batchService.bindToProperties(batch);
        JsonNode json = ApiService.getJsonFromBody(request);
        ObjectNode jsonObj = ApiService.normalizeJsonInputField(json, "batchInput");
        props = strictJsonMapper.updateFromJson(props, jsonObj);
        ApiService.validateProps(props);

        batchService.updateBatch(batch, props);
        batchDao.update(batch);

        JsonNode batchNode = jsonUtils.batchAsJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Transactional
    @Auth(USER)
    public Result deleteBatch(String id) throws HttpException {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessBatch(batch, user, true);
        batchService.remove(batch, user);
        return ok(ApiEnvelope.wrap("Batch deleted successfully").asJsonNode());
    }

    @Transactional
    @Auth({VIEWER, USER})
    public Result getBatchSession(String id, boolean asText) throws HttpException, IOException {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessBatch(batch, user, true);

        ObjectNode session = ApiService.getSessionNode(batch.getBatchSessionData(), batch.getBatchSessionVersion(), asText);
        return ok(ApiEnvelope.wrap(session).asJsonNode());
    }

    /**
     * Updates the batch session. Uses `BodyParser.Raw` to handle JSON payloads with potential malformed content
     * gracefully and give detailed error messages to the user.
     */
    @Transactional
    @Auth(USER)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateBatchSession(Http.Request request, String id, Option<Long> version) throws HttpException, IOException {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessBatch(batch, user);

        String sessionData = ApiService.getSessionDataFromBody(request);

        Long currentVersion = version.getOrElse(batch::getBatchSessionVersion);
        Long newVersion = batchDao.updateBatchSession(batch.getId(), currentVersion, sessionData);
        if (newVersion == null) {
            throw new ForbiddenException("Batch session version conflict");
        }

        ObjectNode data = Json.newObject().put("version", newVersion);
        return ok(ApiEnvelope.wrap("Batch session updated successfully", data).asJsonNode());
    }

    @Transactional
    @Auth({VIEWER, USER})
    public Result getGroupsOfBatch(String id) throws HttpException {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessBatch(batch, user);

        List<GroupResult> groups = groupResultDao.findAllByBatch(batch);

        JsonNode groupArray = jsonUtils.allGroupResults(groups);
        return ok(ApiEnvelope.wrap(groupArray).asJsonNode());
    }

    @Transactional
    @Auth({VIEWER, USER})
    public Result getGroupSession(Long id, boolean asText) throws HttpException, IOException {
        GroupResult groupResult = groupResultDao.findById(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessGroupResult(groupResult, user);

        ObjectNode session = ApiService.getSessionNode(groupResult.getGroupSessionData(), groupResult.getGroupSessionVersion(), asText);
        return ok(ApiEnvelope.wrap(session).asJsonNode());
    }

    /**
     * Updates the group session. Uses `BodyParser.Raw` to handle JSON payloads with potential malformed content
     * gracefully and give detailed error messages to the user.
     */
    @Transactional
    @Auth(USER)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result updateGroupSession(Http.Request request, Long id, Option<Long> version) throws HttpException, IOException {
        GroupResult groupResult = groupResultDao.findById(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessGroupResult(groupResult, user);

        String sessionData = ApiService.getSessionDataFromBody(request);

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
    @Transactional
    @Auth(USER)
    public Result getOrGenerateStudyCodes(Http.Request request, String studyId, Option<Long> batchIdOption, String type,
                                          String comment, Option<Integer> amountOption) throws HttpException {
        // Get props either from query parameters or JSON body
        JsonNode json = request.body().asJson();
        Long batchId = batchIdOption.nonEmpty()
                ? batchIdOption.get()
                : ApiService.getFieldFromJson(json, "batchId", Long.class, null);
        type = type != null
                ? type
                : ApiService.getFieldFromJson(json, "type", String.class, null);
        comment = comment != null
                ? comment
                : ApiService.getFieldFromJson(json, "comment", String.class, null);
        int amount = amountOption.nonEmpty()
                ? amountOption.get()
                : ApiService.getFieldFromJson(json, "amount", Integer.class, 1);

        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user);

        Batch batch = batchService.getBatchOrDefaultBatch(batchId, study);
        authorizationService.canUserAccessBatch(batch, user);

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType(WorkerService.validateAndExtractWorkerType(type));
        props.setComment(comment);
        props.setAmount(amount);
        ApiService.validateProps(props);

        List<String> studyCodeList = studyLinkService.getStudyCodes(batch, props);
        return ok(ApiEnvelope.wrap(studyCodeList).asJsonNode());
    }

    @Transactional
    @Auth({VIEWER, USER})
    public Result getStudyCode(String code) throws HttpException {
        StudyLink studyLink = studyLinkDao.findByStudyCode(code);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudyLink(studyLink, user);

        JsonNode linkNode = jsonUtils.getStudyLinkData(studyLink);
        return ok(ApiEnvelope.wrap(linkNode).asJsonNode());
    }

    @Transactional
    @Auth(USER)
    @BodyParser.Of(BodyParser.Json.class)
    public Result toggleStudyCodeActive(Http.Request request, String code) throws HttpException {
        StudyLink studyLink = studyLinkDao.findByStudyCode(code);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudyLink(studyLink, user);

        JsonNode json = request.body().asJson();
        boolean active = ApiService.getActiveFlagFromJson(json);
        studyLink.setActive(active);
        studyLinkDao.update(studyLink);

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
    @Transactional
    @Auth({VIEWER, USER})
    public Result exportResults(Http.Request request, Boolean isApiCall) throws BadRequestException {
        Map<String, Object> wrapperObject = isApiCall
                ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
                : Collections.emptyMap();

        // The check if the signedin user is a member of the study or a superuser is done in the ResultStreamer
        Source<ByteString, ?> dataSource = resultStreamer.streamResults(request, ResultStreamer.ResultType.COMBINED,
                wrapperObject);

        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_"
                + Helpers.getDateTimeYyyyMMddHHmmss() + "." + Common.getResultsArchiveSuffix());
        return ok().chunked(dataSource).as("application/zip")
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
    }

    /**
     * Returns all result's metadata (but not result files and not metadata) in a zip file. The results are specified by
     * IDs (can be any kind) in the request's body or query parameters. Streaming is used to reduce memory and disk
     * usage.
     *
     * @param isApiCall If true, the response JSON gets an additional 'apiVersion' field
     * @param download  If true, the response JSON is in a file, otherwise in the response body
     */
    @Transactional
    @Auth({VIEWER, USER})
    public Result exportResultMetadata(Http.Request request, boolean download, Boolean isApiCall) throws HttpException, IOException {
        Map<String, Object> wrapperObject = isApiCall
                ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
                : Collections.emptyMap();

        // The check if the signedin user is a member of the study or a superuser is done in the ResultStreamer
        File file = resultStreamer.writeResultMetadata(request, wrapperObject);

        //noinspection ResultOfMethodCallIgnored
        Result result = ok().streamed(
                Helpers.okFileStreamed(file, file::delete),
                Optional.of(file.length()),
                Optional.of("application/json"));
        if (download) {
            String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_metadata_"
                    + Helpers.getDateTimeYyyyMMddHHmmss() + ".json");
            result = result.withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
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
    @Transactional
    @Auth({VIEWER, USER})
    public Result exportResultData(Http.Request request, boolean asPlainText, boolean download, boolean isApiCall)
            throws HttpException {
        // The check if the signedin user is a member of the study or a superuser is done in the ResultStreamer
        if (asPlainText) {
            Source<ByteString, ?> dataSource = resultStreamer.streamComponentResultData(request);
            Result result = ok().chunked(dataSource).as("text/plain; charset=UTF-8");
            if (download) {
                String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_data_"
                        + Helpers.getDateTimeYyyyMMddHHmmss() + ".txt");
                result = result.withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
            }
            return result;
        } else {
            Map<String, Object> wrapperObject = isApiCall
                    ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
                    : Collections.emptyMap();
            Source<ByteString, ?> dataSource = resultStreamer.streamResults(request, ResultStreamer.ResultType.DATA_ONLY,
                    wrapperObject);
            String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_data_"
                    + Helpers.getDateTimeYyyyMMddHHmmss() + ".zip");
            return ok().chunked(dataSource).as("application/zip")
                    .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
        }
    }

    /**
     * Returns all result files (not result data and not metadata) belonging to results in a zip. The results are
     * specified by IDs (can be any kind) in the request's body or as query parameters. Streaming is used to reduce
     * memory and disk usage.
     */
    @Transactional
    @Auth({VIEWER, USER})
    public Result exportResultFiles(Http.Request request) throws BadRequestException {
        // The check if the signedin user is a member of the study or a superuser is done in the ResultStreamer
        Source<ByteString, ?> dataSource = resultStreamer.streamResults(request, ResultStreamer.ResultType.FILES_ONLY);
        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_files_"
                + Helpers.getDateTimeYyyyMMddHHmmss() + ".zip");
        return ok().chunked(dataSource).as("application/zip")
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
    }

    /**
     * Exports a single result file.
     *
     * @param componentResultId ID of the component result that the file belongs to
     * @param filename          Filename of the file to be exported
     */
    @Transactional
    @Auth({VIEWER, USER})
    public Result exportSingleResultFile(Long componentResultId, String filename) throws HttpException {
        ComponentResult componentResult = componentResultDao.findById(componentResultId);
        User signedinUser = authService.getSignedinUser();
        authorizationService.canUserAccessComponentResult(componentResult, signedinUser, false);

        File file;
        try {
            Study study = componentResult.getComponent().getStudy();
            file = ioUtils.getResultUploadFileSecurely(componentResult.getStudyResult().getId(), componentResultId, filename);
            if (!file.exists()) throw new IOException();
            studyLogger.log(study, signedinUser, "Exported single result file");
        } catch (IOException e) {
            throw new NotFoundException("File does not exist");
        }
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
    @Transactional
    @Auth(USER)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result removeResults(Http.Request request) throws HttpException, IOException {
        JsonNode json = ApiService.getJsonFromBody(request);
        List<Long> crids = componentResultIdsExtractor.extract(json);
        crids.addAll(componentResultIdsExtractor.extract(request.queryString()));

        // The check, that the user is a member of the study or a superuser, and that the study is not locked, is done
        // in the ResultRemover`
        User signedinUser = authService.getSignedinUser();
        resultRemover.removeComponentResults(crids, signedinUser, true);

        return ok(ApiEnvelope.wrap(crids).asJsonNode());
    }

}
