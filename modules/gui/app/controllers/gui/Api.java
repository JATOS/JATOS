package controllers.gui;

import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import auth.gui.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import auth.gui.AuthActionApiToken;
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
import utils.common.Helpers;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static auth.gui.AuthAction.Auth;
import static models.common.User.Role.ADMIN;

/**
 * JATOS API Controller: interface for all requests possible via JATOS' API
 *
 * @author Kristian Lange
 *
 * /api/components/properties ?componentId ?componentUuid
 *
 *  keepCurrentAssetsName - if you don't keep the assets - take the assets name form the current study or the uploaded one
 * renameAssets - if the assets name already exists but is from another study - rename it or not by adding some suffix
 *
 * todo POST/GET/DELETE /jatos/api/v1/study/id/assets/filepath
 * todo difference in import study btw keepCurrentAssetsName and renameAssets
 * todo check keepAssets and keepCurrentAssetsName
 * todo API quotas / on-off
 * todo SignInOidc todos
 */
@SuppressWarnings("deprecation")
@Singleton
public class Api extends Controller {

    private final Admin admin;
    private final AdminService adminService;
    private final AuthService authenticationService;
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
    Api(Admin admin, AdminService adminService, AuthService authenticationService,
            ComponentResultIdsExtractor componentResultIdsExtractor,
            StudyDao studyDao, ComponentResultDao componentResultDao, StudyService studyService,
            StudyLinkService studyLinkService,
            ImportExport importExport, ResultRemover resultRemover,
            ResultStreamer resultStreamer, Checker checker, JsonUtils jsonUtils,
            StudyLogger studyLogger, IOUtils ioUtils) {
        this.admin = admin;
        this.adminService = adminService;
        this.authenticationService = authenticationService;
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
     * @return Returns information about the API token used in the request in JSON.
     */
    @Auth
    public Result testToken() {
        return ok(JsonUtils.wrapForApi(JsonUtils.asJsonNode(RequestScope.get(AuthActionApiToken.API_TOKEN))));
    }

    /**
     * @returns Returns admin status information in JSON
     */
    @Transactional
    @Auth(ADMIN)
    public Result status() {
        return ok(JsonUtils.wrapForApi(adminService.getAdminStatus()));
    }

    /**
     * @param filename Log's filename
     * @return Returns the log file
     */
    @Transactional
    @Auth(ADMIN)
    public Result logs(String filename) {
        return admin.logs(filename, -1, false);
    }

    /**
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
     * @param withComponentProperties Flag if true all component properties of the study will be included
     * @param withBatchProperties     Flag if true all batch properties will be included
     * @return All study properties the user has access to (is member of) in JSON
     */
    @Transactional
    @Auth
    public Result getAllStudyPropertiesByUser(Boolean withComponentProperties, Boolean withBatchProperties)
            throws IOException {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Study> studies = studyDao.findAllByUser(loggedInUser);
        ArrayNode studiesArray = Json.newArray();
        for (Study s : studies) {
            studiesArray.add(jsonUtils.studyAsJsonForApi(s, withComponentProperties, withBatchProperties));
        }
        return ok(JsonUtils.wrapForApi(studiesArray));
    }

    /**
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
     * Returns study codes for the given batch and worker type. A comment can be specified and amount specifies the
     * number to be generated.
     */
    @Transactional
    @Auth
    public Result getStudyCodes(String id, Option<Long> batchId, String type, String comment, Integer amount)
            throws ForbiddenException, NotFoundException, BadRequestException {
        return ok(JsonUtils.wrapForApi(studyLinkService.getStudyCodes(id, batchId, type, comment, amount)));
    }

    @Transactional
    @Auth
    public Result exportStudy(String id) throws ForbiddenException, NotFoundException {
        return importExport.exportStudy(id);
    }

    @Transactional
    @Auth
    public Result importStudy(Http.Request request, boolean keepProperties, boolean keepAssets,
            boolean keepCurrentAssetsName, boolean renameAssets) throws ForbiddenException, NotFoundException, IOException {
        return importExport.importStudyApi(request, keepProperties, keepAssets, keepCurrentAssetsName, renameAssets);
    }

    @Transactional
    @Auth
    public Result removeResults(Http.Request request)
            throws BadRequestException, ForbiddenException, NotFoundException {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> crids = componentResultIdsExtractor.extract(request.body().asJson());
        crids.addAll(componentResultIdsExtractor.extract(request.queryString()));
        resultRemover.removeComponentResults(crids, loggedInUser, true);
        return ok();
    }

    /**
     * Returns results (including metadata, data, and files) in a zip file. The results are specified by IDs (can be any kind) in
     * the request's body or as query parameters. Streaming is used to reduce memory and disk usage.
     */
    @Transactional
    @Auth
    public Result exportResults(Http.Request request, Boolean wrap) throws BadRequestException {
        Map<String, Object> wrapperObject = wrap
                ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
                : Collections.emptyMap();
        Source<ByteString, ?> dataSource = resultStreamer.streamResults(request, ResultStreamer.ResultType.COMBINED, wrapperObject);
        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_"
                + Helpers.getDateTimeYyyyMMddHHmmss() + ".jrzip");
        return ok().chunked(dataSource).as("application/zip")
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
    }

    /**
     * Returns all result's metadata (but not result files and not metadata) in a zip file. The results are specified by
     * IDs (can be any kind) in the request's body or query parameters. Streaming is used to reduce memory and disk usage.
     */
    @Transactional
    @Auth
    public Result exportResultMetadata(Http.Request request, Boolean wrap)
            throws ForbiddenException, BadRequestException, NotFoundException, IOException {
        Map<String, Object> wrapperObject = wrap
                ? Collections.singletonMap("apiVersion", Common.getJatosApiVersion())
                : Collections.emptyMap();
        File file = resultStreamer.writeResultMetadata(request, wrapperObject);
        String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_metadata_"
                + Helpers.getDateTimeYyyyMMddHHmmss() + ".json");
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
     */
    @Transactional
    @Auth
    public Result exportResultData(Http.Request request, boolean asPlainText, boolean wrap)
            throws ForbiddenException, BadRequestException, NotFoundException {
        if (asPlainText) {
            Source<ByteString, ?> dataSource = resultStreamer.streamComponentResultData(request);
            String filename = HttpHeaderParameterEncoding.encode("filename", "jatos_results_data_"
                    + Helpers.getDateTimeYyyyMMddHHmmss() + ".txt");
            return ok().chunked(dataSource).as("application/octet-stream")
                    .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filename);
        } else {
            Map<String, Object> wrapperObject = wrap
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

    @Transactional
    @Auth
    public Result exportSingleResultFile(Long componentResultId, String filename)
            throws ForbiddenException, NotFoundException {
        ComponentResult componentResult = componentResultDao.findById(componentResultId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkComponentResult(componentResult, loggedInUser, false);

        File file;
        try {
            Study study = componentResult.getComponent().getStudy();
            file = ioUtils.getResultUploadFileSecurely(componentResult.getStudyResult().getId(), componentResultId, filename);
            if (!file.exists()) throw new IOException();
            studyLogger.log(study, loggedInUser, "Exported single result file");
        } catch (IOException e) {
            return notFound("File does not exist");
        }
        return ok(file);
    }

}
