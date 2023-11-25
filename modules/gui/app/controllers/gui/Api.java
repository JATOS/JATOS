package controllers.gui;

import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import auth.gui.AuthApiToken;
import auth.gui.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.RequestScope;
import general.common.StudyLogger;
import models.common.ComponentResult;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.core.utils.HttpHeaderParameterEncoding;
import play.db.jpa.Transactional;
import play.http.HttpEntity;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.ResponseHeader;
import play.mvc.Result;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static auth.gui.AuthAction.Auth;
import static controllers.gui.actionannotations.ApiAccessLoggingAction.ApiAccessLogging;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static models.common.User.Role.ADMIN;

/**
 * JATOS API Controller: all requests possible via JATOS' API
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
    private final StudyService studyService;
    private final StudyLinkService studyLinkService;
    private final ImportExport importExport;
    private final ResultRemover resultRemover;
    private final ResultStreamer resultStreamer;
    private final Checker checker;
    private final JsonUtils jsonUtils;
    private final StudyLogger studyLogger;
    private final IOUtils ioUtils;

    @Inject
    Api(Admin admin, AdminService adminService, AuthService authService,
            ComponentResultIdsExtractor componentResultIdsExtractor,
            StudyDao studyDao, ComponentResultDao componentResultDao, StudyService studyService,
            StudyLinkService studyLinkService,
            ImportExport importExport, ResultRemover resultRemover,
            ResultStreamer resultStreamer, Checker checker, JsonUtils jsonUtils,
            StudyLogger studyLogger, IOUtils ioUtils) {
        this.admin = admin;
        this.adminService = adminService;
        this.authService = authService;
        this.componentResultIdsExtractor = componentResultIdsExtractor;
        this.studyDao = studyDao;
        this.componentResultDao = componentResultDao;
        this.studyService = studyService;
        this.studyLinkService = studyLinkService;
        this.importExport = importExport;
        this.resultRemover = resultRemover;
        this.resultStreamer = resultStreamer;
        this.checker = checker;
        this.jsonUtils = jsonUtils;
        this.studyLogger = studyLogger;
        this.ioUtils = ioUtils;
    }

    /**
     * Returns information about the API token used in the request in JSON.
     */
    @Auth
    public Result testToken() {
        return ok(JsonUtils.wrapForApi(JsonUtils.asJsonNode(RequestScope.get(AuthApiToken.API_TOKEN))));
    }

    /**
     * Returns admin status information in JSON
     */
    @Transactional
    @Auth(ADMIN)
    public Result status() {
        return ok(JsonUtils.wrapForApi(adminService.getAdminStatus()));
    }

    /**
     * Returns a JATOS application log file.
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
     * Returns a study log.
     *
     * @param id         Study's ID or UUID
     * @param entryLimit It cuts the log after the number of lines given in entryLimit. Only if 'download' is false.
     * @param download   If true streams the whole study log file - if not only until entryLimit
     * @return Depending on 'download' flag returns the whole study log file - or only part of it (until entryLimit) in
     * reverse order and 'Transfer-Encoding:chunked'
     */
    @Transactional
    @Auth
    public Result studyLog(String id, int entryLimit, boolean download) throws ForbiddenException, NotFoundException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        if (download) {
            Path studyLogPath = Paths.get(studyLogger.getPath(study));
            if (Files.notExists(studyLogPath)) return notFound();

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
     * Returns all study properties a user can access.
     *
     * @param withComponentProperties Flag if true all component properties of the study will be included
     * @param withBatchProperties     Flag if true all batch properties will be included
     * @return All study properties the user has access to (is member of) in JSON
     */
    @Transactional
    @Auth
    public Result getAllStudyPropertiesByUser(Boolean withComponentProperties, Boolean withBatchProperties)
            throws IOException {
        User signedinUser = authService.getSignedinUser();
        List<Study> studies = studyDao.findAllByUser(signedinUser);
        ArrayNode studiesArray = Json.newArray();
        for (Study s : studies) {
            studiesArray.add(jsonUtils.studyAsJsonForApi(s, withComponentProperties, withBatchProperties));
        }
        return ok(JsonUtils.wrapForApi(studiesArray));
    }

    /**
     * Get study properties
     *
     * @param id                      Study's ID or UUID
     * @param withComponentProperties Flag if true all component properties of the study will be included
     * @param withBatchProperties     Flag if true all batch properties will be included
     * @return The study properties in JSON
     */
    @Transactional
    @Auth
    public Result getStudyProperties(String id, Boolean withComponentProperties, Boolean withBatchProperties)
            throws ForbiddenException, NotFoundException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        JsonNode studiesNode = jsonUtils.studyAsJsonForApi(study, withComponentProperties, withBatchProperties);
        return ok(JsonUtils.wrapForApi(studiesNode));
    }

    /**
     * Gets the study assets directory structure as JSON
     *
     * @param id      Study's ID or UUID
     * @param flatten Flag, if set to `true` the returned JSON will be a flat list of files (no tree, no directories).
     *                If `false`, the returned JSON will have tree-like structure and include directories. Default is
     *                `false`.
     * @return JSON with study assets directory structure
     */
    @Transactional
    @Auth
    public Result getStudyAssetsStructure(String id, boolean flatten)
            throws ForbiddenException, NotFoundException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        File base;
        try {
            base = ioUtils.getStudyAssetsDir(study.getDirName());
        } catch (IOException e) {
            return notFound("Study assets directory couldn't be found");
        }
        JsonNode structure = DirectoryStructureToJson.get(base, flatten);
        return ok(JsonUtils.wrapForApi(structure));
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
    public Result downloadStudyAssetsFile(String id, String filepath) throws ForbiddenException, NotFoundException {
        filepath = Helpers.urlDecode(filepath);
        if (filepath.startsWith("/")) filepath = filepath.substring(1);
        Study study = studyService.getStudyFromIdOrUuid(id);
        File file;
        try {
            file = ioUtils.getFileInStudyAssetsDir(study.getDirName(), filepath);
            if (!file.isFile()) throw new IOException();
        } catch (IOException e) {
            return notFound("File '" + filepath + "' couldn't be found.");
        }
        return ok().sendFile(file);
    }

    /**
     * Upload a file to a study assets folder.
     *
     * @param id       Study's ID or UUID
     * @param filepath Supposed path of the uploaded file in the study assets directory. If it is null, "", "/" or "."
     *                 it will be ignored and the uploaded file saved in the top-level of the assets under the uploaded
     *                 file's name. If it is a directory, the filename is taken from the uploaded file. If it ends with
     *                 a filename the uploaded file will be renamed to this name. All non-existing subdirectories will
     *                 be created. Existing files will be overwritten. The path can be URL encoded but doesn't have to be.
     */
    @Transactional
    @Auth
    public Result uploadStudyAssetsFile(Http.Request request, String id, String filepath)
            throws ForbiddenException, NotFoundException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        checker.checkStudyLocked(study);

        if (request.body().asMultipartFormData() == null) return badRequest("File missing");
        Http.MultipartFormData.FilePart<Object> filePart = request.body().asMultipartFormData().getFile("studyAssetsFile");
        if (filePart == null) return badRequest("File missing");
        File uploadedFile = (File) filePart.getFile();

        try {
            Path assetsFilePath= ioUtils.getAssetsFilePath(filepath, filePart.getFilename(), study);
            if (Files.notExists(assetsFilePath)) Files.createDirectories(assetsFilePath);
            Files.move(uploadedFile.toPath(), assetsFilePath, REPLACE_EXISTING);
            return ok();
        } catch (IOException e) {
            LOGGER.info(".uploadStudyAssetsFile: " + e.getLocalizedMessage());
            return badRequest("Error writing file");
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
    public Result deleteStudyAssetsFile(String id, String filepath) throws ForbiddenException, NotFoundException {
        filepath = Helpers.urlDecode(filepath);
        if (filepath.startsWith("/")) filepath = filepath.substring(1);
        Study study = studyService.getStudyFromIdOrUuid(id);
        checker.checkStudyLocked(study);

        try {
            File file = ioUtils.getFileInStudyAssetsDir(study.getDirName(), filepath);
            if (file.isDirectory()) throw new IOException("Directories can't be deleted.");
            Files.delete(file.toPath());
        } catch (NoSuchFileException e) {
            return notFound("File '" + filepath + "' couldn't be found.");
        } catch (IOException e) {
            LOGGER.info(".deleteStudyAssetsFile: " + e.getLocalizedMessage());
            return badRequest();
        }
        return ok();
    }

    /**
     * Get study codes for the given batch and worker type
     *
     * @param id      Study's ID or UUID
     * @param batchId Optional specify the batch ID to which the study codes should belong to. If it is not specified
     *                the default batch of this study will be used.
     * @param type    Worker type: `PersonalSingle` (or `ps`), `PersonalMultiple` (or `pm`), `GeneralSingle`
     *                (or `gs`), `GeneralMultiple` (or `gm`), `MTurk` (or `mt`)
     * @param comment Some comment that will be associated with the worker.
     * @param amount  Number of study codes that have to be generated. If empty 1 is assumed.
     */
    @Transactional
    @Auth
    public Result getStudyCodes(String id, Option<Long> batchId, String type, String comment, Integer amount)
            throws ForbiddenException, NotFoundException, BadRequestException {
        return ok(JsonUtils.wrapForApi(studyLinkService.getStudyCodes(id, batchId, type, comment, amount)));
    }

    /**
     * Returns a JATOS study archive (.jzip)
     *
     * @param id Study's ID or UUID
     */
    @Transactional
    @Auth
    public Result exportStudy(String id) throws ForbiddenException, NotFoundException {
        return importExport.exportStudy(id);
    }

    /**
     * Deletes a study
     *
     * @param id Study's ID or UUID
     */
    @Transactional
    @Auth
    public Result deleteStudy(String id) throws ForbiddenException, NotFoundException, IOException {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = authService.getSignedinUser();
        checker.checkStudyLocked(study);

        studyService.removeStudyInclAssets(study, signedinUser);
        return ok();
    }

    /**
     * Imports a JATOS study archive (.jzip).
     *
     * @param keepProperties        If true and the study exists already in JATOS the current properties are kept.
     *                              Default is `false` (properties are overwritten by default). If the study doesn't
     *                              already exist this parameter has no effect.
     * @param keepAssets            If true and the study exists already in JATOS the current study assets directory is
     *                              kept. Default is `false` (assets are overwritten by default). If the study doesn't
     *                              already exist this parameter has no effect.
     * @param keepCurrentAssetsName If the assets are going to be overwritten (`keepAssets=false`), this flag indicates
     *                              if the study assets directory name is taken form the current or the uploaded one. In
     *                              the common case that both names are the same this has no effect. But if the current
     *                              asset directory name is different from the uploaded one a `keepCurrentAssetsName=true`
     *                              indicates that the name of the currently installed assets directory should be kept.
     *                              A `false` indicates that the name should be taken from the uploaded one. Default is `true`.
     * @param renameAssets          If the study assets directory already exists in JATOS but belongs to a different
     *                              study it cannot be overwritten. In this case you can set `renameAssets=true` to let
     *                              JATOS add a suffix to the assets directory name (original name + "_" + a number).
     *                              Default is `true`.
     */
    @Transactional
    @Auth
    public Result importStudy(Http.Request request, boolean keepProperties, boolean keepAssets,
            boolean keepCurrentAssetsName, boolean renameAssets) throws ForbiddenException, NotFoundException, IOException {
        return importExport.importStudyApi(request, keepProperties, keepAssets, keepCurrentAssetsName, renameAssets);
    }

    /**
     * Removes results from the database (ComponentResults and StudyResults) and result files from the file system.
     * Which results are to be removed are indicated by query parameters and/or JSON in the request's body. Different
     * IDs can be used, e.g. study ID (to delete all results of this study), component results (all of this component),
     * batch ID (all of this batch). Of course component result IDs or study result IDs can be specified directly. It
     * primarily removes the ComponentResults since results are associated with them, but if in the process a
     * StudyResults becomes empty (no more ComponentResults) it will be deleted too.
     */
    @Transactional
    @Auth
    public Result removeResults(Http.Request request)
            throws BadRequestException, ForbiddenException, NotFoundException {
        User signedinUser = authService.getSignedinUser();
        List<Long> crids = componentResultIdsExtractor.extract(request.body().asJson());
        crids.addAll(componentResultIdsExtractor.extract(request.queryString()));
        resultRemover.removeComponentResults(crids, signedinUser, true);
        return ok();
    }

    /**
     * Returns results (including metadata, data, and files) in a zip file. The results are specified by IDs (can be
     * nearly any kind) in the request's body or as query parameters. Streaming is used to reduce memory and disk usage.
     *
     * @param isApiCall If true the response JSON gets an additional 'apiVersion' field
     */
    @Transactional
    @Auth
    public Result exportResults(Http.Request request, Boolean isApiCall) throws BadRequestException {
        Map<String, Object> wrapperObject = isApiCall
                ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
                : Collections.emptyMap();
        Source<ByteString, ?> dataSource = resultStreamer.streamResults(request, ResultStreamer.ResultType.COMBINED,
                wrapperObject);
        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_"
                + Helpers.getDateTimeYyyyMMddHHmmss() + ".jrzip");
        return ok().chunked(dataSource).as("application/zip")
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
    }

    /**
     * Returns all result's metadata (but not result files and not metadata) in a zip file. The results are specified by
     * IDs (can be any kind) in the request's body or query parameters. Streaming is used to reduce memory and disk usage.
     *
     * @param isApiCall If true the response JSON gets an additional 'apiVersion' field
     */
    @Transactional
    @Auth
    public Result exportResultMetadata(Http.Request request, Boolean isApiCall)
            throws ForbiddenException, BadRequestException, NotFoundException, IOException {
        Map<String, Object> wrapperObject = isApiCall
                ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
                : Collections.emptyMap();
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
     * the result data as plain text (each result data in a new line) or in a zip file (each
     * result data in its own file). The results are specified by IDs (can be any kind) in the request's body or as
     * query parameters. Both options use streaming to reduce memory and disk usage.
     *
     * @param asPlainText If true the results will be returned in one single text file, each result in a new line.
     * @param isApiCall   If true the response JSON gets an additional 'apiVersion' field
     */
    @Transactional
    @Auth
    public Result exportResultData(Http.Request request, boolean asPlainText, boolean isApiCall)
            throws ForbiddenException, BadRequestException, NotFoundException {
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
    public Result exportResultFiles(Http.Request request)
            throws IOException, ForbiddenException, BadRequestException, NotFoundException {
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
    public Result exportSingleResultFile(Long componentResultId, String filename)
            throws ForbiddenException, NotFoundException {
        ComponentResult componentResult = componentResultDao.findById(componentResultId);
        User signedinUser = authService.getSignedinUser();
        checker.checkComponentResult(componentResult, signedinUser, false);

        File file;
        try {
            Study study = componentResult.getComponent().getStudy();
            file = ioUtils.getResultUploadFileSecurely(componentResult.getStudyResult().getId(), componentResultId, filename);
            if (!file.exists()) throw new IOException();
            studyLogger.log(study, signedinUser, "Exported single result file");
        } catch (IOException e) {
            return notFound("File does not exist");
        }
        return ok(file);
    }

}
