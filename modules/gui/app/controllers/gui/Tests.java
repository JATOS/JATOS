package controllers.gui;

import akka.stream.javadsl.Flow;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.UserDao;
import general.common.Common;
import models.common.User;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import services.gui.UserService;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static auth.gui.AuthAction.Auth;

/**
 * Controller with endpoints used by /jatos/test. Each endpoint tests a different aspect of JATOS.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Tests extends Controller {

    private final UserDao userDao;

    @Inject
    Tests(UserDao userDao) {
        this.userDao = userDao;
    }

    @Transactional
    @Auth(User.Role.ADMIN)
    public Result test() {
        return ok(views.html.gui.admin.test.render());
    }

    @Transactional
    @Auth(User.Role.ADMIN)
    public Result testDatabase() {
        try {
            userDao.findByUsername(UserService.ADMIN_USERNAME);
        } catch (Exception e) {
            return badRequest();
        }
        return ok();
    }

    @Transactional
    @Auth(User.Role.ADMIN)
    public Result testFolderAccess() {
        Map<String, Boolean> folderAccessResults = new HashMap<>();
        folderAccessResults.put("studyAssetsRoot", testFolder(Common.getStudyAssetsRootPath()));
        folderAccessResults.put("resultUploads", testFolder(Common.getResultUploadsPath()));
        folderAccessResults.put("logs", testFolder(Common.getLogsPath()));
        folderAccessResults.put("studyLogs", testFolder(Common.getStudyLogsPath()));
        folderAccessResults.put("tmp", testFolder(Common.getTmpDir()));
        return ok(JsonUtils.asJsonNode(folderAccessResults));
    }

    private boolean testFolder(String path) {
        try {
            File studyAssetsRoot = new File(path);
            if (!studyAssetsRoot.canRead()
                    || !studyAssetsRoot.canWrite()
                    || !studyAssetsRoot.isDirectory()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Transactional
    @Auth(User.Role.ADMIN)
    public WebSocket testWebSocket() {
        return WebSocket.Text.accept(request -> {
            // send response back to a client
            return Flow.<String>create().map(msg -> msg);
        });
    }

}
