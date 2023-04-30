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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;

import static auth.gui.AuthAction.Auth;

/**
 * Controller with endpoints used by /jatos/test. Each endpoint test a different
 * aspect of JATOS.
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
    public Result testStudyAssetsRootFolder() {
        try {
            File studyAssetsRoot = new File(Common.getStudyAssetsRootPath());
            if (!studyAssetsRoot.canRead()) {
                return badRequest();
            }
            if (!studyAssetsRoot.canWrite()) {
                return badRequest();
            }
            if (!studyAssetsRoot.isDirectory()) {
                return badRequest();
            }
        } catch (Exception e) {
            return badRequest();
        }
        return ok();
    }

    @Transactional
    @Auth(User.Role.ADMIN)
    public WebSocket testWebSocket() {
        return WebSocket.Text.accept(request -> {
            // send response back to client
            return Flow.<String>create().map(msg -> msg);
        });
    }

}
