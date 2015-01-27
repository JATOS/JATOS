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
import services.Breadcrumbs;
import services.DateUtils;
import services.ErrorMessages;
import services.IOUtils;
import services.JsonUtils;
import services.Messages;
import services.PersistanceUtils;
import exceptions.ResultException;

/**
 * Controller that deals with requests regarding ComponentResult.
 * 
 * @author Kristian Lange
 */
public class ComponentResults extends Controller {

	private static final String CLASS_NAME = ComponentResults.class
			.getSimpleName();

	/**
	 * Shows a view with all component results of a component of a study.
	 */
	@Transactional
	public static Result index(Long studyId, Long componentId, String errorMsg,
			int httpStatus) throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForComponent(study,
				component, Breadcrumbs.RESULTS);
		return status(httpStatus,
				views.html.jatos.result.componentResults.render(studyList,
						loggedInUser, breadcrumbs, messages, study, component));
	}

	@Transactional
	public static Result index(Long studyId, Long componentId, String errorMsg)
			throws ResultException {
		return index(studyId, componentId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public static Result index(Long studyId, Long componentId)
			throws ResultException {
		return index(studyId, componentId, null, Http.Status.OK);
	}

	/**
	 * Ajax request
	 * 
	 * Removes all ComponentResults specified in the parameter. 
	 */
	@Transactional
	public static Result remove(String componentResultIds)
			throws ResultException {
		Logger.info(CLASS_NAME + ".remove: componentResultIds "
				+ componentResultIds + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		List<Long> componentResultIdList = ControllerUtils
				.extractResultIds(componentResultIds);
		List<ComponentResult> componentResultList = getAllComponentResults(componentResultIdList);
		checkAllComponentResults(componentResultList, loggedInUser, true);

		for (ComponentResult componentResult : componentResultList) {
			PersistanceUtils.removeComponentResult(componentResult);
		}
		return ok();
	}

	/**
	 * Ajax request
	 * 
	 * Returns all ComponentResults as JSON for a given component.
	 */
	@Transactional
	public static Result tableDataByComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".tableDataByComponent: studyId " + studyId
				+ ", " + "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);
		String dataAsJson = null;
		try {
			dataAsJson = JsonUtils.allComponentResultsForUI(component);
		} catch (IOException e) {
			return internalServerError(ErrorMessages.PROBLEM_GENERATING_JSON_DATA);
		}
		return ok(dataAsJson);
	}

	/**
	 * Ajax request
	 * 
	 * Returns all ComponentResults as text for a given component.
	 */
	@Transactional
	public static Result exportData(String componentResultIds)
			throws ResultException {
		Logger.info(CLASS_NAME + ".exportData: componentResultIds "
				+ componentResultIds + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		List<Long> componentResultIdList = ControllerUtils
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
		response().setCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME,
				ControllerUtils.JQDOWNLOAD_COOKIE_CONTENT);
		return ok(componentResultDataAsStr);
	}

	/**
	 * Put all ComponentResult's data into a String each in a separate line.
	 */
	private static String getComponentResultData(
			List<ComponentResult> componentResultList) throws ResultException {
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

	private static List<ComponentResult> getAllComponentResults(
			List<Long> componentResultIdList) throws ResultException {
		List<ComponentResult> componentResultList = new ArrayList<>();
		for (Long componentResultId : componentResultIdList) {
			ComponentResult componentResult = ComponentResult
					.findById(componentResultId);
			if (componentResult == null) {
				String errorMsg = ErrorMessages
						.componentResultNotExist(componentResultId);
				ControllerUtils.throwAjaxResultException(errorMsg,
						Http.Status.NOT_FOUND);
			}
			componentResultList.add(componentResult);
		}
		return componentResultList;
	}

	private static void checkAllComponentResults(
			List<ComponentResult> componentResultList, UserModel loggedInUser,
			boolean studyMustNotBeLocked) throws ResultException {
		for (ComponentResult componentResult : componentResultList) {
			ComponentModel component = componentResult.getComponent();
			StudyModel study = component.getStudy();
			ControllerUtils.checkStandardForComponents(study.getId(),
					component.getId(), study, loggedInUser, component);
			if (studyMustNotBeLocked) {
				ControllerUtils.checkStudyLocked(study);
			}
		}
	}
}
