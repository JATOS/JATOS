package controllers;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.Component;
import models.Study;
import models.User;
import persistance.ComponentDao;
import persistance.StudyDao;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.BreadcrumbsService;
import services.ComponentService;
import services.JatosGuiExceptionThrower;
import services.ResultRemover;
import services.StudyService;
import services.UserService;
import utils.JsonUtils;

import common.RequestScopeMessaging;

import controllers.actionannotations.AuthenticationAction.Authenticated;
import controllers.actionannotations.JatosGuiAction.JatosGui;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.JatosGuiException;
import exceptions.NotFoundException;

/**
 * Controller that deals with requests regarding ComponentResult.
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class ComponentResults extends Controller {

	private static final String CLASS_NAME = ComponentResults.class
			.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final ComponentService componentService;
	private final UserService userService;
	private final BreadcrumbsService breadcrumbsService;
	private final ResultRemover resultRemover;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;

	@Inject
	ComponentResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, ComponentService componentService,
			UserService userService, BreadcrumbsService breadcrumbsService,
			ResultRemover resultRemover, StudyDao studyDao,
			ComponentDao componentDao, JsonUtils jsonUtils) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.componentService = componentService;
		this.userService = userService;
		this.breadcrumbsService = breadcrumbsService;
		this.resultRemover = resultRemover;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
		this.jsonUtils = jsonUtils;
	}

	/**
	 * Shows a view with all component results of a component of a study.
	 */
	@Transactional
	public Result index(Long studyId, Long componentId, String errorMsg,
			int httpStatus) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			componentService.checkStandardForComponents(studyId, componentId,
					component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}

		RequestScopeMessaging.error(errorMsg);
		String breadcrumbs = breadcrumbsService.generateForComponent(study,
				component, BreadcrumbsService.RESULTS);
		return status(httpStatus,
				views.html.gui.result.componentResults.render(loggedInUser,
						breadcrumbs, study, component));
	}

	@Transactional
	public Result index(Long studyId, Long componentId, String errorMsg)
			throws JatosGuiException {
		return index(studyId, componentId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public Result index(Long studyId, Long componentId)
			throws JatosGuiException {
		return index(studyId, componentId, null, Http.Status.OK);
	}

	/**
	 * Ajax request
	 * 
	 * Removes all ComponentResults specified in the parameter. The parameter is
	 * a comma separated list of of ComponentResult IDs as a String.
	 */
	@Transactional
	public Result remove(String componentResultIds) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".remove: componentResultIds "
				+ componentResultIds + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			resultRemover.removeComponentResults(componentResultIds,
					loggedInUser);
		} catch (ForbiddenException | BadRequestException | NotFoundException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok().as("text/html");
	}

	/**
	 * Ajax request
	 * 
	 * Removes all ComponentResults of the given component and study.
	 */
	@Transactional
	public Result removeAllOfComponent(Long studyId, Long componentId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".removeAllOfComponent: studyId " + studyId
				+ ", " + "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			componentService.checkStandardForComponents(studyId, componentId,
					component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}

		try {
			resultRemover.removeAllComponentResults(component, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok().as("text/html");
	}

	/**
	 * Ajax request
	 * 
	 * Returns all ComponentResults as JSON for a given component.
	 */
	@Transactional
	public Result tableDataByComponent(Long studyId, Long componentId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".tableDataByComponent: studyId " + studyId
				+ ", " + "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			componentService.checkStandardForComponents(studyId, componentId,
					component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		String dataAsJson = null;
		try {
			dataAsJson = jsonUtils.allComponentResultsForUI(component);
		} catch (IOException e) {
			jatosGuiExceptionThrower.throwAjax(e,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

}
