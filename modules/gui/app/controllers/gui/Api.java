package controllers.gui;

import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import general.common.RequestScope;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.IOException;

import static controllers.gui.actionannotations.ApiTokenAuthAction.API_TOKEN;
import static controllers.gui.actionannotations.ApiTokenAuthAction.ApiTokenAuth;

/**
 * JATOS API Controller: interface for all requests possible via JATOS' API
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Api extends Controller {

    private final Admin admin;
    private final ImportExport importExport;

    @Inject
    Api(Admin admin, ImportExport importExport) {
        this.admin = admin;
        this.importExport = importExport;
    }

    @ApiTokenAuth
    public Result testToken() {
        return ok(JsonUtils.asJson(RequestScope.get(API_TOKEN)));
    }

    @ApiTokenAuth
    public Result status() {
        return admin.status();
    }

    @ApiTokenAuth
    @Transactional
    public Result exportDataOfStudyResults(Http.Request request) throws IOException {
        return importExport.exportDataOfStudyResults(request);
    }

}
