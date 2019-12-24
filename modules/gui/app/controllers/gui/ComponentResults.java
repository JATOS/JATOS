package controllers.gui;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import models.common.Component;
import models.common.Study;
import models.common.User;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import scala.Option;
import services.gui.*;
import utils.common.HttpUtils;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Controller that deals with requests regarding ComponentResult.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class ComponentResults extends Controller {

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final AuthenticationService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final ResultRemover resultRemover;
    private final ResultService resultService;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final ComponentResultDao componentResultDao;
    private final IOUtils ioUtils;

    @Inject
    ComponentResults(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
            AuthenticationService authenticationService, BreadcrumbsService breadcrumbsService,
            ResultRemover resultRemover, ResultService resultService, StudyDao studyDao,
            ComponentDao componentDao, ComponentResultDao componentResultDao, IOUtils ioUtils) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.resultRemover = resultRemover;
        this.resultService = resultService;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.componentResultDao = componentResultDao;
        this.ioUtils = ioUtils;
    }

    /**
     * Shows a view with all component results of a component of a study.
     */
    @Transactional
    @Authenticated
    public Result componentResults(Long studyId, Long componentId, Option<Integer> max) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwHome(e);
        }

        String breadcrumbs = breadcrumbsService.generateForComponent(study, component, BreadcrumbsService.RESULTS);
        return ok(views.html.gui.result.componentResults
                .render(loggedInUser, breadcrumbs, HttpUtils.isLocalhost(), study, component, max));
    }

    /**
     * Ajax POST request
     * <p>
     * Removes all ComponentResults specified in the parameter. The parameter is
     * a comma separated list of of ComponentResult IDs as a String.
     */
    @Transactional
    @Authenticated
    public Result remove() throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> componentResultIdList = new ArrayList<>();
        request().body().asJson().get("resultIds").forEach(node -> componentResultIdList.add(node.asLong()));
        try {
            // Permission check is done in service for each result individually
            resultRemover.removeComponentResults(componentResultIdList, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException | IOException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax request with chunked streaming (reduces memory usage)
     *
     * Returns all ComponentResults as JSON for a given component. It gets up to 'max' results - or if 'max' is
     * undefined it gets all. If their is a problem during retrieval it returns nothing.
     */
    @Transactional
    @Authenticated
    public Result tableDataByComponent(Long studyId, Long componentId, Option<Integer> max) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        int resultCount = componentResultDao.countByComponent(component);
        int bufferSize = max.isDefined() ? max.get() : resultCount;
        Source<ByteString, ?> source = Source.<ByteString>actorRef(bufferSize, OverflowStrategy.fail())
                .mapMaterializedValue(sourceActor -> {
                    CompletableFuture.runAsync(() -> {
                        resultService.fetchComponentResultsAndWriteIntoActor(sourceActor, loggedInUser, max,
                                () -> componentResultDao.findAllByComponentScrollable(component));
                        sourceActor.tell(new Status.Success(NotUsed.getInstance()), ActorRef.noSender());
                    });
                    return sourceActor;
                });
        return ok().chunked(source).as("text/html; charset=utf-8");
    }

}
