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
import play.db.jpa.Transactional;
import play.mvc.Result;
import services.ErrorMessages;
import services.MAErrorMessages;
import services.Persistance;

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
	@Transactional
	public Result startStudy(Long studyId) throws Exception {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		ComponentModel firstComponent = utils.retrieveFirstComponent(study);
		checkStandard(study, worker.getUser(), firstComponent);

		String mechArgTry = utils.retrieveMechArgTry();
		if (!mechArgTry.equals(Studies.STUDY)) {
			throw new ForbiddenPublixException(
					ErrorMessages.noMechArgStudyTry());
		}

		Persistance.createStudyResult(study, worker);
		return startComponent(studyId, firstComponent.getId());
	}

	@Override
	@Transactional
	public Result startComponent(Long studyId, Long componentId)
			throws Exception {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		ComponentModel component = utils.retrieveComponent(study, componentId);
		checkStandard(study, worker.getUser(), component);

		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study);

		String mechArgTry = utils.retrieveMechArgTry();
		if (mechArgTry.equals(Components.COMPONENT)) {
			// Single component try: stop study after first component.
			utils.finishStudy(true, studyResult);
			return redirect(routes.Studies.index(study.getId()));
		}

		utils.startComponent(component, studyResult);

		return redirect(component.getViewUrl());
	}

	@Override
	@Transactional
	public Result startNextComponent(Long studyId) throws Exception {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		utils.checkMembership(study, worker.getUser());

		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study);

		String mechArgTry = utils.retrieveMechArgTry();
		if (mechArgTry.equals(Components.COMPONENT)) {
			// Single component try: stop study after first component.
			utils.finishStudy(true, studyResult);
			return redirect(routes.Studies.index(study.getId()));
		}

		ComponentModel nextComponent = utils.retrieveNextComponent(studyResult);
		return startComponent(studyId, nextComponent.getId());
	}

	@Override
	@Transactional
	public Result getComponentData(Long studyId, Long componentId)
			throws Exception {
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

		return ok(component.asJsonForPublic());
	}

	@Override
	@Transactional
	public Result submitResultData(Long studyId, Long componentId)
			throws Exception {
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
	@Transactional
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws Exception {
		Logger.info(CLASS_NAME + ".finishStudy: studyId " + studyId + ", "
				+ "logged-in user email " + session(Users.COOKIE_EMAIL) + ", "
				+ "successful " + successful + ", " + "errorMsg \"" + errorMsg
				+ "\"");
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		utils.checkMembership(study, worker.getUser());

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		if (studyResult.getStudyState() == StudyState.STARTED) {
			utils.finishStudy(successful, studyResult);
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
