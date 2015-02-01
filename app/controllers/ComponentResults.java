package controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.Breadcrumbs;
import services.ComponentService;
import services.ErrorMessages;
import services.JatosGuiExceptionThrower;
import services.Messages;
import services.ResultService;
import services.StudyService;
import services.UserService;
import utils.DateUtils;
import utils.IOUtils;
import utils.JsonUtils;
import utils.PersistanceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import common.JatosGuiAction;
import daos.ComponentDao;
import daos.ComponentResultDao;
import daos.StudyDao;
import exceptions.JatosGuiException;

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

	private final PersistanceUtils persistanceUtils;
	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final ComponentService componentService;
	private final UserService userService;
	private final ResultService resultService;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;
	private final ComponentResultDao componentResultDao;
	private final JsonUtils jsonUtils;

	@Inject
	public ComponentResults(PersistanceUtils persistanceUtils,
			JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, ComponentService componentService,
			UserService userService, ResultService resultService,
			StudyDao studyDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils) {
		this.persistanceUtils = persistanceUtils;
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
		componentService.checkStandardForComponents(studyId, componentId,
				study, loggedInUser, component);

		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForComponent(study,
				component, Breadcrumbs.RESULTS);
		return status(httpStatus,
				views.html.jatos.result.componentResults.render(studyList,
						loggedInUser, breadcrumbs, messages, study, component));
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
		List<ComponentResult> componentResultList = getAllComponentResults(componentResultIdList);
		checkAllComponentResults(componentResultList, loggedInUser, true);

		for (ComponentResult componentResult : componentResultList) {
			persistanceUtils.removeComponentResult(componentResult);
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
		Logger.info(CLASS_NAME + ".tableDataByComponent: studyId " + studyId
				+ ", " + "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		componentService.checkStandardForComponents(studyId, componentId,
				study, loggedInUser, component);
		String dataAsJson = null;
		try {
			dataAsJson = jsonUtils.allComponentResultsForUI(component);
		} catch (IOException e) {
			return internalServerError(ErrorMessages.PROBLEM_GENERATING_JSON_DATA);
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
		response().discardCookie(ImportExport.JQDOWNLOAD_COOKIE_NAME);
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		List<Long> componentResultIdList = resultService
				.extractResultIds(componentResultIds);
		List<ComponentResult> componentResultList = getAllComponentResults(componentResultIdList);
		checkAllComponentResults(componentResultList, loggedInUser, true);
		String componentResultDataAsStr = getComponentResultData(componentResultList);

		response().setContentType("application/x-download");
		String filename = "results_" + DateUtils.getDateForFile(new Date())
				+ "." + IOUtils.TXT_FILE_SUFFIX;
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		// Set cookie for jQuery.fileDownload plugin
		response().setCookie(ImportExport.JQDOWNLOAD_COOKIE_NAME,
				ImportExport.JQDOWNLOAD_COOKIE_CONTENT);
		return ok(componentResultDataAsStr);
	}

	/**
	 * Put all ComponentResult's data into a String each in a separate line.
	 */
	private String getComponentResultData(
			List<ComponentResult> componentResultList) throws JatosGuiException {
		StringBuilder sb = new StringBuilder();
		Iterator<ComponentResult> iterator = componentResultList.iterator();
		while (iterator.hasNext()) {
			ComponentResult componentResult = iterator.next();
			String data = componentResult.getData();
			if (data != null) {
				sb.append(data);
				if (iterator.hasNext()) {
					sb.append("\n");
				}
			}
		}
		return sb.toString();
	}

	private List<ComponentResult> getAllComponentResults(
			List<Long> componentResultIdList) throws JatosGuiException {
		List<ComponentResult> componentResultList = new ArrayList<>();
		for (Long componentResultId : componentResultIdList) {
			ComponentResult componentResult = componentResultDao
					.findById(componentResultId);
			if (componentResult == null) {
				String errorMsg = ErrorMessages
						.componentResultNotExist(componentResultId);
				jatosGuiExceptionThrower.throwAjax(errorMsg,
						Http.Status.NOT_FOUND);
			}
			componentResultList.add(componentResult);
		}
		return componentResultList;
	}

	private void checkAllComponentResults(
			List<ComponentResult> componentResultList, UserModel loggedInUser,
			boolean studyMustNotBeLocked) throws JatosGuiException {
		for (ComponentResult componentResult : componentResultList) {
			ComponentModel component = componentResult.getComponent();
			StudyModel study = component.getStudy();
			componentService.checkStandardForComponents(study.getId(),
					component.getId(), study, loggedInUser, component);
			if (studyMustNotBeLocked) {
				studyService.checkStudyLocked(study);
			}
		}
	}
}
