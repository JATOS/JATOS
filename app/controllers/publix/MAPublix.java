package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import models.results.ComponentResult.ComponentState;
import models.results.StudyResult;
import models.workers.MAWorker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Result;

import com.google.common.net.MediaType;

import controllers.BadRequests;
import controllers.Components;
import controllers.Studies;
import controllers.Users;
import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.PublixException;

/**
 * Implementation of MechArg's public API for studies and components that are
 * started via MechArg's UI.
 * 
 * @author madsen
 */
public class MAPublix extends Publix {

	public static final String MECHARG_TRY = "mecharg_try";
	private static final String CLASS_NAME = MAPublix.class.getSimpleName();

	private MAErrorMessages errorMessages = new MAErrorMessages();
	private MARetriever retriever = new MARetriever(errorMessages);
	private Persistance persistance = new Persistance();
	private PublixUtils utils = new PublixUtils(errorMessages, retriever,
			persistance);

	@Override
	@Transactional
	public Result startStudy(Long studyId) throws Exception {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = retriever.retrieveStudy(studyId);
		MAWorker worker = retriever.retrieveWorker();
		ComponentModel firstComponent = retriever.retrieveFirstComponent(study);
		checkStandard(study, worker.getUser(), firstComponent);

		persistance.createStudyResult(study, worker);

		return startComponent(studyId, firstComponent.getId());
	}

	@Override
	@Transactional
	public Result startComponent(Long studyId, Long componentId)
			throws Exception {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = retriever.retrieveStudy(studyId);
		MAWorker worker = retriever.retrieveWorker();
		ComponentModel component = retriever.retrieveComponent(study,
				componentId);
		checkStandard(study, worker.getUser(), component);

		StudyResult studyResult = retrieveStudyResult(worker, study);

		utils.startComponent(component, studyResult);

		return redirect(component.getViewUrl());
	}

	@Override
	@Transactional
	public Result startNextComponent(Long studyId) throws Exception {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = retriever.retrieveStudy(studyId);
		MAWorker worker = retriever.retrieveWorker();
		StudyResult studyResult = retrieveStudyResult(worker, study);
		ComponentModel nextComponent = retriever
				.retrieveNextComponent(studyResult);
		return startComponent(studyId, nextComponent.getId());
	}

	@Override
	@Transactional
	public Result getComponentData(Long studyId, Long componentId)
			throws Exception {
		Logger.info(CLASS_NAME + ".getComponentData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "logged-in user email "
				+ session(Users.COOKIE_EMAIL));
		MAWorker worker = retriever
				.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = retriever.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = retriever.retrieveComponent(study,
				componentId, MediaType.TEXT_JAVASCRIPT_UTF_8);
		checkStandard(study, worker.getUser(), component,
				MediaType.TEXT_JAVASCRIPT_UTF_8);

		StudyResult studyResult = retrieveStudyResult(worker, study,
				MediaType.TEXT_JAVASCRIPT_UTF_8);

		ComponentResult componentResult = retriever.retrieveComponentResult(
				component, studyResult);
		if (componentResult == null) {
			// If component was never started, conveniently start it
			componentResult = utils.startComponent(component, studyResult,
					MediaType.TEXT_JAVASCRIPT_UTF_8);
		} else if (componentResult.getComponentState() != ComponentState.STARTED) {
			throw new ForbiddenPublixException(
					errorMessages.componentAlreadyStarted(study.getId(),
							component.getId()), MediaType.TEXT_JAVASCRIPT_UTF_8);
		}

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
		StudyModel study = retriever.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		MAWorker worker = retriever
				.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = retriever.retrieveComponent(study,
				componentId, MediaType.TEXT_JAVASCRIPT_UTF_8);
		checkStandard(study, worker.getUser(), component,
				MediaType.TEXT_JAVASCRIPT_UTF_8);

		StudyResult studyResult = retrieveStudyResult(worker, study,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		// TODO stop study in case of component try (same for other methods)
		return null;
	}

	@Override
	@Transactional
	public Result finishStudy(Long studyId, Boolean successful,
			String errorMsg) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	private void checkStandard(StudyModel study, UserModel loggedInUser,
			ComponentModel component) throws PublixException {
		checkStandard(study, loggedInUser, component, MediaType.HTML_UTF_8);
	}

	private void checkStandard(StudyModel study, UserModel loggedInUser,
			ComponentModel component, MediaType errorMediaType)
			throws PublixException {
		if (!study.hasMember(loggedInUser)) {
			throw new BadRequestPublixException(BadRequests.notMember(
					loggedInUser.getName(), loggedInUser.getEmail(),
					study.getId(), study.getTitle()), errorMediaType);
		}
		if (!component.getStudy().equals(study)) {
			throw new BadRequestPublixException(
					BadRequests.componentNotBelongToStudy(study.getId(),
							component.getId()), errorMediaType);
		}
	}

	private StudyResult retrieveStudyResult(MAWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		return retrieveStudyResult(worker, study, MediaType.HTML_UTF_8);
	}

	private StudyResult retrieveStudyResult(MAWorker worker, StudyModel study,
			MediaType mediaType) throws ForbiddenPublixException {
		String mechArgTry = session(MECHARG_TRY);
		if (mechArgTry == null) {
			throw new ForbiddenPublixException(errorMessages.noMechArgTry(),
					mediaType);
		}

		StudyResult studyResult = null;
		try {
			studyResult = retriever.retrieveWorkersStartedStudyResult(worker,
					study, mediaType);
		} catch (ForbiddenPublixException e) {
			if (mechArgTry.equals(Studies.STUDY)) {
				throw e;
			}
			if (mechArgTry.equals(Components.COMPONENT)) {
				// Try-out of a single component: Just create a StudyResult for
				// this. The StudyResult will have only one ComponentResult.
				studyResult = persistance.createStudyResult(study, worker);
			}
		}
		return studyResult;
	}

}
