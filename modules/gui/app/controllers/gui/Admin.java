package controllers.gui;

import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import auth.gui.AuthAction.Auth;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import general.common.Common;
import models.common.Study;
import models.common.User;
import models.common.User.Role;
import play.core.utils.HttpHeaderParameterEncoding;
import play.db.jpa.Transactional;
import play.http.HttpEntity;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.ResponseHeader;
import play.mvc.Result;
import services.gui.AdminService;
import auth.gui.AuthService;
import services.gui.BreadcrumbsService;
import services.gui.LogFileReader;
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller class around administration (updates are handled in Updates and user manager in Users)
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Admin extends Controller {

    private final AuthService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final StudyResultDao studyResultDao;
    private final UserDao userDao;
    private final LogFileReader logFileReader;
    private final AdminService adminService;

    @Inject
    Admin(AuthService authenticationService,
          BreadcrumbsService breadcrumbsService, StudyDao studyDao, StudyResultDao studyResultDao,
          UserDao userDao, LogFileReader logFileReader, AdminService adminService) {
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.studyResultDao = studyResultDao;
        this.userDao = userDao;
        this.logFileReader = logFileReader;
        this.adminService = adminService;
    }

    /**
     * Returns admin page
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result administration(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser();
        String breadcrumbs = breadcrumbsService.generateForAdministration(null);
        return ok(views.html.gui.admin.admin.render(request, loggedInUser, breadcrumbs, Helpers.isLocalhost()));
    }

    /**
     * Returns the content (all regular file's names) of the logs directory as JSON
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result listLogs() throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(Common.getBasepath() + "/logs/"))) {
            List<String> content = paths
                    .filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
            return ok(JsonUtils.asJsonNode(content));
        }
    }

    /**
     * For backward compatibility. Uses logs.
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result log(Integer lineLimit) {
        return logs("application.log", lineLimit, true);
    }

    /**
     * Returns the log file specified by 'filename'. If 'reverse' is true, it returns the content of the file in
     * reverse order and as 'Transfer-Encoding:chunked'. It limits the number of lines to the given lineLimit. If the
     * log file can't be read it still returns with OK but instead of the file content with an error message.
     * If 'reverse' is false it returns the file for download.
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result logs(String filename, Integer lineLimit, boolean reverse) {
        if (reverse) {
            return ok().chunked(logFileReader.read(filename, lineLimit)).as("text/plain; charset=UTF-8");
        } else {
            Path logPath = Paths.get(Common.getBasepath() + "/logs/" + filename);
            if (Files.notExists(logPath)) {
                return notFound();
            }
            Source<ByteString, ?> source = FileIO.fromPath(logPath);
            Optional<Long> contentLength = Optional.of(logPath.toFile().length());
            String filenameInHeader = HttpHeaderParameterEncoding.encode("filename", "jatos_logs_" + filename);
            return new Result(new ResponseHeader(200, Collections.emptyMap()),
                    new HttpEntity.Streamed(source, contentLength, Optional.of("application/octet-stream")))
                    .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; " + filenameInHeader);
        }
    }

    /**
     * Returns some status values
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result status() {
        return ok(adminService.getAdminStatus());
    }

    /**
     * Returns study admin page
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result studyAdmin(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser();
        String breadcrumbs = breadcrumbsService.generateForAdministration(BreadcrumbsService.STUDIES);
        return ok(views.html.gui.admin.studyAdmin.render(request, loggedInUser, breadcrumbs, Helpers.isLocalhost()));
    }

    /**
     * Returns table data for study admin page
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result allStudiesData() {
        List<Study> studyList = studyDao.findAll();
        boolean studyAssetsSizeFlag = Common.showStudyAssetsSizeInStudyAdmin();
        boolean resultDataSizeFlag = Common.showResultDataSizeInStudyAdmin();
        boolean resultFileSizeFlag = Common.showResultFileSizeInStudyAdmin();
        List<Map<String, Object>> studiesData = adminService.getStudiesData(studyList, studyAssetsSizeFlag,
                resultDataSizeFlag, resultFileSizeFlag);
        return ok(JsonUtils.asJsonNode(studiesData));
    }

    /**
     * Returns admin data for all studies that belong to the given user
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result studiesDataByUser(String username) {
        String normalizedUsername = User.normalizeUsername(username);
        User user = userDao.findByUsername(normalizedUsername);
        Set<Study> studyList = user.getStudyList();
        List<Map<String, Object>> studiesData = adminService.getStudiesData(studyList, true, true, true);
        return ok(JsonUtils.asJsonNode(studiesData));
    }

    /**
     * Returns the study assets folder size of one study
     */
    @Transactional
    @Auth
    public Result studyAssetsSize(Long studyId) {
        User loggedInUser = authenticationService.getLoggedInUser();
        Study study = studyDao.findById(studyId);
        if (study == null) return badRequest("Study does not exist");
        if (!study.hasUser(loggedInUser) && !loggedInUser.isAdmin()) return forbidden("No access for this user");
        return ok(JsonUtils.asJsonNode(adminService.getStudyAssetDirSize(study)));
    }

    /**
     * Returns the result data size of one study
     */
    @Transactional
    @Auth
    public Result resultDataSize(Long studyId) {
        User loggedInUser = authenticationService.getLoggedInUser();
        Study study = studyDao.findById(studyId);
        if (study == null) return badRequest("Study does not exist");
        if (!study.hasUser(loggedInUser) && !loggedInUser.isAdmin()) return forbidden("No access for this user");
        int studyResultCount = studyResultDao.countByStudy(study);
        return ok(JsonUtils.asJsonNode(adminService.getResultDataSize(study, studyResultCount)));
    }

    /**
     * Returns the size of all result files of one study
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result resultFileSize(Long studyId) {
        User loggedInUser = authenticationService.getLoggedInUser();
        Study study = studyDao.findById(studyId);
        if (study == null) return badRequest("Study does not exist");
        if (!study.hasUser(loggedInUser) && !loggedInUser.isAdmin()) return forbidden("No access for this user");
        int studyResultCount = studyResultDao.countByStudy(study);
        return ok(JsonUtils.asJsonNode(adminService.getResultFileSize(study, studyResultCount)));
    }

}
