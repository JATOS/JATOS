package controllers.gui;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiExceptionAction.GuiExceptionCatching;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.Checker;
import services.gui.JatosGuiExceptionThrower;
import services.gui.ResultRemover;
import services.gui.UserService;
import utils.common.JsonUtils;

/**
 * Controller that deals with requests regarding ComponentResult.
 * 
 * @author Kristian Lange
 */
@GuiExceptionCatching
@GuiAccessLogging
@Authenticated
@Singleton
public class ComponentResults extends Controller {

	private static final ALogger LOGGER = Logger.of(ComponentResults.class);

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final Checker checker;
	private final UserService userService;
	private final BreadcrumbsService breadcrumbsService;
	private final ResultRemover resultRemover;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;
	private final ComponentResultDao componentResultDao;

	@Inject
	ComponentResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			Checker checker, UserService userService,
			BreadcrumbsService breadcrumbsService, ResultRemover resultRemover,
			JsonUtils jsonUtils, StudyDao studyDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.checker = checker;
		this.userService = userService;
		this.breadcrumbsService = breadcrumbsService;
		this.resultRemover = resultRemover;
		this.jsonUtils = jsonUtils;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
		this.componentResultDao = componentResultDao;
	}

	/**
	 * Shows a view with all component results of a component of a study.
	 */
	@Transactional
	public Result componentResults(Long studyId, Long componentId,
			String errorMsg, int httpStatus) throws JatosGuiException {
		LOGGER.info(".componentResults: studyId " + studyId + ", "
				+ "componentId " + componentId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForComponents(studyId, componentId, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}

		RequestScopeMessaging.error(errorMsg);
		String breadcrumbs = breadcrumbsService.generateForComponent(study,
				component, BreadcrumbsService.RESULTS);
		return status(httpStatus, views.html.gui.result.componentResults
				.render(loggedInUser, breadcrumbs, study, component));
	}

	@Transactional
	public Result componentResults(Long studyId, Long componentId,
			String errorMsg) throws JatosGuiException {
		return componentResults(studyId, componentId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public Result componentResults(Long studyId, Long componentId)
			throws JatosGuiException {
		return componentResults(studyId, componentId, null, Http.Status.OK);
	}

	/**
	 * Ajax DELETE request
	 * 
	 * Removes all ComponentResults specified in the parameter. The parameter is
	 * a comma separated list of of ComponentResult IDs as a String.
	 */
	@Transactional
	public Result remove(String componentResultIds) throws JatosGuiException {
		LOGGER.info(".remove: componentResultIds " + componentResultIds);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			// Permission check is done in service for each result individually
			resultRemover.removeComponentResults(componentResultIds,
					loggedInUser);
		} catch (ForbiddenException | BadRequestException
				| NotFoundException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok();
	}

	/**
	 * Ajax request
	 * 
	 * Removes all ComponentResults of the given component and study.
	 */
	@Transactional
	public Result removeAllOfComponent(Long studyId, Long componentId)
			throws JatosGuiException {
		LOGGER.info(".removeAllOfComponent: studyId " + studyId + ", "
				+ "componentId " + componentId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForComponents(studyId, componentId, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}

		try {
			resultRemover.removeAllComponentResults(component, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok();
	}

	/**
	 * Ajax request
	 * 
	 * Returns all ComponentResults as JSON for a given component.
	 */
	@Transactional
	public Result tableDataByComponent(Long studyId, Long componentId)
			throws JatosGuiException {
		LOGGER.info(".tableDataByComponent: studyId " + studyId + ", "
				+ "componentId " + componentId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForComponents(studyId, componentId, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		JsonNode dataAsJson = null;
		try {
			List<ComponentResult> componentResultList = componentResultDao
					.findAllByComponent(component);
			dataAsJson = jsonUtils
					.allComponentResultsForUI(componentResultList);
		} catch (IOException e) {
			jatosGuiExceptionThrower.throwAjax(e,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

}
