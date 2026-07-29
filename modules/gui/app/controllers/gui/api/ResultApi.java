package controllers.gui.api;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import auth.gui.AuthAction.Auth;
import daos.common.ComponentResultDao;
import exceptions.common.NotFoundException;
import general.common.ApiEnvelope;
import general.common.Common;
import general.common.StudyLogger;
import http.common.Http.Context;
import models.common.ComponentResult;
import models.common.Study;
import models.common.User;
import play.core.utils.HttpHeaderParameterEncoding;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.AuthorizationService;
import services.gui.ComponentResultIdsExtractor;
import services.gui.ResultRemover;
import services.gui.ResultStreamer;
import utils.common.IOUtils;
import utils.common.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static actions.common.TransactionalAction.Mode.READ_ONLY;
import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;

@Singleton
public class ResultApi extends Controller {

    private final ComponentResultIdsExtractor componentResultIdsExtractor;
    private final ResultStreamer resultStreamer;
    private final ResultRemover resultRemover;
    private final AuthorizationService authorizationService;
    private final ComponentResultDao componentResultDao;
    private final IOUtils ioUtils;
    private final StudyLogger studyLogger;

    @Inject
    ResultApi(ComponentResultIdsExtractor componentResultIdsExtractor,
              ResultStreamer resultStreamer,
              ResultRemover resultRemover,
              AuthorizationService authorizationService,
              ComponentResultDao componentResultDao,
              IOUtils ioUtils,
              StudyLogger studyLogger) {
        this.componentResultIdsExtractor = componentResultIdsExtractor;
        this.resultStreamer = resultStreamer;
        this.resultRemover = resultRemover;
        this.authorizationService = authorizationService;
        this.componentResultDao = componentResultDao;
        this.ioUtils = ioUtils;
        this.studyLogger = studyLogger;
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
        Context.current().response().setHeader(CONTENT_DISPOSITION, cdHeader);
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
            Context.current().response().setHeader(CONTENT_DISPOSITION, cdHeader);
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
                Context.current().response().setHeader(CONTENT_DISPOSITION, cdHeader);
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
            Context.current().response().setHeader(CONTENT_DISPOSITION, cdHeader);
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
        Context.current().response().setHeader(CONTENT_DISPOSITION, cdHeader);
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
    @TransactionalAction.Transactional(READ_ONLY)
    public Result exportSingleResultFile(Long componentResultId, String filename) throws IOException {
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
