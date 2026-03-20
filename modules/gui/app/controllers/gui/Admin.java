package controllers.gui;

import auth.gui.AuthAction.Auth;
import auth.gui.AuthService;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import general.common.Common;
import models.common.Study;
import models.common.User;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.AdminService;
import services.gui.BreadcrumbsService;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;
import static models.common.User.Role.*;

/**
 * Controller class around administration (updates are handled in Updates and user manager in Users)
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Admin extends Controller {

    private final AuthService authService;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final StudyResultDao studyResultDao;
    private final UserDao userDao;
    private final AdminService adminService;

    @Inject
    Admin(AuthService authService, BreadcrumbsService breadcrumbsService, StudyDao studyDao,
            StudyResultDao studyResultDao, UserDao userDao, AdminService adminService) {
        this.authService = authService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.studyResultDao = studyResultDao;
        this.userDao = userDao;
        this.adminService = adminService;
    }

    /**
     * Returns admin page
     */
    @Transactional
    @Auth(roles = ADMIN)
    @SaveLastVisitedPageUrl
    public Result administration(Http.Request request) {
        User signedinUser = authService.getSignedinUser();
        String breadcrumbs = breadcrumbsService.generateForAdministration(null);
        return ok(views.html.gui.admin.admin.render(request, signedinUser, breadcrumbs));
    }

    /**
     * Returns the content (all regular file's names) of the logs directory as JSON
     */
    @Transactional
    @Auth(roles = ADMIN)
    public Result listLogs() throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(Common.getLogsPath()))) {
            List<String> content = paths
                    .filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
            return ok(JsonUtils.asJsonNode(content));
        }
    }

    /**
     * Returns some status values
     */
    @Transactional
    @Auth(roles = ADMIN)
    public Result status() {
        return ok(adminService.getAdminStatus());
    }

    /**
     * Returns study manager page
     */
    @Transactional
    @Auth(roles = ADMIN)
    @SaveLastVisitedPageUrl
    public Result studyManager(Http.Request request) {
        User signedinUser = authService.getSignedinUser();
        String breadcrumbs = breadcrumbsService.generateForAdministration(BreadcrumbsService.STUDY_MANAGER);
        return ok(views.html.gui.admin.studyManager.render(request, signedinUser, breadcrumbs));
    }

    /**
     * Returns table data for study manager page
     */
    @Transactional
    @Auth(roles = ADMIN)
    public Result allStudiesData() {
        List<Study> studyList = studyDao.findAll();
        boolean studyAssetsSizeFlag = Common.showStudyAssetsSizeInStudyManager();
        boolean resultDataSizeFlag = Common.showResultDataSizeInStudyManager();
        boolean resultFileSizeFlag = Common.showResultFileSizeInStudyManager();
        List<Map<String, Object>> studiesData = adminService.getStudiesData(studyList, studyAssetsSizeFlag,
                resultDataSizeFlag, resultFileSizeFlag);
        return ok(JsonUtils.asJsonNode(studiesData));
    }

    /**
     * Returns admin data for all studies that belong to the given user
     */
    @Transactional
    @Auth(roles = ADMIN)
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
    @Auth(roles = {VIEWER, USER, ADMIN})
    public Result studyAssetsSize(Long studyId) {
        User signedinUser = authService.getSignedinUser();
        Study study = studyDao.findById(studyId);
        if (study == null) return badRequest("Study does not exist");
        if (!study.hasUser(signedinUser) && !signedinUser.isAdmin()) return forbidden("No access for this user");
        return ok(JsonUtils.asJsonNode(adminService.getStudyAssetDirSize(study)));
    }

    /**
     * Returns the result data size of one study
     */
    @Transactional
    @Auth(roles = {VIEWER, USER, ADMIN})
    public Result resultDataSize(Long studyId) {
        User signedinUser = authService.getSignedinUser();
        Study study = studyDao.findById(studyId);
        if (study == null) return badRequest("Study does not exist");
        if (!study.hasUser(signedinUser) && !signedinUser.isAdmin()) return forbidden("No access for this user");
        int studyResultCount = studyResultDao.countByStudy(study);
        return ok(JsonUtils.asJsonNode(adminService.getResultDataSize(study, studyResultCount)));
    }

    /**
     * Returns the size of all result files of one study
     */
    @Transactional
    @Auth(roles = {VIEWER, USER, ADMIN})
    public Result resultFileSize(Long studyId) {
        User signedinUser = authService.getSignedinUser();
        Study study = studyDao.findById(studyId);
        if (study == null) return badRequest("Study does not exist");
        if (!study.hasUser(signedinUser) && !signedinUser.isAdmin()) return forbidden("No access for this user");
        int studyResultCount = studyResultDao.countByStudy(study);
        return ok(JsonUtils.asJsonNode(adminService.getResultFileSize(study, studyResultCount)));
    }

}
