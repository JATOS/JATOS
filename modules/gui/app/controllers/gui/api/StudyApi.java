package controllers.gui.api;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction.Transactional;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.common.*;
import general.common.ApiEnvelope;
import general.common.Common;
import general.common.StudyLogger;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import json.common.StrictJson;
import models.common.Study;
import models.common.User;
import models.gui.StudyProperties;
import models.gui.StudySourceProperties;
import models.gui.StudySourceSyncProperties;
import org.apache.pekko.stream.javadsl.FileIO;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import play.core.utils.HttpHeaderParameterEncoding;
import play.http.HttpEntity;
import play.libs.Json;
import play.mvc.*;
import services.gui.*;
import services.gui.importexport.LocalFolderStudyImport;
import services.gui.importexport.StudyArchiveImportExport;
import services.gui.importexport.StudySourceService;
import services.gui.importexport.StudySourceSync;
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
import static general.common.ApiEnvelope.ErrorCode.*;
import static java.lang.Boolean.TRUE;
import static messaging.common.FlashMessagingHelper.SUCCESS;
import static models.common.User.Role.*;

@Singleton
public class StudyApi extends Controller {

    private final AuthorizationService authorizationService;
    private final ApiService apiService;
    private final StudyService studyService;
    private final StudySourceService studySourceService;
    private final StudySourceSync studySourceSync;
    private final StudyArchiveImportExport studyArchiveImportExport;
    private final LocalFolderStudyImport localFolderStudyImport;
    private final UserDao userDao;
    private final StudyDao studyDao;
    private final DomainJsonMapper domainJsonMapper;
    private final StrictJson strictJson;
    private final StudyLogger studyLogger;

    @Inject
    StudyApi(AuthorizationService authorizationService,
             ApiService apiService,
             StudyService studyService,
             StudySourceService studySourceService,
             StudySourceSync studySourceSync,
             StudyArchiveImportExport studyArchiveImportExport,
             LocalFolderStudyImport localFolderStudyImport,
             UserDao userDao,
             StudyDao studyDao,
             DomainJsonMapper domainJsonMapper,
             StrictJson strictJson,
             StudyLogger studyLogger) {
        this.authorizationService = authorizationService;
        this.apiService = apiService;
        this.studyService = studyService;
        this.studySourceService = studySourceService;
        this.studySourceSync = studySourceSync;
        this.studyArchiveImportExport = studyArchiveImportExport;
        this.localFolderStudyImport = localFolderStudyImport;
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
    @Auth(roles = {VIEWER, USER, SUPERUSER}, types = {TOKEN, SESSION})
    @Transactional
    public Result getAllStudyPropertiesOfSignedinUser(Boolean withComponentProperties, Boolean withBatchProperties) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        List<Study> studies = authorizationService.isAllowedSuperuser(signedinUser)
                ? studyDao.findAll() : studyDao.findAllByUser(signedinUser);

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
    @Auth(roles = {USER, SUPERUSER}, types = {TOKEN, SESSION})
    @BodyParser.Of(ApiJsonBodyParser.class)
    public Result createStudy(play.mvc.Http.Request request) {
        JsonNode json = apiService.getJsonFromBody(request);
        return createStudyFromJson(json, false);
    }

    /**
     * Dispatches the request based on the {@code Content-Type} header: 1) ZIP / multipart upload → import a study
     * archive 2) JSON → create a study from properties or a configured source.
     *
     * StudyUploadBodyParser selects the parser by content type, spilling binary archives to disk while preserving the
     * normal JSON and multipart limits.
     */
    @Async(Executor.IO)
    @Auth(roles = {USER, SUPERUSER}, types = {TOKEN, SESSION})
    @BodyParser.Of(StudyUploadBodyParser.class)
    public Result importOrCreateStudy(play.mvc.Http.Request request, boolean keepProperties, boolean keepAssets,
                                      boolean keepCurrentAssetsName, boolean renameAssets) {
        String contentType = request.header(CONTENT_TYPE).orElse("");

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
            if (json.has("source")) {
                for (String option : List.of("keepProperties", "keepAssets", "keepCurrentAssetsName", "renameAssets")) {
                    if (request.queryString().containsKey(option)) {
                        throw new BadRequestException("Query parameter '" + option + "' is not supported for source-based creation.", INVALID_REQUEST);
                    }
                }
                return createStudyFromSource(json);
            } else {
                return createStudyFromJson(json, renameAssets);
            }
        }

        throw new HttpException(UNSUPPORTED_MEDIA_TYPE, "Wrong 'Content-Type' " + contentType, WRONG_CONTENT_TYPE);
    }

    private Result createStudyFromSource(JsonNode json) {
        if (json.size() != 1) {
            throw new BadRequestException("Source-based creation cannot include study properties or other fields.", INVALID_REQUEST);
        }
        JsonNode source = json.get("source");
        if (!source.isObject()) throw new BadRequestException("'source' must be a JSON object.", INVALID_REQUEST);
        if (!"LOCAL_FOLDER".equals(source.path("type").asText())) {
            throw new BadRequestException("Unsupported source type. Use LOCAL_FOLDER.", INVALID_REQUEST);
        }
        source.fieldNames().forEachRemaining(field -> {
            if (!field.equals("type") && !field.equals("localFolderPath")) {
                throw new BadRequestException("Unknown source field: " + field, INVALID_REQUEST);
            }
        });
        StudySourceProperties props = strictJson.updateFromJson(new StudySourceProperties(), source);
        apiService.validateProps(props);
        var result = localFolderStudyImport.createStudyFromLocalFolder(props.getLocalFolderPath());
        Context.current().response().putFlash(SUCCESS, result.message());
        return created(ApiEnvelope.wrap(result.message(), result.study()).asJsonNode());
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
    @Auth(roles = {USER, SUPERUSER}, types = {TOKEN, SESSION})
    @BodyParser.Of(StudyUploadBodyParser.class)
    public Result importStudy(play.mvc.Http.Request request, boolean keepProperties, boolean keepAssets,
                              boolean keepCurrentAssetsName, boolean renameAssets) {
        List<String> allowedContentTypes = Arrays.asList("application/zip", "application/jzip", "application/octet-stream");
        Path file = apiService.extractFile(request, Study.STUDY, allowedContentTypes);
        if (file.toFile().length() > Common.getStudyArchiveMaxSize()) {
            throw new HttpException(REQUEST_ENTITY_TOO_LARGE, "Study archive exceeds the configured size limit", TOO_LARGE);
        }

        try {
            Map<String, Object> importInfo = studyArchiveImportExport.importStudy(file);

            JsonNode studyNode = studyArchiveImportExport.importStudyConfirmedForApi(
                    keepProperties,
                    keepAssets,
                    keepCurrentAssetsName,
                    renameAssets);

            JsonNode envelope = ApiEnvelope.wrap(studyNode).asJsonNode();
            boolean wasOverwritten = TRUE.equals(importInfo.get("studyExists"));
            return wasOverwritten ? ok(envelope) : created(envelope);
        } finally {
            studyArchiveImportExport.cleanupAfterStudyImport();
        }
    }

    /**
     * Returns the study archive (.jzip) as a file
     */
    @Async(Executor.IO)
    @Auth(roles = {USER, SUPERUSER}, types = {TOKEN, SESSION})
    public Result exportStudy(String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Path zipFile;
        try {
            zipFile = studyArchiveImportExport.createStudyExportZipFile(study.getId());
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
    @Auth(roles = {USER, ADMIN, SUPERUSER}, types = {TOKEN, SESSION})
    @Transactional
    public Result deleteStudy(String id) {
        Study study = studyDao.findAndLock(studyService.getStudyFromIdOrUuid(id));
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkLockedStudyOwner(study, signedinUser, false);
        authorizationService.checkStudyNotLocked(study);

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
    @Auth(roles = {VIEWER, USER, SUPERUSER}, types = {TOKEN, SESSION})
    @Transactional
    public Result getStudyProperties(String id, Boolean withComponentProperties, Boolean withBatchProperties) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        JsonNode studiesNode = domainJsonMapper.studyAsJsonForApi(study, withComponentProperties, withBatchProperties);
        return ok(ApiEnvelope.wrap(studiesNode).asJsonNode());
    }


    /**
     * Updates study properties for members and enabled superusers. Only admins may change activation status; admins
     * without study access may update only that status.
     */
    @Async(Executor.IO)
    @Auth(roles = {USER, ADMIN, SUPERUSER}, types = {TOKEN, SESSION})
    @BodyParser.Of(ApiJsonBodyParser.class)
    @Transactional
    public Result updateStudyProperties(play.mvc.Http.Request request, String id) throws IOException {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Study study = studyService.getStudyFromIdOrUuid(id);
        authorizationService.canUserAccessStudy(study, signedinUser, true, true);

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
    @Auth(roles = {VIEWER, USER, SUPERUSER}, types = {TOKEN, SESSION})
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
    @Auth(roles = {USER, ADMIN, SUPERUSER}, types = {TOKEN, SESSION})
    @Transactional
    public Result changeOwner(String id, Long userId) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        authorizationService.checkStudyOwner(study, Context.current().args().get(SIGNEDIN_USER), true);
        studyService.changeOwner(study, userDao.findById(userId));
        return ok(ApiEnvelope.wrap("Study ownership transferred successfully").asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {USER, SUPERUSER}, types = {TOKEN, SESSION})
    @Transactional
    public Result addMemberToStudy(String id, Long userId) {
        return changeMemberOfStudy(id, userId, true);
    }

    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, SUPERUSER}, types = {TOKEN, SESSION})
    @Transactional
    public Result removeMemberFromStudy(String id, Long userId) {
        return changeMemberOfStudy(id, userId, false);
    }

    public Result changeMemberOfStudy(String id, Long userId, boolean isMember) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);

        User user = userDao.findById(userId);
        authorizationService.checkUserExists(user);

        authorizationService.checkMembershipChange(study, signedinUser, user, isMember);
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
     * @return The complete log file as application/octet-stream when downloading, otherwise a chunked application/json
     * array of log entries in reverse chronological order, limited by entryLimit.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, SUPERUSER}, types = {TOKEN, SESSION})
    public Result studyLog(String id, int entryLimit, boolean download) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        if (download) {
            Path studyLogPath = Path.of(studyLogger.getPath(study.getUuid()));
            if (Files.notExists(studyLogPath)) throw new NotFoundException("Study log file doesn't exist");

            Source<ByteString, ?> source = FileIO.fromPath(studyLogPath);
            Optional<Long> contentLength = Optional.of(studyLogPath.toFile().length());
            String cdHeader = "attachment; "
                    + HttpHeaderParameterEncoding.encode("filename", "jatos_studylog_"
                    + studyLogger.getFilename(study.getUuid()));
            Context.current().response().setHeader(CONTENT_DISPOSITION, cdHeader);
            return new Result(
                    new ResponseHeader(200, Collections.emptyMap()),
                    new HttpEntity.Streamed(source, contentLength, Optional.of("application/octet-stream")));
        } else {
            return ok().chunked(studyLogger.readLogFile(study, entryLimit)).as("application/json");
        }
    }

    /**
     * GET request that returns the study source configuration.
     */
    @Async(Executor.IO)
    @Transactional
    @Auth(roles = {USER, SUPERUSER}, types = {TOKEN, SESSION})
    public Result getStudySource(String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User user = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkStudyOwner(study, user, false);
        StudySourceProperties props = studySourceService.get(study.getId());
        return ok(ApiEnvelope.wrap(props).asJsonNode());
    }

    /**
     * PUT request to update study source configuration without synchronizing content.
     */
    @Async(Executor.IO)
    @Transactional
    @Auth(roles = {USER, SUPERUSER}, types = {TOKEN, SESSION})
    @BodyParser.Of(ApiJsonBodyParser.class)
    public Result saveStudySource(Http.Request request, String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User user = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.checkStudyOwner(study, user, false);
        JsonNode json = apiService.getJsonFromBody(request);
        if (!json.isObject()) throw new BadRequestException("Request body is not a JSON object", INVALID_JSON);
        StudySourceProperties props = strictJson.updateFromJson(new StudySourceProperties(), json);
        apiService.validateProps(props);
        StudySourceProperties settings = studySourceService.save(study.getId(), props);
        return ok(ApiEnvelope.wrap(settings).asJsonNode());
    }

    /**
     * Preview current source content for a study member before selecting an overwrite scope.
     */
    @Async(Executor.IO)
    @Transactional
    @Auth(roles = {USER, SUPERUSER}, types = {TOKEN, SESSION})
    public Result previewStudySourceSync(String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User user = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, user, true);
        StudySourceSync.SyncPreview syncPreview = studySourceSync.preview(study.getId());
        return ok(ApiEnvelope.wrap(syncPreview).asJsonNode());
    }

    @Async(Executor.IO)
    @Auth(roles = {USER, SUPERUSER}, types = {TOKEN, SESSION})
    @BodyParser.Of(ApiJsonBodyParser.class)
    public Result syncStudySource(Http.Request request, String id) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        if (study == null) throw new NotFoundException("Study doesn't exist.");
        JsonNode json = apiService.getJsonFromBody(request);
        if (!json.isObject()) throw new BadRequestException("Request body is not a JSON object", INVALID_JSON);
        StudySourceSyncProperties props = strictJson.updateFromJson(new StudySourceSyncProperties(), json);
        apiService.validateProps(props);

        var result = studySourceSync.sync(study.getId(), props);
        Context.current().response().putFlash(SUCCESS, result.message());
        return ok(ApiEnvelope.wrap(result.message(), Json.toJson(result)).asJsonNode());
    }

}
