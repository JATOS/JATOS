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

		String mechArgTry = retrieveMechArgTry();
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

		StudyResult studyResult = retrieveWorkersStartedStudyResult(worker,
				study);

		String mechArgTry = retrieveMechArgTry();
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
		checkMembership(study, worker.getUser());

		StudyResult studyResult = retrieveWorkersStartedStudyResult(worker,
				study);

		String mechArgTry = retrieveMechArgTry();
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

		StudyResult studyResult = retrieveWorkersStartedStudyResult(worker,
				study, MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentResult componentResult = utils.retrieveStartedComponentResult(
				component, studyResult, MediaType.TEXT_JAVASCRIPT_UTF_8);

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

		StudyResult studyResult = retrieveWorkersStartedStudyResult(worker,
				study, MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentResult componentResult = utils.retrieveStartedComponentResult(
				component, studyResult, MediaType.TEXT_JAVASCRIPT_UTF_8);

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
		checkMembership(study, worker.getUser());

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
		checkMembership(study, loggedInUser, errorMediaType);
		if (!component.getStudy().equals(study)) {
			throw new BadRequestPublixException(
					ErrorMessages.componentNotBelongToStudy(study.getId(),
							component.getId()), errorMediaType);
		}
	}

	private void checkMembership(StudyModel study, UserModel loggedInUser)
			throws BadRequestPublixException {
		checkMembership(study, loggedInUser, MediaType.HTML_UTF_8);
	}

	private void checkMembership(StudyModel study, UserModel loggedInUser,
			MediaType errorMediaType) throws BadRequestPublixException {
		if (!study.hasMember(loggedInUser)) {
			throw new BadRequestPublixException(ErrorMessages.notMember(
					loggedInUser.getName(), loggedInUser.getEmail(),
					study.getId(), study.getTitle()), errorMediaType);
		}
	}

	private String retrieveMechArgTry() throws ForbiddenPublixException {
		return retrieveMechArgTry(MediaType.HTML_UTF_8);
	}

	private String retrieveMechArgTry(MediaType mediaType)
			throws ForbiddenPublixException {
		String mechArgTry = session(MECHARG_TRY);
		if (mechArgTry == null) {
			throw new ForbiddenPublixException(ErrorMessages.noMechArgTry(),
					mediaType);
		}
		return mechArgTry;
	}

	private StudyResult retrieveWorkersStartedStudyResult(MAWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		return retrieveWorkersStartedStudyResult(worker, study,
				MediaType.HTML_UTF_8);
	}

	private StudyResult retrieveWorkersStartedStudyResult(MAWorker worker,
			StudyModel study, MediaType mediaType)
			throws ForbiddenPublixException {
		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study, mediaType);
		String mechArgTry = retrieveMechArgTry(mediaType);
		if (studyResult == null) {
			if (mechArgTry.equals(Studies.STUDY)) {
				throw new ForbiddenPublixException(
						errorMessages.workerNeverStartedStudy(worker,
								study.getId()), mediaType);
			}
			if (mechArgTry.equals(Components.COMPONENT)) {
				// Try-out of a single component: Just create a StudyResult for
				// this. The StudyResult will have only one ComponentResult.
				studyResult = Persistance.createStudyResult(study, worker);
			}
		}
		return studyResult;
	}

}
