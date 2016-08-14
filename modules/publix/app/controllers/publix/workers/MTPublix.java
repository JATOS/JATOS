package controllers.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.MTWorkerDao;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.PublixException;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixHelpers;
import services.publix.ResultCreator;
import services.publix.WorkerCreator;
import services.publix.group.ChannelService;
import services.publix.group.GroupService;
import services.publix.idcookie.IdCookie;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.MTErrorMessages;
import services.publix.workers.MTPublixUtils;
import services.publix.workers.MTStudyAuthorisation;
import utils.common.ControllerUtils;
import utils.common.JsonUtils;

/**
 * Implementation of JATOS' public API for studies that are started via MTurk. A
 * MTurk run is done by a MTWorker or a MTSandboxWorker.
 * 
 * @author Kristian Lange
 */
@Singleton
public class MTPublix extends Publix<MTWorker> implements IPublix {

	private static final ALogger LOGGER = Logger.of(MTPublix.class);

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

	private final MTPublixUtils publixUtils;
	private final MTStudyAuthorisation studyAuthorisation;
	private final ResultCreator resultCreator;
	private final WorkerCreator workerCreator;
	private final MTErrorMessages errorMessages;
	private final MTWorkerDao mtWorkerDao;

	@Inject
	MTPublix(JPAApi jpa, MTPublixUtils publixUtils,
			MTStudyAuthorisation studyAuthorisation,
			ResultCreator resultCreator, WorkerCreator workerCreator,
			GroupService groupService, ChannelService channelService,
			IdCookieService idCookieService, MTErrorMessages errorMessages,
			StudyAssets studyAssets, JsonUtils jsonUtils,
			ComponentResultDao componentResultDao,
			StudyResultDao studyResultDao, MTWorkerDao mtWorkerDao,
			GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, groupService,
				channelService, idCookieService, errorMessages, studyAssets,
				jsonUtils, componentResultDao, studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.resultCreator = resultCreator;
		this.workerCreator = workerCreator;
		this.errorMessages = errorMessages;
		this.mtWorkerDao = mtWorkerDao;
	}

	@Override
	public Result startStudy(Long studyId, Long batchId)
			throws PublixException {
		// Get MTurk query parameters
		String mtWorkerId = getQueryString(MT_WORKER_ID);
		String mtAssignmentId = getQueryString(ASSIGNMENT_ID);
		// String mtHitId = getQueryString(HIT_ID);
		LOGGER.info(".startStudy: studyId " + studyId + ", " + "batchId "
				+ batchId);

		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
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
					MTErrorMessages.NO_MTURK_WORKERID);
		}
		MTWorker worker = mtWorkerDao.findByMTWorkerId(mtWorkerId);
		if (worker == null) {
			String workerType = retrieveWorkerTypeFromQueryString();
			boolean isRequestFromMTurkSandbox = workerType
					.equals(MTSandboxWorker.WORKER_TYPE);
			worker = workerCreator.createAndPersistMTWorker(mtWorkerId,
					isRequestFromMTurkSandbox, batch);
		}
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);
		LOGGER.info(".startStudy: study (study ID " + studyId + ", batch ID "
				+ batchId + ") " + "assigned to worker with ID "
				+ worker.getId());

		publixUtils.finishAbandonedStudyResults();
		StudyResult studyResult = resultCreator.createStudyResult(study, batch,
				worker);
		idCookieService.writeIdCookie(worker, batch, studyResult);

		Component firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(
				controllers.publix.routes.PublixInterceptor.startComponent(
						studyId, firstComponent.getId(), studyResult.getId()));
	}

	@Override
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg,
			Long studyResultId) throws PublixException {
		LOGGER.info(".finishStudy: studyId " + studyId + ", " + "studyResultId "
				+ studyResultId + ", " + "successful " + successful + ", "
				+ "errorMsg \"" + errorMsg + "\"");
		IdCookie idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		MTWorker worker = publixUtils
				.retrieveTypedWorker(idCookie.getWorkerId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		StudyResult studyResult = publixUtils.retrieveWorkersStudyResult(worker,
				study, studyResultId);
		String confirmationCode;
		if (!PublixHelpers.studyDone(studyResult)) {
			confirmationCode = publixUtils.finishStudyResult(successful,
					errorMsg, studyResult);
			groupService.finishStudyResultInGroup(studyResult);
		} else {
			confirmationCode = studyResult.getConfirmationCode();
		}
		idCookieService.discardIdCookie(studyResult.getId());
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

	private String retrieveWorkerTypeFromQueryString()
			throws BadRequestPublixException {
		String mtWorkerId = Publix.getQueryString(MTPublix.MT_WORKER_ID);
		if (mtWorkerId != null) {
			return retrieveWorkerType(mtWorkerId);
		}
		throw new BadRequestPublixException(
				PublixErrorMessages.UNKNOWN_WORKER_TYPE);
	}

	public String retrieveWorkerType(String mtWorkerId) {
		String turkSubmitTo = request().getQueryString(MTPublix.TURK_SUBMIT_TO);
		if (turkSubmitTo != null
				&& turkSubmitTo.toLowerCase().contains(MTPublix.SANDBOX)) {
			return MTSandboxWorker.WORKER_TYPE;
		} else {
			return MTWorker.WORKER_TYPE;
		}
	}

}
