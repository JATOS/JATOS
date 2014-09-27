package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import models.results.ComponentResult.ComponentState;
import models.results.StudyResult;
import models.results.StudyResult.StudyState;
import models.workers.MAWorker;
import play.Logger;
import play.mvc.Result;
import services.ErrorMessages;
import services.JsonUtils;
import services.MAErrorMessages;
import services.Persistance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.MediaType;

import controllers.Components;
import controllers.Studies;
import controllers.Users;
import controllers.routes;
import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.PublixException;

/**
 * Implementation of MechArg's public API for studies and components that are
 * started via MechArg's UI.
 * 
 * @author madsen
 */
public class MAPublix extends Publix implements IPublix {

	public static final String MECHARG_TRY = "mecharg_try";
	private static final String CLASS_NAME = MAPublix.class.getSimpleName();

	private MAErrorMessages errorMessages = new MAErrorMessages();
	private MAPublixUtils utils = new MAPublixUtils(errorMessages);

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		ComponentModel firstComponent = utils
				.retrieveFirstActiveComponent(study);
		checkStandard(study, worker.getUser(), firstComponent);

		String mechArgTry = utils.retrieveMechArgTry();
		if (!mechArgTry.equals(Studies.STUDY)) {
			throw new ForbiddenPublixException(
					ErrorMessages.noMechArgStudyTry());
		}

		Persistance.createStudyResult(study, worker);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

	@Override
	public Result startComponent(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();

		// Check if it's a single component try.
		String mechArgTry = utils.retrieveMechArgTry();
		if (mechArgTry.equals(Components.COMPONENT)) {
			// Finish study after first component
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, true, null));
		}

		ComponentModel component = utils.retrieveComponent(study, componentId);
		checkStandard(study, worker.getUser(), component);

		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study);
		utils.startComponent(component, studyResult);

		String redirectUrl = PublixUtils.getUrlWithRequestQueryString(request()
				.uri(), component.getViewUrl());
		return redirect(redirectUrl);
	}

	@Override
	public Result startNextComponent(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		utils.checkMembership(study, worker.getUser());

		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study);

		// Check if it's a single component try.
		String mechArgTry = utils.retrieveMechArgTry();
		if (mechArgTry.equals(Components.COMPONENT)) {
			// Finish study after first component
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, true, null));
		}

		ComponentModel nextComponent = utils
				.retrieveNextActiveComponent(studyResult);
		if (nextComponent == null) {
			// Study has no more components -> finish it
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, true, null));
		}
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, nextComponent.getId()));
	}

	@Override
	public Result getStudyData(Long studyId) throws PublixException,
			JsonProcessingException {
		Logger.info(CLASS_NAME + ".getStudyData: studyId " + studyId);
		MAWorker worker = utils.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = utils.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study, MediaType.TEXT_JAVASCRIPT_UTF_8);

		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		studyResult.merge();
		return ok(JsonUtils.asJsonForPublix(study));
	}

	@Override
	public Result getComponentData(Long studyId, Long componentId)
			throws PublixException, JsonProcessingException {
		Logger.info(CLASS_NAME + ".getComponentData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "logged-in user email "
				+ session(Users.COOKIE_EMAIL));
		MAWorker worker = utils.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = utils.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = utils.retrieveComponent(study, componentId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		checkStandard(study, worker.getUser(), component,
				MediaType.TEXT_JAVASCRIPT_UTF_8);

		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study, MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentState maxAllowedComponentState = ComponentState.STARTED;
		ComponentResult componentResult = utils.retrieveStartedComponentResult(
				component, studyResult, maxAllowedComponentState,
				MediaType.TEXT_JAVASCRIPT_UTF_8);

		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		componentResult.merge();

		return ok(JsonUtils.asJsonForPublix(component));
	}

	@Override
	public Result submitResultData(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".submitResultData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "logged-in user email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = utils.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		MAWorker worker = utils.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = utils.retrieveComponent(study, componentId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		checkStandard(study, worker.getUser(), component,
				MediaType.TEXT_JAVASCRIPT_UTF_8);

		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study, MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentState maxAllowedComponentState = ComponentState.DATA_RETRIEVED;
		ComponentResult componentResult = utils.retrieveStartedComponentResult(
				component, studyResult, maxAllowedComponentState,
				MediaType.TEXT_JAVASCRIPT_UTF_8);

		String data = utils.getDataFromRequestBody(request().body(), component,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		componentResult.setData(data);
		componentResult.setComponentState(ComponentState.RESULTDATA_POSTED);
		componentResult.merge();
		return ok();
	}

	@Override
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException {
		Logger.info(CLASS_NAME + ".finishStudy: studyId " + studyId + ", "
				+ "logged-in user email " + session(Users.COOKIE_EMAIL) + ", "
				+ "successful " + successful + ", " + "errorMsg \"" + errorMsg
				+ "\"");
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		utils.checkMembership(study, worker.getUser());

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		StudyState state = studyResult.getStudyState();
		if (!(state == StudyState.FINISHED || state == StudyState.FAIL)) {
			utils.finishStudy(successful, studyResult);
			Publix.session().remove(MAPublix.MECHARG_TRY);
		}
		return redirect(routes.Studies.index(study.getId()));
	}

	private void checkStandard(StudyModel study, UserModel loggedInUser,
			ComponentModel component) throws PublixException {
		checkStandard(study, loggedInUser, component, MediaType.HTML_UTF_8);
	}

	private void checkStandard(StudyModel study, UserModel loggedInUser,
			ComponentModel component, MediaType errorMediaType)
			throws PublixException {
		utils.checkMembership(study, loggedInUser, errorMediaType);
		if (!component.getStudy().equals(study)) {
			throw new BadRequestPublixException(
					ErrorMessages.componentNotBelongToStudy(study.getId(),
							component.getId()), errorMediaType);
		}
	}

}
