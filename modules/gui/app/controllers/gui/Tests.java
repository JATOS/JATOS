package controllers.gui;

import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.UserDao;
import general.common.Common;
import play.Logger;
import play.Logger.ALogger;
import play.cache.CacheApi;
import play.cache.NamedCache;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.LegacyWebSocket;
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

    private static final ALogger LOGGER = Logger.of(Tests.class);

    private final UserDao userDao;
    private final JsonUtils jsonUtils;
    private final CacheApi cache;

    @Inject
    Tests(UserDao userDao, JsonUtils jsonUtils,
            @NamedCache("user-session-cache") CacheApi cache) {
        this.userDao = userDao;
        this.jsonUtils = jsonUtils;
        this.cache = cache;
    }

    public Result test() {
        LOGGER.debug(".test");
        return ok(views.html.gui.test.render());
    }

    @Transactional
    public Result testDatabase() {
        LOGGER.debug(".testDatabase");
        try {
            userDao.findByEmail(UserService.ADMIN_EMAIL);
        } catch (Exception e) {
            return badRequest();
        }
        return ok();
    }

    public Result testStudyAssetsRootFolder() {
        LOGGER.debug(".testStudyAssetsRootFolder");
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
        LOGGER.debug(".testCache");
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
        LOGGER.debug(".testJsonSerialization");
        try {
            JsonUtils.asStringForDB("{\"test\":\"test\"}");
        } catch (Exception e) {
            return badRequest();
        }
        return ok();
    }

    @SuppressWarnings("deprecation")
    public LegacyWebSocket<String> testWebSocket() {
        LOGGER.debug(".testWebSocket");
        return WebSocket.whenReady((in, out) -> {
            in.onMessage(out::write);
        });
    }

}
