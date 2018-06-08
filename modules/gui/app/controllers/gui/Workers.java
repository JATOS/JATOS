package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.BatchDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.*;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URL;
import java.util.Map;
import java.util.Set;

/**
 * Controller that handles all worker actions in JATOS' GUI
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class Workers extends Controller {

    private static final ALogger LOGGER = Logger.of(Workers.class);

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final AuthenticationService authenticationService;
    private final WorkerService workerService;
    private final BreadcrumbsService breadcrumbsService;
    private final JsonUtils jsonUtils;
    private final StudyDao studyDao;
    private final BatchDao batchDao;

    @Inject
    Workers(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
            AuthenticationService authenticationService,
            WorkerService workerService, BreadcrumbsService breadcrumbsService,
            JsonUtils jsonUtils, StudyDao studyDao, BatchDao batchDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.authenticationService = authenticationService;
        this.workerService = workerService;
        this.breadcrumbsService = breadcrumbsService;
        this.jsonUtils = jsonUtils;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
    }



}
