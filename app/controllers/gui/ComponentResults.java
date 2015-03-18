package controllers.gui;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import models.ComponentModel;
import models.ComponentResult;
import models.StudyModel;
import models.UserModel;
import persistance.ComponentDao;
import persistance.ComponentResultDao;
import persistance.StudyDao;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.RequestScopeMessaging;
import services.gui.Breadcrumbs;
import services.gui.ComponentService;
import services.gui.ImportExportService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.MessagesStrings;
import services.gui.ResultService;
import services.gui.StudyService;
import services.gui.UserService;
import utils.DateUtils;
import utils.IOUtils;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.gui.JatosGuiException;

/**
 * Controller that deals with requests regarding ComponentResult.
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
@Singleton
public class ComponentResults extends Controller {

	private static final String CLASS_NAME = ComponentResults.class
			.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final ComponentService componentService;
	private final UserService userService;
	private final ResultService resultService;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;
	private final ComponentResultDao componentResultDao;

	@Inject
	ComponentResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, ComponentService componentService,
			UserService userService, ResultService resultService,
			StudyDao studyDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.componentService = componentService;
		this.userService = userService;
		this.resultService = resultService;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
		this.componentResultDao = componentResultDao;
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}
		componentService.checkStandardForComponents(studyId, componentId,
				loggedInUser, component);

		RequestScopeMessaging.error(errorMsg);
		String breadcrumbs = Breadcrumbs.generateForComponent(study, component,
				Breadcrumbs.RESULTS);
		return status(httpStatus,
				views.html.gui.result.componentResults.render(studyList,
						loggedInUser, breadcrumbs, study, component));
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
	 * Removes all ComponentResults specified in the parameter.
	 */
	@Transactional
	public Result remove(String componentResultIds) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".remove: componentResultIds "
				+ componentResultIds + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		List<Long> componentResultIdList = resultService
				.extractResultIds(componentResultIds);
		List<ComponentResult> componentResultList = resultService
				.getAllComponentResults(componentResultIdList);
		try {
			resultService.checkAllComponentResults(componentResultList,
					loggedInUser, true);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		for (ComponentResult componentResult : componentResultList) {
			componentResultDao.remove(componentResult);
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}
		componentService.checkStandardForComponents(studyId, componentId,
				loggedInUser, component);
		String dataAsJson = null;
		try {
			dataAsJson = jsonUtils.allComponentResultsForUI(component);
		} catch (IOException e) {
			return internalServerError(MessagesStrings.PROBLEM_GENERATING_JSON_DATA);
		}
		return ok(dataAsJson);
	}

	/**
	 * Ajax request
	 * 
	 * Returns all result data of ComponentResults as text for a given
	 * component.
	 */
	@Transactional
	public Result exportData(String componentResultIds)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".exportData: componentResultIds "
				+ componentResultIds + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(ImportExportService.JQDOWNLOAD_COOKIE_NAME);
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		List<Long> componentResultIdList = resultService
				.extractResultIds(componentResultIds);
		List<ComponentResult> componentResultList = resultService
				.getAllComponentResults(componentResultIdList);
		try {
			resultService.checkAllComponentResults(componentResultList,
					loggedInUser, true);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		String componentResultDataAsStr = resultService
				.getComponentResultData(componentResultList);

		response().setContentType("application/x-download");
		String filename = "results_" + DateUtils.getDateForFile(new Date())
				+ "." + IOUtils.TXT_FILE_SUFFIX;
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		// Set cookie for jQuery.fileDownload plugin
		response().setCookie(ImportExportService.JQDOWNLOAD_COOKIE_NAME,
				ImportExportService.JQDOWNLOAD_COOKIE_CONTENT);
		return ok(componentResultDataAsStr);
	}

}
