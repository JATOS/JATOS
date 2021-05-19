package controllers.gui;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import models.common.User;
import models.common.User.Role;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.AdminService;
import services.gui.AuthenticationService;
import services.gui.BreadcrumbsService;
import services.gui.LogFileReader;
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller class around administration (updates are handled in Updates and user manager in Users)
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class Admin extends Controller {

    private final AuthenticationService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final StudyResultDao studyResultDao;
    private final UserDao userDao;
    private final WorkerDao workerDao;
    private final LogFileReader logFileReader;
    private final AdminService adminService;

    @Inject
    Admin(AuthenticationService authenticationService,
            BreadcrumbsService breadcrumbsService, StudyDao studyDao, StudyResultDao studyResultDao,
            UserDao userDao, WorkerDao workerDao, LogFileReader logFileReader, AdminService adminService) {
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.studyResultDao = studyResultDao;
        this.userDao = userDao;
        this.workerDao = workerDao;
        this.logFileReader = logFileReader;
        this.adminService = adminService;
    }

    /**
     * Returns admin page
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result administration(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser();
        String breadcrumbs = breadcrumbsService.generateForAdministration(null);
        return ok(views.html.gui.admin.admin.render(request, loggedInUser, breadcrumbs, Helpers.isLocalhost()));
    }

    /**
     * Returns the content of a log file in reverse order and as 'Transfer-Encoding:chunked'. It does so only if an
     * user with Role ADMIN is logged in. It limits the number of lines to the given lineLimit. If the log file can't
     * be read it still returns with OK but instead of the file content with an error message.
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result log(String filename, Integer lineLimit) {
        if (!filename.matches("^application.log|update.log|loader.log$")) return badRequest();
        return ok().chunked(logFileReader.read(filename, lineLimit)).as("text/plain; charset=utf-8");
    }

    /**
     * Ajax request
     *
     * Returns some status values
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result status() {
        Map<String, Object> map = new HashMap<>();
        map.put("studyCount", studyDao.count());
        map.put("studyResultCount", studyResultDao.count());
        map.put("workerCount", workerDao.count());
        map.put("userCount", userDao.count());
        map.put("serverTime", Helpers.formatTimestamp(new Date()));
        map.put("latestUsers", adminService.getLatestUsers(10));
        map.put("latestStudyRuns", adminService.getLatestStudyRuns(10));
        return ok(JsonUtils.asJson(map));
    }

    /**
     * Returns study admin page
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result studyAdmin(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser();
        String breadcrumbs = breadcrumbsService.generateForAdministration(BreadcrumbsService.STUDIES);
        return ok(views.html.gui.admin.studyAdmin.render(request, loggedInUser, breadcrumbs, Helpers.isLocalhost()));
    }

    /**
     * Ajax request
     *
     * Returns table data for study admin page
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result allStudiesData() {
        return ok(JsonUtils.asJson(adminService.getStudiesData(studyDao.findAll())));
    }

    /**
     * Ajax request
     *
     * Returns admin data for all studies that belong to the given user
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result studiesDataByUser(String username) {
        String normalizedUsername = User.normalizeUsername(username);
        User user = userDao.findByUsername(normalizedUsername);
        return ok(JsonUtils.asJson(adminService.getStudiesData(user.getStudyList())));
    }

}
