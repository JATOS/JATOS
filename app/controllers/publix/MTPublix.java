package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.results.ComponentResult;
import models.results.ComponentResult.ComponentState;
import models.results.StudyResult;
import models.results.StudyResult.StudyState;
import models.workers.MTSandboxWorker;
import models.workers.MTTesterWorker;
import models.workers.MTWorker;
import play.Logger;
import play.libs.F.Promise;
import play.mvc.Result;
import services.ErrorMessages;
import services.JsonUtils;
import services.MTErrorMessages;
import services.PersistanceUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.MediaType;

import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.PublixException;

/**
 * Implementation of MechArg's public API for studies that are started via
 * MTurk.
 * 
 * @author Kristian Lange
 */
public class MTPublix extends Publix implements IPublix {

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
			throw new BadRequestPublixException(ErrorMessages.NO_MTURK_WORKERID);
		}
		MTWorker worker = MTWorker.findByMTWorkerId(mtWorkerId);
		if (worker == null) {
			worker = PersistanceUtils.createMTWorker(mtWorkerId,
					isRequestFromMTurkSandbox());
		}
		checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, String.valueOf(worker.getId()));

		PersistanceUtils.createStudyResult(study, worker);

		ComponentModel firstComponent = utils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

	@Override
	public Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		MTWorker worker = utils.retrieveWorker();
		StudyModel study = utils.retrieveStudy(studyId);
		ComponentModel component = utils.retrieveComponent(study, componentId);
		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study);

		utils.startComponent(component, studyResult);

		String urlPath = ExternalAssets.getComponentUrlPath(study, component);
		String urlWithQueryStr = PublixUtils.getUrlWithRequestQueryString(
				request(), urlPath);
		return forwardTo(urlWithQueryStr);
	}

	@Override
	public Result startNextComponent(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "workerId " + session(WORKER_ID));
		MTWorker worker = utils.retrieveWorker();
		StudyModel study = utils.retrieveStudy(studyId);
		StudyResult studyResult = utils.retrieveWorkersStartedStudyResult(
				worker, study);

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
		Logger.info(CLASS_NAME + ".getStudyData: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID));
		MTWorker worker = utils.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
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
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		MTWorker worker = utils.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = utils.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = utils.retrieveComponent(study, componentId,
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
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		MTWorker worker = utils.retrieveWorker(MediaType.TEXT_JAVASCRIPT_UTF_8);
		StudyModel study = utils.retrieveStudy(studyId,
				MediaType.TEXT_JAVASCRIPT_UTF_8);
		ComponentModel component = utils.retrieveComponent(study, componentId,
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
				+ "workerId " + session(WORKER_ID) + ", " + "successful "
				+ successful + ", " + "errorMsg \"" + errorMsg + "\"");
		MTWorker worker = utils.retrieveWorker();
		StudyModel study = utils.retrieveStudy(studyId);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		String confirmationCode;
		StudyState state = studyResult.getStudyState();
		if (!(state == StudyState.FINISHED || state == StudyState.FAIL)) {
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
		String turkSubmitTo = request().getQueryString("turkSubmitTo");
		if (turkSubmitTo != null && turkSubmitTo.contains("sandbox")) {
			return true;
		}
		return false;
	}

	private void checkWorkerAllowedToStartStudy(MTWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		if (worker instanceof MTSandboxWorker
				|| worker instanceof MTTesterWorker) {
			return;
		}
		if (worker.didStudy(study)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
		;
	}

}
