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
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static models.common.User.Role.ADMIN;

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
    private final StudyService studyService;
    private final ComponentService componentService;
    private final StudyLinkService studyLinkService;
    private final BatchService batchService;
    private final ImportExport importExport;
    private final ResultRemover resultRemover;
    private final ResultStreamer resultStreamer;
    private final AuthorizationService authorizationService;
    private final JsonUtils jsonUtils;
    private final StudyLogger studyLogger;
    private final IOUtils ioUtils;
    private final UserService userService;
    private final ApiTokenService apiTokenService;
    private final WorkerService workerService;
    private final StrictJsonMapper strictJsonMapper;

    @Inject
    Api(Admin admin, AdminService adminService, AuthService authService,
        ComponentResultIdsExtractor componentResultIdsExtractor,
        StudyDao studyDao, ComponentResultDao componentResultDao, UserDao userDao, ApiTokenDao apiTokenDao,
        BatchDao batchDao, StudyLinkDao studyLinkDao, StudyService studyService, ComponentService componentService,
        StudyLinkService studyLinkService, BatchService batchService, ImportExport importExport, ResultRemover resultRemover,
        ResultStreamer resultStreamer, AuthorizationService authorizationService, JsonUtils jsonUtils,
        StudyLogger studyLogger, IOUtils ioUtils, UserService userService, ApiTokenService apiTokenService,
        WorkerService workerService, StrictJsonMapper strictJsonMapper) {
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
        this.studyService = studyService;
        this.componentService = componentService;
        this.studyLinkService = studyLinkService;
        this.batchService = batchService;
        this.importExport = importExport;
        this.resultRemover = resultRemover;
        this.resultStreamer = resultStreamer;
        this.authorizationService = authorizationService;
        this.jsonUtils = jsonUtils;
        this.studyLogger = studyLogger;
        this.ioUtils = ioUtils;
        this.userService = userService;
        this.apiTokenService = apiTokenService;
        this.workerService = workerService;
        this.strictJsonMapper = strictJsonMapper;
    }

    /**
     * Returns metadata of the API token used in this request
     */
    @Auth
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
    @Auth
    public Result getUser(Long id) throws HttpException, IOException {
        User user = userDao.findById(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkAdminOrSelf(signedinUser, user);

        JsonNode userNode = jsonUtils.asJsonForApi(user);
        return ok(ApiEnvelope.wrap(userNode).asJsonNode());
    }

    @Transactional
    @Auth(ADMIN)
    @BodyParser.Of(BodyParser.Json.class)
    public Result createUser(Http.Request request) throws HttpException, IOException, AuthException {
        JsonNode json = request.body().asJson();
        NewUserProperties props = strictJsonMapper.getMapper().treeToValue(json, NewUserProperties.class);
        ApiService.validateProps(props);
        authorizationService.checkAuthMethodIsDbOrLdap(props);

        User user = userService.registerUser(props);
        JsonNode userJson = jsonUtils.asJsonForApi(user);
        return created(ApiEnvelope.wrap(userJson).asJsonNode());
    }

    @Transactional
    @Auth
    @BodyParser.Of(BodyParser.Json.class)
    public Result updateUser(Http.Request request, Long id) throws HttpException, IOException {
        User user = userDao.findById(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkAuthMethodIsDbOrLdap(user);
        authorizationService.checkAdminOrSelf(signedinUser, user);

        UserProperties props = userService.bindToProperties(user);
        JsonNode json = request.body().asJson();
        strictJsonMapper.updateFromJson(props, json);
        ApiService.validateProps(props);

        authorizationService.checkSignedinUserAllowedToChangeUser(props, signedinUser, user);

        userService.updateUser(user, props);

        JsonNode userNode = jsonUtils.asJsonForApi(user);
        return ok(ApiEnvelope.wrap(userNode).asJsonNode());
    }

    @Transactional
    @Auth
    public Result deleteUser(Long id) throws IOException, HttpException {
        User user = userDao.findById(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkAdminOrSelf(signedinUser, user);
        authorizationService.checkNotUserAdmin(user);

        userService.removeUser(user);

        ObjectNode response = Json.mapper().createObjectNode()
            .put("message", "User deleted successfully");
        return ok(ApiEnvelope.wrap(response).asJsonNode());
    }


    /**
     * Generate API tokens. It returns the token and the token metadata.
     */
    @Transactional
    @Auth
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

        ObjectNode responseJson = JsonUtils.asObjectNode(apiToken);
        responseJson.put("token", apiTokenStr);
        return created(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

    /**
     * List the metadata of all tokens that belong to a user.
     */
    @Transactional
    @Auth
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
    @Auth
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
    @Auth
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
        responseJson.put("username", token.getUser().getUsername());
        responseJson.put("active", token.isActive());
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

    /**
     * Admins can delete tokens of non-admin users. Users (including admins) can delete their own tokens.
     */
    @Transactional
    @Auth
    public Result deleteApiToken(Long id) throws NotFoundException, ForbiddenException {
        ApiToken token = apiTokenDao.find(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.checkUserAllowedToAccessApiToken(token, signedinUser);

        apiTokenDao.remove(token);

        ObjectNode responseJson = Json.mapper().createObjectNode();
        responseJson.put("message", "Token deleted successfully");
        responseJson.put("id", id);
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
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
    @Auth
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

    @Transactional
    @Auth
    @BodyParser.Of(BodyParser.Json.class)
    public Result createStudy(Http.Request request) throws IOException, BadRequestException {
        User signedinUser = authService.getSignedinUser();

        JsonNode json = request.body().asJson();
        StudyProperties props = strictJsonMapper.getMapper().treeToValue(json, StudyProperties.class);
        ApiService.validateProps(props);

        Study study = studyService.createAndPersistStudy(signedinUser, props);
        ioUtils.createStudyAssetsDir(study.getUuid());

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
    @Auth
    public Result importStudy(Http.Request request, boolean keepProperties, boolean keepAssets,
                              boolean keepCurrentAssetsName, boolean renameAssets) throws HttpException, IOException {
        Study study = importExport.importStudyApi(request, keepProperties, keepAssets, keepCurrentAssetsName, renameAssets);
        JsonNode studyNode = jsonUtils.studyAsJsonForApi(study, false, false);
        return created(ApiEnvelope.wrap(studyNode).asJsonNode());
    }

    /**
     * This method calls either importStudy or createStudy according to the 'Accept' header
     */
    @Transactional
    @Auth
    public Result importOrCreateStudy(Http.Request request, boolean keepProperties, boolean keepAssets,
                                      boolean keepCurrentAssetsName, boolean renameAssets)
        throws HttpException, IOException {
        boolean acceptsMissing = request.getHeaders().get(Http.HeaderNames.ACCEPT).isEmpty();
        if (request.accepts("application/zip") || acceptsMissing) {
            return importStudy(request, keepProperties, keepAssets, keepCurrentAssetsName, renameAssets);
        }
        if (request.accepts("application/json")) {
            return createStudy(request);
        }
        return status(406, ApiEnvelope.wrap("Wrong 'Access' header", ErrorCode.NOT_ACCEPTABLE).asJsonNode());
    }

    /**
     * Returns the study archive (.jzip) as a file
     */
    @Transactional
    @Auth
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
    @Auth
    public Result getStudyProperties(String id, Boolean withComponentProperties, Boolean withBatchProperties)
        throws IOException, HttpException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, signedinUser);

        JsonNode studiesNode = jsonUtils.studyAsJsonForApi(study, withComponentProperties, withBatchProperties);
        return ok(ApiEnvelope.wrap(studiesNode).asJsonNode());
    }

    /**
     * Update study properties. A user can update its own studies. A superuser can update all studies. An admin can
     * activate/deactivate any study.
     */
    @Transactional
    @Auth
    @BodyParser.Of(BodyParser.Json.class)
    public Result updateStudyProperties(Http.Request request, String id) throws IOException, HttpException {
        User signedinUser = authService.getSignedinUser();
        Study study = studyService.getStudyFromIdOrUuid(id);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        boolean isMemberOrSuperuser = study.hasUser(signedinUser) || Helpers.isAllowedSuperuser(signedinUser);
        boolean isAdminNonMember = signedinUser.isAdmin() && !isMemberOrSuperuser;

        JsonNode json = request.body().asJson();

        // Admins who are not members: only allow toggling "active"
        if (isAdminNonMember) {
            boolean active = ApiService.getActiveFlagFromJson(json);
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
        strictJsonMapper.updateFromJson(props, json);
        ApiService.validateProps(props);

        studyService.renameStudyAssetsDir(study, props.getDirName());
        studyService.updateStudy(study, props, signedinUser);

        JsonNode studyNode = jsonUtils.studyAsJsonForApi(study, false, false);
        return ok(ApiEnvelope.wrap(studyNode).asJsonNode());
    }

    @Transactional
    @Auth
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
    @Auth
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
    @Auth
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
        return ok().sendFile(file);
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
    @Auth
    public Result uploadStudyAssetsFile(Http.Request request, String id, String filepath) throws HttpException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user, true);

        if (request.body().asMultipartFormData() == null) {
            throw new BadRequestException("File missing", ErrorCode.NOT_FOUND);
        }
        Http.MultipartFormData.FilePart<Object> filePart = request.body().asMultipartFormData().getFile("studyAssetsFile");
        if (filePart == null) throw new BadRequestException("File missing", ErrorCode.NOT_FOUND);
        File uploadedFile = (File) filePart.getFile();

        try {
            Path assetsFilePath = ioUtils.getAssetsFilePath(filepath, filePart.getFilename(), study);
            if (Files.notExists(assetsFilePath)) Files.createDirectories(assetsFilePath);
            Files.move(uploadedFile.toPath(), assetsFilePath, REPLACE_EXISTING);
            return created(ApiEnvelope.wrap("File uploaded successfully").asJsonNode());
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
    @Auth
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
    @Auth
    public Result allMembersOfStudy(String id) throws HttpException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, signedinUser);

        List<Long> userIds = studyDao.findAllMembersByStudyId(study.getId());

        ObjectNode responseJson = Json.mapper().createObjectNode();
        responseJson.put("studyId", study.getId());
        responseJson.put("studyUuid", study.getUuid());
        responseJson.putPOJO("members", userIds);
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

    @Transactional
    @Auth
    public Result addMemberToStudy(String id, Long userId) throws HttpException {
        return changeMemberOfStudy(id, userId, true);
    }

    @Transactional
    @Auth
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
        responseJson.put("studyId", study.getId());
        responseJson.putPOJO("members", userIds);
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

    /**
     * Returns a study log.
     *
     * @param id         Study's ID or UUID
     * @param entryLimit It cuts the log after the number of lines given in entryLimit. Only if 'download' is false.
     * @param download   If true streams the whole study log file - if not only until entryLimit
     * @return Depending on the 'download' flag returns the whole study log file - or only part of it (until entryLimit) in
     * reverse order and 'Transfer-Encoding:chunked'
     */
    @Transactional
    @Auth
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
            return ok().chunked(studyLogger.readLogFile(study, entryLimit)).as("application/jsonline");
        }
    }

    /**
     * Creates a component within the specified study
     */
    @Transactional
    @Auth
    @BodyParser.Of(BodyParser.Json.class)
    public Result createComponent(Http.Request request, String studyId) throws IOException, HttpException {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user, true);

        JsonNode json = request.body().asJson();
        ComponentProperties props = strictJsonMapper.getMapper().treeToValue(json, ComponentProperties.class);
        ApiService.validateProps(props);

        Component component = componentService.createAndPersistComponent(study, props);

        JsonNode componentNode = jsonUtils.asJsonForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Transactional
    @Auth
    public Result getComponentsByStudy(String studyIdOrUuid) throws HttpException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(studyIdOrUuid);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user);

        List<Component> components = study.getComponentList();

        JsonNode componentsNode = jsonUtils.asJsonForApi(components);
        return ok(ApiEnvelope.wrap(componentsNode).asJsonNode());
    }

    @Transactional
    @Auth
    public Result getComponent(String id) throws HttpException, IOException {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessComponent(component, user);

        JsonNode componentNode = jsonUtils.asJsonForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Transactional
    @Auth
    @BodyParser.Of(BodyParser.Json.class)
    public Result updateComponent(Http.Request request, String id) throws HttpException, IOException {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessComponent(component, user, true);

        ComponentProperties props = componentService.bindToProperties(component);
        JsonNode json = request.body().asJson();
        strictJsonMapper.updateFromJson(props, json);
        ApiService.validateProps(props);

        componentService.renameHtmlFilePath(component, props.getHtmlFilePath(), props.isHtmlFileRename());
        componentService.updateComponentAfterEdit(component, props);

        JsonNode componentNode = jsonUtils.asJsonForApi(component);
        return ok(ApiEnvelope.wrap(componentNode).asJsonNode());
    }

    @Transactional
    @Auth
    public Result deleteComponent(String id) throws HttpException {
        Component component = componentService.getComponentFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessComponent(component, user, true);

        componentService.remove(component, user);
        return ok(ApiEnvelope.wrap("Component deleted successfully").asJsonNode());
    }

    @Transactional
    @Auth
    public Result getBatchesByStudy(String studyId) throws HttpException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user);

        List<Batch> batches = study.getBatchList();

        JsonNode batchesNode = jsonUtils.asJsonForApi(batches);
        return ok(ApiEnvelope.wrap(batchesNode).asJsonNode());
    }

    @Transactional
    @Auth
    public Result getBatch(String id) throws HttpException, IOException {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessBatch(batch, user);

        JsonNode batchNode = jsonUtils.asJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Transactional
    @Auth
    @BodyParser.Of(BodyParser.Json.class)
    public Result createBatch(Http.Request request, String studyId) throws HttpException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user, true);

        JsonNode json = request.body().asJson();
        BatchProperties props = strictJsonMapper.getMapper().treeToValue(json, BatchProperties.class);
        ApiService.validateProps(props);

        Batch batch = batchService.bindToBatch(props);
        batchService.createAndPersistBatch(batch, study, user);

        JsonNode batchNode = jsonUtils.asJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Transactional
    @Auth
    @BodyParser.Of(BodyParser.Json.class)
    public Result updateBatch(Http.Request request, String id) throws HttpException, IOException {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessBatch(batch, user, true);

        BatchProperties props = batchService.bindToProperties(batch);
        JsonNode json = request.body().asJson();
        strictJsonMapper.updateFromJson(props, json);
        ApiService.validateProps(props);

        batchDao.update(batch);

        JsonNode batchNode = jsonUtils.asJsonForApi(batch);
        return ok(ApiEnvelope.wrap(batchNode).asJsonNode());
    }

    @Transactional
    @Auth
    public Result deleteBatch(String id) throws HttpException {
        Batch batch = batchService.getBatchFromIdOrUuid(id);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessBatch(batch, user, true);

        batchService.remove(batch, user);

        ObjectNode response = Json.mapper().createObjectNode()
            .put("message", "Batch deleted successfully");
        return ok(ApiEnvelope.wrap(response).asJsonNode());
    }

    /**
     * Get or generate study codes for the given study, batch and worker type
     *
     * @param studyId Study's ID or UUID
     * @param batchId Optional specify the batch ID to which the study codes should belong to. If it is not specified,
     *                the default batch of this study will be used.
     * @param type    Worker type: `PersonalSingle` (or `ps`), `PersonalMultiple` (or `pm`), `GeneralSingle` (or `gs`),
     *                `GeneralMultiple` (or `gm`), `MTurk` (or `mt`)
     * @param comment Some comment that will be associated with the worker.
     * @param amount  Number of study codes that have to be generated. If empty, 1 is assumed.
     */
    @Transactional
    @Auth
    public Result getOrGenerateStudyCodes(String studyId, Option<Long> batchId, String type, String comment, Integer amount)
        throws HttpException {
        Study study = studyService.getStudyFromIdOrUuid(studyId);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudy(study, user);

        Batch batch = batchService.getBatchOrDefaultBatch(batchId, study);
        authorizationService.canUserAccessBatch(batch, user);

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType(workerService.extractWorkerType(type));
        props.setComment(comment);
        props.setAmount(amount != null ? amount : 1);
        ApiService.validateProps(props);

        JsonNode studyCodesNode = studyLinkService.getStudyCodes(batch, props);
        return ok(ApiEnvelope.wrap(studyCodesNode).asJsonNode());
    }

    /**
     * Same as getOrGenerateStudyCodes but expects the parameter in a JSON body
     */
    @Transactional
    @Auth
    public Result getOrGenerateStudyCodesFromJsonBody(Http.Request request, String studyId) throws HttpException {
        JsonNode json = request.body().asJson();
        String comment = ApiService.getFieldFromJson(json, "comment", String.class, null);
        int amount = ApiService.getFieldFromJson(json, "amount", Integer.class, 1);
        String type = ApiService.getFieldFromJson(json, "type", String.class, null);
        Long batchId = ApiService.getFieldFromJson(json, "batchId", Long.class, null);
        Option<Long> batchIdOption = batchId != null ? Option.apply(batchId) : Option.empty();
        return getOrGenerateStudyCodes(studyId, batchIdOption, type, comment, amount);
    }

    @Transactional
    @Auth
    public Result getStudyCode(String code) throws HttpException {
        StudyLink studyLink = studyLinkDao.findByStudyCode(code);
        User user = authService.getSignedinUser();
        authorizationService.canUserAccessStudyLink(studyLink, user);

        JsonNode linkNode = jsonUtils.getStudyLinkData(studyLink);
        return ok(ApiEnvelope.wrap(linkNode).asJsonNode());
    }

    @Transactional
    @Auth
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
    @Auth
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
     */
    @Transactional
    @Auth
    public Result exportResultMetadata(Http.Request request, Boolean isApiCall) throws HttpException, IOException {
        Map<String, Object> wrapperObject = isApiCall
            ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
            : Collections.emptyMap();

        // The check if the signedin user is a member of the study or a superuser is done in the ResultStreamer
        File file = resultStreamer.writeResultMetadata(request, wrapperObject);

        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_metadata_"
            + Helpers.getDateTimeYyyyMMddHHmmss() + ".json");
        //noinspection ResultOfMethodCallIgnored
        return ok().streamed(
                Helpers.okFileStreamed(file, file::delete),
                Optional.of(file.length()),
                Optional.of("application/json"))
            .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
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
    @Auth
    public Result exportResultData(Http.Request request, boolean asPlainText, boolean isApiCall) throws HttpException {
        // The check if the signedin user is a member of the study or a superuser is done in the ResultStreamer
        if (asPlainText) {
            Source<ByteString, ?> dataSource = resultStreamer.streamComponentResultData(request);
            String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_data_"
                + Helpers.getDateTimeYyyyMMddHHmmss() + ".txt");
            return ok().chunked(dataSource).as("application/octet-stream")
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
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
    @Auth
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
    @Auth
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
    @Auth
    @BodyParser.Of(BodyParser.Json.class)
    public Result removeResults(Http.Request request) throws HttpException {
        User signedinUser = authService.getSignedinUser();
        List<Long> crids = componentResultIdsExtractor.extract(request.body().asJson());
        crids.addAll(componentResultIdsExtractor.extract(request.queryString()));

        // The check, that the user is a member of the study or a superuser, and that the study is not locked, is done
        // in the ResultRemover`
        resultRemover.removeComponentResults(crids, signedinUser, true);

        JsonNode responseJson = JsonUtils.asObjectNode(crids);
        return ok(ApiEnvelope.wrap(responseJson).asJsonNode());
    }

}
