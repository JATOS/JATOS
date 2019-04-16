package controllers.gui;

import akka.stream.javadsl.Flow;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.UserDao;
import general.common.Common;
import play.cache.NamedCache;
import play.cache.SyncCacheApi;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import services.gui.UserService;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;

/**
 * Controller with endpoints used by /jatos/test. Each endpoint test a different
 * aspect of JATOS.
 *
 * @author Kristian Lange (2017)
 */
@GuiAccessLogging
@Singleton
public class Tests extends Controller {

    private final UserDao      userDao;
    private final SyncCacheApi cache;

    @Inject
    Tests(UserDao userDao, @NamedCache("user-session-cache") SyncCacheApi cache) {
        this.userDao = userDao;
        this.cache = cache;
    }

    public Result test() {
        return ok(views.html.gui.test.render());
    }

    @Transactional
    public Result testDatabase() {
        try {
            userDao.findByEmail(UserService.ADMIN_EMAIL);
        } catch (Exception e) {
            return badRequest();
        }
        return ok();
    }

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

    public Result testCache() {
        try {
            cache.set("test", "testValue");
            String value = cache.get("test");
            if (!value.equals("testValue")) {
                return badRequest();
            }
        } catch (Exception e) {
            return badRequest();
        }
        return ok();
    }

    public Result testJsonSerialization() {
        try {
            JsonUtils.asStringForDB("{\"test\":\"test\"}");
        } catch (Exception e) {
            return badRequest();
        }
        return ok();
    }

    public WebSocket testWebSocket() {
        return WebSocket.Text.accept(request -> {
            // send response back to client
            return Flow.<String>create().map(msg -> msg);
        });
    }

}
