package controllers.publix.mt;

import models.ComponentModel;
import models.StudyModel;
import models.StudyResult;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import persistance.IComponentResultDao;
import persistance.IStudyResultDao;
import persistance.workers.IMTWorkerDao;
import play.Logger;
import play.mvc.Result;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.gui.ControllerUtils;
import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.PublixErrorMessages;
import controllers.publix.PublixInterceptor;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.PublixException;

/**
 * Implementation of JATOS' public API for studies that are started via MTurk.
 * 
 * @author Kristian Lange
 */
@Singleton
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

	private final MTPublixUtils publixUtils;
	private final MTErrorMessages errorMessages;
	private final IMTWorkerDao mtWorkerDao;

	@Inject
	MTPublix(MTPublixUtils publixUtils, MTErrorMessages errorMessages,
			IComponentResultDao componentResultDao, JsonUtils jsonUtils,
			IStudyResultDao studyResultDao, IMTWorkerDao mtWorkerDao) {
		super(publixUtils, componentResultDao, jsonUtils, studyResultDao);
		this.publixUtils = publixUtils;
		this.errorMessages = errorMessages;
		this.mtWorkerDao = mtWorkerDao;
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

		StudyModel study = publixUtils.retrieveStudy(studyId);

		// Check if it's just a preview coming from MTurk. We don't allow
		// previews.
		if (mtAssignmentId != null
				&& mtAssignmentId.equals(ASSIGNMENT_ID_NOT_AVAILABLE)) {
			// It's a preview coming from Mechanical Turk -> no previews
			throw new BadRequestPublixException(
					errorMessages.noPreviewAvailable(studyId));
		}

		// Check worker and create if doesn't exists
		if (mtWorkerId == null) {
			throw new BadRequestPublixException(
					PublixErrorMessages.NO_MTURK_WORKERID);
		}
		MTWorker worker = mtWorkerDao.findByMTWorkerId(mtWorkerId);
		if (worker == null) {
			String workerType = session(PublixInterceptor.WORKER_TYPE);
			boolean isRequestFromMTurkSandbox = workerType
					.equals(MTSandboxWorker.WORKER_TYPE);
			worker = mtWorkerDao.create(mtWorkerId,
					isRequestFromMTurkSandbox);
		}
		publixUtils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, String.valueOf(worker.getId()));

		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);

		ComponentModel firstComponent = publixUtils
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
		StudyModel study = publixUtils.retrieveStudy(studyId);
		MTWorker worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		String confirmationCode;
		if (!publixUtils.studyDone(studyResult)) {
			confirmationCode = publixUtils.finishStudy(successful, errorMsg,
					studyResult);
		} else {
			confirmationCode = studyResult.getConfirmationCode();
		}

		publixUtils.discardIdCookie();
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

}
