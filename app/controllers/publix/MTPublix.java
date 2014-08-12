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

import com.google.common.net.MediaType;

import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;

/**
 * Implementation of MechArg's public API for studies that are started via
 * MTurk.
 * 
 * @author madsen
 */
public class MTPublix extends Publix {

	public static final String ASSIGNMENT_ID_NOT_AVAILABLE = "ASSIGNMENT_ID_NOT_AVAILABLE";
	private static final String CLASS_NAME = MTPublix.class.getSimpleName();

	private MTErrorMessages errorMessages = new MTErrorMessages();
	private MTRetriever retriever = new MTRetriever(errorMessages);
	private Persistance persistance = new Persistance();
	private PublixUtils utils = new PublixUtils(errorMessages, retriever,
			persistance);

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

		StudyModel study = retriever.retrieveStudy(studyId);

		checkForMTurkPreview(studyId, mtAssignmentId);

		// Check worker
		if (mtWorkerId == null) {
			throw new BadRequestPublixException(
					errorMessages.workerNotInQueryParameter(mtWorkerId));
		}
		MTWorker worker = MTWorker.findByMTWorkerId(mtWorkerId);
		if (worker == null) {
			worker = persistance.createMTWorker(mtWorkerId,
					isRequestFromMTurkSandbox());
		}
		checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, String.valueOf(worker.getId()));

		persistance.createStudyResult(study, worker);

		ComponentModel firstComponent = retriever.retrieveFirstComponent(study);
		return startComponent(studyId, firstComponent.getId());
	}

	@Override
	@Transactional
	public Result startComponent(Long studyId, Long componentId)
			throws Exception {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		MTWorker worker = retriever.retrieveWorker();
		StudyModel study = retriever.retrieveStudy(studyId);
		ComponentModel component = retriever.retrieveComponent(study,
				componentId);
		StudyResult studyResult = retriever.retrieveWorkersStartedStudyResult(
				worker, study);

		utils.startComponent(component, studyResult);

		return redirect(component.getViewUrl());
	}

	@Override
	@Transactional
	public Result startNextComponent(Long studyId) throws Exception {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "workerId " + session(WORKER_ID));
		MTWorker worker = retriever.retrieveWorker();
		StudyModel study = retriever.retrieveStudy(studyId);
		StudyResult studyResult = retriever.retrieveWorkersStartedStudyResult(
				worker, study);
		ComponentModel nextComponent = retriever
				.retrieveNextComponent(studyResult);
		return startComponent(studyId, nextComponent.getId());
	}

	@Override
	@Transactional
	public Result getComponentData(Long studyId, Long componentId)
			throws Exception {
		Logger.info(CLASS_NAME + ".getComponentData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		MTWorker worker = retriever
				.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = retriever.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = retriever.retrieveComponent(study,
				componentId, MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyResult studyResult = retriever.retrieveWorkersStartedStudyResult(
				worker, study, MediaType.TEXT_JAVASCRIPT_UTF_8);

		// Check component result
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
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		MTWorker worker = retriever
				.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = retriever.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = retriever.retrieveComponent(study,
				componentId, MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyResult studyResult = retriever.retrieveWorkersStartedStudyResult(
				worker, study, MediaType.TEXT_JAVASCRIPT_UTF_8);

		// Check component result
		ComponentResult componentResult = retriever.retrieveComponentResult(
				component, studyResult);
		if (componentResult == null
				|| componentResult.getComponentState() == ComponentState.FINISHED
				|| componentResult.getComponentState() == ComponentState.FAIL) {
			throw new ForbiddenPublixException(
					errorMessages.componentAlreadyFinishedOrFailed(
							study.getId(), component.getId()),
					MediaType.TEXT_JAVASCRIPT_UTF_8);
		}

		// Get data in format JSON, text or XML and convert to String
		String data = PublixUtils.getRequestBodyAsString(request().body());
		if (data == null) {
			componentResult.setComponentState(ComponentState.FAIL);
			componentResult.merge();
			return badRequest(errorMessages.submittedDataUnknownFormat(
					study.getId(), component.getId()));
		}

		componentResult.setData(data);
		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		componentResult.merge();

		return ok();
	}
	
	@Override
	@Transactional
	public Result finishStudy(Long studyId, Boolean successful,
			String errorMsg) throws Exception {
		Logger.info(CLASS_NAME + ".finishStudy: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID) + ", "
				+ "successful " + successful + ", " + "errorMsg \""
				+ errorMsg + "\"");

		MTWorker worker = retriever.retrieveWorker();
		StudyModel study = retriever.retrieveStudy(studyId);

		StudyResult studyResult = retriever.retrieveWorkersLastStudyResult(
				worker, study);
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
					errorMessages.assignmentIdNotSpecified());
		}
		if (mtAssignmentId.equals(ASSIGNMENT_ID_NOT_AVAILABLE)) {
			// It's a preview coming from Mechanical Turk -> no previews
			throw new BadRequestPublixException(
					errorMessages.noPreviewAvailable(studyId));
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
