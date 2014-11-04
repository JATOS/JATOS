package controllers;

import java.io.IOException;
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
import play.mvc.Security;
import play.mvc.SimpleResult;
import services.ErrorMessages;
import services.JsonUtils;
import services.PersistanceUtils;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class ComponentResults extends Controller {

	private static final String CLASS_NAME = ComponentResults.class
			.getSimpleName();

	@Transactional
	public static Result index(Long studyId, Long componentId, String errorMsg,
			int httpStatus) throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		String breadcrumbs = Breadcrumbs
				.generateBreadcrumbs(Breadcrumbs.getHomeBreadcrumb(),
						Breadcrumbs.getStudyBreadcrumb(study),
						Breadcrumbs.getComponentBreadcrumb(study, component),
						"Results");
		return status(httpStatus,
				views.html.mecharg.result.componentResults.render(studyList,
						loggedInUser, breadcrumbs, study, component, errorMsg));
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
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result remove(String componentResultIds)
			throws ResultException {
		Logger.info(CLASS_NAME + ".remove: componentResultIds "
				+ componentResultIds + ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		List<Long> componentResultIdList = ControllerUtils
				.extractResultIds(componentResultIds);
		for (Long componentResultId : componentResultIdList) {
			ComponentResult componentResult = ComponentResult
					.findById(componentResultId);
			if (componentResult == null) {
				String errorMsg = ErrorMessages
						.componentResultNotExist(componentResultId);
				SimpleResult result = notFound(errorMsg);
				throw new ResultException(result, errorMsg);
			}
			ComponentModel resultsComponent = componentResult.getComponent();
			StudyModel resultsStudy = resultsComponent.getStudy();
			ControllerUtils.checkStandardForComponents(resultsStudy.getId(),
					resultsComponent.getId(), resultsStudy, loggedInUser,
					resultsComponent);
			ControllerUtils.checkStudyLocked(resultsStudy);
			PersistanceUtils.removeComponentResult(componentResult);
		}

		return ok();
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result tableDataByComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".tableDataByComponent: studyId " + studyId
				+ ", " + "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
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

	@Transactional
	public static Result exportData(String componentResultIds)
			throws ResultException {
		Logger.info(CLASS_NAME + ".exportResultData: componentResultIds "
				+ componentResultIds + ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		String componentResultDataAsStr = getComponentResultData(
				componentResultIds, loggedInUser);
		return ok(componentResultDataAsStr);
	}

	private static String getComponentResultData(String componentResultIds,
			UserModel loggedInUser) throws ResultException {
		List<Long> componentResultIdList = ControllerUtils
				.extractResultIds(componentResultIds);

		// Put all ComponentResult's data into a String each in a separate line
		Iterator<Long> iterator = componentResultIdList.iterator();
		StringBuilder sb = new StringBuilder();
		while (iterator.hasNext()) {
			Long componentResultId = iterator.next();
			ComponentResult componentResult = ComponentResult
					.findById(componentResultId);
			if (componentResult == null) {
				String errorMsg = ErrorMessages
						.componentResultNotExist(componentResultId);
				SimpleResult result = badRequest(errorMsg);
				throw new ResultException(result, errorMsg);
			}
			ComponentModel resultsComponent = componentResult.getComponent();
			StudyModel resultsStudy = resultsComponent.getStudy();
			ControllerUtils.checkStandardForComponents(resultsStudy.getId(),
					resultsComponent.getId(), resultsStudy, loggedInUser,
					resultsComponent);
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

}
