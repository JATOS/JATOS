package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.results.ComponentResult;
import models.results.ComponentResult.ComponentState;
import models.results.StudyResult;
import models.results.StudyResult.StudyState;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Result;
import services.ErrorMessages;
import services.MTErrorMessages;
import services.Persistance;

import com.google.common.net.MediaType;

import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;

/**
 * Implementation of MechArg's public API for studies that are started via
 * MTurk.
 * 
 * @author madsen
 */
public class MTPublix extends Publix implements IPublix {

	public static final String ASSIGNMENT_ID_NOT_AVAILABLE = "ASSIGNMENT_ID_NOT_AVAILABLE";
	private static final String CLASS_NAME = MTPublix.class.getSimpleName();

	private MTErrorMessages errorMessages = new MTErrorMessages();
	private MTPublixUtils utils = new MTPublixUtils(errorMessages);

	@Override
	@Transactional
	public Result startStudy(Long studyId) throws Exception {
		// Get MTurk query parameters
		// Hint: Don't confuse MTurk's workerId with MechArg's workerId. They
		// aren't the same. MechArg's workerId is automatically generated
		// and MTurk's workerId is stored within the MTWorker.
		String mtWorkerId = request().getQueryString("workerId");
		String mtAssignmentId = request().getQueryString("assignmentId");
		String mtHitId = request().getQueryString("hitId");
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "Parameters from MTurk: workerId " + mtWorkerId + ", "
				+ "assignmentId " + mtAssignmentId + ", " + "hitId " + mtHitId);

		StudyModel study = utils.retrieveStudy(studyId);

		checkForMTurkPreview(studyId, mtAssignmentId);

		// Check worker
		if (mtWorkerId == null) {
			throw new BadRequestPublixException(
					ErrorMessages.workerNotInQueryParameter(mtWorkerId));
		}
		MTWorker worker = MTWorker.findByMTWorkerId(mtWorkerId);
		if (worker == null) {
			worker = Persistance.createMTWorker(mtWorkerId,
					isRequestFromMTurkSandbox());
		}
		checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, String.valueOf(worker.getId()));

		Persistance.createStudyResult(study, worker);

		ComponentModel firstComponent = utils.retrieveFirstComponent(study);
		return startComponent(studyId, firstComponent.getId());
	}

	@Override
	@Transactional
	public Result startComponent(Long studyId, Long componentId)
			throws Exception {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		MTWorker worker = utils.retrieveWorker();
		StudyModel study = utils.retrieveStudy(studyId);
		ComponentModel component = utils.retrieveComponent(study, componentId);
		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study);

		utils.startComponent(component, studyResult);
		return redirect(component.getViewUrl());
	}

	@Override
	@Transactional
	public Result startNextComponent(Long studyId) throws Exception {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "workerId " + session(WORKER_ID));
		MTWorker worker = utils.retrieveWorker();
		StudyModel study = utils.retrieveStudy(studyId);
		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study);
		ComponentModel nextComponent = utils.retrieveNextComponent(studyResult);
		return startComponent(studyId, nextComponent.getId());
	}

	@Override
	@Transactional
	public Result getComponentData(Long studyId, Long componentId)
			throws Exception {
		Logger.info(CLASS_NAME + ".getComponentData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		MTWorker worker = utils.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = utils.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = utils.retrieveComponent(study, componentId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study, MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentResult componentResult = utils.retrieveStartedComponentResult(
				component, studyResult);

		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		componentResult.merge();

		return ok(component.asJsonForPublic());
	}

	@Override
	@Transactional
	public Result submitResultData(Long studyId, Long componentId)
			throws Exception {
		Logger.info(CLASS_NAME + ".submitResultData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		MTWorker worker = utils.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = utils.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = utils.retrieveComponent(study, componentId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study, MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentResult componentResult = utils.retrieveStartedComponentResult(
				component, studyResult);

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
				+ "workerId " + session(WORKER_ID) + ", " + "successful "
				+ successful + ", " + "errorMsg \"" + errorMsg + "\"");
		MTWorker worker = utils.retrieveWorker();
		StudyModel study = utils.retrieveStudy(studyId);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		String confirmationCode;
		if (studyResult.getStudyState() == StudyState.STARTED) {
			confirmationCode = utils.finishStudy(successful, studyResult);
		} else {
			confirmationCode = studyResult.getConfirmationCode();
		}

		if (!successful) {
			return ok(views.html.publix.error.render(errorMsg));
		}
		return ok(views.html.publix.confirmationCode.render(confirmationCode));
	}

	private void checkForMTurkPreview(Long studyId, String mtAssignmentId)
			throws BadRequestPublixException {
		if (mtAssignmentId == null) {
			throw new BadRequestPublixException(
					ErrorMessages.assignmentIdNotSpecified());
		}
		if (mtAssignmentId.equals(ASSIGNMENT_ID_NOT_AVAILABLE)) {
			// It's a preview coming from Mechanical Turk -> no previews
			throw new BadRequestPublixException(
					ErrorMessages.noPreviewAvailable(studyId));
		}
	}

	/**
	 * Returns true if the request comes from the Mechanical Turk Sandbox and
	 * false otherwise.
	 */
	private boolean isRequestFromMTurkSandbox() {
		String turkSubmitTo = request().getQueryString("turkSubmitTo");
		if (turkSubmitTo != null && turkSubmitTo.contains("sandbox")) {
			return true;
		}
		return false;
	}

	private void checkWorkerAllowedToStartStudy(MTWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		if (worker instanceof MTSandboxWorker) {
			return;
		}
		if (worker.didStudy(study)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
		;
	}

}
