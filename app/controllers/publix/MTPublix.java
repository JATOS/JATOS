package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.results.StudyResult;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import play.Logger;
import play.mvc.Result;
import services.ErrorMessages;
import services.MTErrorMessages;
import services.PersistanceUtils;
import controllers.ControllerUtils;
import exceptions.BadRequestPublixException;
import exceptions.PublixException;

/**
 * Implementation of JATOS' public API for studies that are started via MTurk.
 * 
 * @author Kristian Lange
 */
public class MTPublix extends Publix<MTWorker> implements IPublix {

	public static final String HIT_ID = "hitId";
	public static final String ASSIGNMENT_ID = "assignmentId";

	/**
	 * Hint: Don't confuse MTurk's workerId with JATOS' workerId. They aren't
	 * the same. JATOS' workerId is automatically generated and MTurk's workerId
	 * is stored within the MTWorker.
	 */
	public static final String MT_WORKER_ID = "workerId";
	public static final String SANDBOX = "sandbox";
	public static final String TURK_SUBMIT_TO = "turkSubmitTo";
	public static final String ASSIGNMENT_ID_NOT_AVAILABLE = "ASSIGNMENT_ID_NOT_AVAILABLE";

	private static final String CLASS_NAME = MTPublix.class.getSimpleName();

	protected static final MTErrorMessages errorMessages = new MTErrorMessages();
	protected static final MTPublixUtils utils = new MTPublixUtils(
			errorMessages);

	public MTPublix() {
		super(utils);
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		// Get MTurk query parameters
		String mtWorkerId = getQueryString(MT_WORKER_ID);
		String mtAssignmentId = getQueryString(ASSIGNMENT_ID);
		String mtHitId = getQueryString(HIT_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "Parameters from MTurk: workerId " + mtWorkerId + ", "
				+ "assignmentId " + mtAssignmentId + ", " + "hitId " + mtHitId);

		StudyModel study = utils.retrieveStudy(studyId);

		checkForMTurkPreview(studyId, mtAssignmentId);

		// Check worker
		if (mtWorkerId == null) {
			throw new BadRequestPublixException(ErrorMessages.NO_MTURK_WORKERID);
		}
		MTWorker worker = MTWorker.findByMTWorkerId(mtWorkerId);
		if (worker == null) {
			worker = PersistanceUtils.createMTWorker(mtWorkerId,
					isRequestFromMTurkSandbox());
		}
		utils.checkWorkerAllowedToDoStudy(worker, study);
		session(WORKER_ID, String.valueOf(worker.getId()));

		utils.finishAllPriorStudyResults(worker, study);
		PersistanceUtils.createStudyResult(study, worker);

		ComponentModel firstComponent = utils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

	@Override
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException {
		Logger.info(CLASS_NAME + ".finishStudy: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID) + ", " + "successful "
				+ successful + ", " + "errorMsg \"" + errorMsg + "\"");
		StudyModel study = utils.retrieveStudy(studyId);
		MTWorker worker = utils.retrieveWorker();
		utils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		String confirmationCode;
		if (!utils.studyDone(studyResult)) {
			confirmationCode = utils.finishStudy(successful, errorMsg,
					studyResult);
		} else {
			confirmationCode = studyResult.getConfirmationCode();
		}

		PublixUtils.discardIdCookie();
		if (ControllerUtils.isAjax()) {
			return ok(confirmationCode);
		} else {
			if (!successful) {
				return ok(views.html.publix.error.render(errorMsg));
			} else {
				return ok(views.html.publix.confirmationCode
						.render(confirmationCode));
			}
		}
	}

	private void checkForMTurkPreview(Long studyId, String mtAssignmentId)
			throws BadRequestPublixException {
		if (mtAssignmentId != null
				&& mtAssignmentId.equals(ASSIGNMENT_ID_NOT_AVAILABLE)) {
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
		String workerType = session(PublixInterceptor.WORKER_TYPE);
		return workerType.equals(MTSandboxWorker.WORKER_TYPE);
	}

}
