package controllers.gui.api;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import exceptions.common.NotFoundException;
import general.common.ApiEnvelope;
import general.common.Common;
import http.common.Http;
import http.common.HttpUtils;
import json.common.DirectoryStructureToJson;
import play.core.utils.HttpHeaderParameterEncoding;
import play.http.HttpEntity;
import play.mvc.Controller;
import play.mvc.ResponseHeader;
import play.mvc.Result;
import services.gui.AdminService;
import services.gui.LogFileReader;
import utils.common.IOUtils;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static models.common.User.Role.ADMIN;

public class AdminApi extends Controller {

    private final AdminService adminService;
    private final IOUtils ioUtils;
    private final LogFileReader logFileReader;

    @Inject
    AdminApi(AdminService adminService,
             IOUtils ioUtils,
             LogFileReader logFileReader) {
        this.adminService = adminService;
        this.ioUtils = ioUtils;
        this.logFileReader = logFileReader;
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
        }

        Path logPath = Path.of(Common.getLogsPath(), filename);
        return ok().sendPath(logPath, false, Optional.of("jatos_logs_" + filename))
                .as("application/octet-stream");
    }

}
