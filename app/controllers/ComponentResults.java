package controllers;

import java.util.List;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import services.ErrorMessages;
import services.Persistance;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class ComponentResults extends Controller {

	private static final String CLASS_NAME = ComponentResults.class
			.getSimpleName();

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result remove(Long componentResultId) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: componentResultId "
				+ componentResultId + ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			throw new ResultException(redirect(routes.Authentication.login()));
		}

		ComponentResult componentResult = ComponentResult
				.findById(componentResultId);
		if (componentResult == null) {
			return badRequest(ErrorMessages
					.componentResultNotExist(componentResultId));
		}
		// Check that logged-in user is member of the study the component
		// belongs too
		StudyModel study = componentResult.getStudyResult().getStudy();
		if (!study.hasMember(loggedInUser)) {
			return badRequest(ErrorMessages.notMember(loggedInUser.getName(),
					loggedInUser.getEmail(), study.getId(), study.getTitle()));
		}

		Persistance.removeComponentResult(componentResult);
		return ok();
	}

	@Transactional
	public static Result removeResults(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".removeResults: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		Components.checkStandardForComponents(studyId, componentId, study,
				studyList, loggedInUser, component);

		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}

		List<ComponentResult> componentResultList = ComponentResult
				.findAllByComponent(component);

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study),
				Breadcrumbs.getComponentBreadcrumb(study, component),
				"Delete Results");
		return ok(views.html.mecharg.component.removeResults.render(studyList,
				loggedInUser, breadcrumbs, component, study,
				componentResultList));
	}

	@Transactional
	public static Result submitRemovedResults(Long studyId, Long componentId)
			throws Exception {
		Logger.info(CLASS_NAME + ".submitRemovedResults: studyId " + studyId
				+ ", " + "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		ComponentModel component = ComponentModel.findById(componentId);
		Components.checkStandardForComponents(studyId, componentId, study,
				studyList, loggedInUser, component);

		if (!study.hasMember(loggedInUser)) {
			throw BadRequests
					.forbiddenNotMember(loggedInUser, study, studyList);
		}

		Map<String, String[]> formMap = request().body().asFormUrlEncoded();
		String[] checkedComponents = formMap.get(ComponentModel.RESULT);
		if (checkedComponents != null) {
			for (String resultIdStr : checkedComponents) {
				Persistance.removeComponentResult(resultIdStr);
			}
		}

		return redirect(routes.Components.index(study.getId(), componentId));
	}

}
