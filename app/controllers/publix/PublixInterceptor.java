package controllers.publix;

import java.io.IOException;

import models.workers.ClosedStandaloneWorker;
import models.workers.JatosWorker;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import models.workers.OpenStandaloneWorker;
import models.workers.PMWorker;
import play.db.jpa.Transactional;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.closed_standalone.ClosedStandalonePublix;
import controllers.publix.jatos.JatosPublix;
import controllers.publix.mt.MTPublix;
import controllers.publix.open_standalone.OpenStandalonePublix;
import controllers.publix.personal_multiple.PMPublix;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.PublixException;

/**
 * Interceptor for Publix: it intercepts requests for JATOS' public API and
 * forwards them to one of the implementations of the API (all extend Publix).
 * Each implementation deals with different workers (e.g. workers from MechTurk,
 * personal multiple workers).
 * 
 * When a study is started the implementation to use is determined by parameters
 * in the request's query string. Then the worker type is put into the session
 * and used in subsequent requests of the same study run.
 * 
 * 1. Requests coming from MechTurk or MechTurk Sandbox (MTWorker and
 * MTSandboxWorker) will be forwarded to MTPublix.<br>
 * 2. Requests coming from Jatos' UI (if clicked on show study/component) run
 * (JatosWorker) will be forwarded to JatosPublix.<br>
 * 3. Requests coming from a personal multiple run (PMWorker) will be forwarded
 * to PMPublix.<br>
 * 4. Requests coming from a closed standalone run (limited to pre-created
 * ClosedStandaloneWorker) will be forwarded to ClosedStandalonePublix.<br>
 * 5. Requests coming from an open standalone run (unlimited to everyone with
 * the link) will be forwarded to OpenStandalonePublix.<br>
 * 
 * TODO: Move @Transactional out of controller and get rid of synchronisation.
 * We need a lock for a worker to prevent the same worker doing the same study
 * in parallel.
 * 
 * @author Kristian Lange
 */
@Singleton
@With(PublixAction.class)
public class PublixInterceptor extends Controller implements IPublix {

	public static final String WORKER_TYPE = "workerType";

	private final JatosPublix jatosPublix;
	private final MTPublix mtPublix;
	private final PMPublix pmPublix;
	private final ClosedStandalonePublix closedStandalonePublix;
	private final OpenStandalonePublix openStandalonePublix;

	@Inject
	PublixInterceptor(JatosPublix jatosPublix, MTPublix mtPublix,
			PMPublix pmPublix, ClosedStandalonePublix closedStandalonePublix,
			OpenStandalonePublix openStandalonePublix) {
		this.jatosPublix = jatosPublix;
		this.mtPublix = mtPublix;
		this.pmPublix = pmPublix;
		this.closedStandalonePublix = closedStandalonePublix;
		this.openStandalonePublix = openStandalonePublix;
	}

	@Override
	@Transactional
	public Result startStudy(Long studyId) throws PublixException {
		Result result = null;
		String workerType = getWorkerTypeFromQuery();
		// Put worker type into session so later Publix calls of this study
		// run know it too
		session(WORKER_TYPE, workerType);
		switch (workerType) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.startStudy(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.startStudy(studyId);
			break;
		case PMWorker.WORKER_TYPE:
			result = pmPublix.startStudy(studyId);
			break;
		case ClosedStandaloneWorker.WORKER_TYPE:
			result = closedStandalonePublix.startStudy(studyId);
			break;
		case OpenStandaloneWorker.WORKER_TYPE:
			result = openStandalonePublix.startStudy(studyId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException {
		Promise<Result> promise = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			promise = mtPublix.startComponent(studyId, componentId);
			break;
		case JatosWorker.WORKER_TYPE:
			promise = jatosPublix.startComponent(studyId, componentId);
			break;
		case PMWorker.WORKER_TYPE:
			promise = pmPublix.startComponent(studyId, componentId);
			break;
		case ClosedStandaloneWorker.WORKER_TYPE:
			promise = closedStandalonePublix.startComponent(studyId,
					componentId);
			break;
		case OpenStandaloneWorker.WORKER_TYPE:
			promise = openStandalonePublix.startComponent(studyId, componentId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return promise;
	}

	@Override
	@Transactional
	public Promise<Result> startComponentByPosition(Long studyId,
			Integer position) throws PublixException {
		// This method calls startComponent(). Therefore no synchronisation
		// and JPA transaction handling
		Promise<Result> promise = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			promise = mtPublix.startComponentByPosition(studyId, position);
			break;
		case JatosWorker.WORKER_TYPE:
			promise = jatosPublix.startComponentByPosition(studyId, position);
			break;
		case PMWorker.WORKER_TYPE:
			promise = pmPublix.startComponentByPosition(studyId, position);
			break;
		case ClosedStandaloneWorker.WORKER_TYPE:
			promise = closedStandalonePublix.startComponentByPosition(studyId,
					position);
			break;
		case OpenStandaloneWorker.WORKER_TYPE:
			promise = openStandalonePublix.startComponentByPosition(studyId,
					position);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return promise;
	}

	@Override
	@Transactional
	public Result startNextComponent(Long studyId) throws PublixException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.startNextComponent(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.startNextComponent(studyId);
			break;
		case PMWorker.WORKER_TYPE:
			result = pmPublix.startNextComponent(studyId);
			break;
		case ClosedStandaloneWorker.WORKER_TYPE:
			result = closedStandalonePublix.startNextComponent(studyId);
			break;
		case OpenStandaloneWorker.WORKER_TYPE:
			result = openStandalonePublix.startNextComponent(studyId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Result getInitData(Long studyId, Long componentId)
			throws PublixException, IOException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.getInitData(studyId, componentId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.getInitData(studyId, componentId);
			break;
		case PMWorker.WORKER_TYPE:
			result = pmPublix.getInitData(studyId, componentId);
			break;
		case ClosedStandaloneWorker.WORKER_TYPE:
			result = closedStandalonePublix.getInitData(studyId, componentId);
			break;
		case OpenStandaloneWorker.WORKER_TYPE:
			result = openStandalonePublix.getInitData(studyId, componentId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Result setStudySessionData(Long studyId) throws PublixException,
			JsonProcessingException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.setStudySessionData(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.setStudySessionData(studyId);
			break;
		case PMWorker.WORKER_TYPE:
			result = pmPublix.setStudySessionData(studyId);
			break;
		case ClosedStandaloneWorker.WORKER_TYPE:
			result = closedStandalonePublix.setStudySessionData(studyId);
			break;
		case OpenStandaloneWorker.WORKER_TYPE:
			result = openStandalonePublix.setStudySessionData(studyId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Result submitResultData(Long studyId, Long componentId)
			throws PublixException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.submitResultData(studyId, componentId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.submitResultData(studyId, componentId);
			break;
		case PMWorker.WORKER_TYPE:
			result = pmPublix.submitResultData(studyId, componentId);
			break;
		case ClosedStandaloneWorker.WORKER_TYPE:
			result = closedStandalonePublix.submitResultData(studyId,
					componentId);
			break;
		case OpenStandaloneWorker.WORKER_TYPE:
			result = openStandalonePublix
					.submitResultData(studyId, componentId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Result finishComponent(Long studyId, Long componentId,
			Boolean successful, String errorMsg) throws PublixException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.finishComponent(studyId, componentId, successful,
					errorMsg);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.finishComponent(studyId, componentId,
					successful, errorMsg);
			break;
		case PMWorker.WORKER_TYPE:
			result = pmPublix.finishComponent(studyId, componentId, successful,
					errorMsg);
			break;
		case ClosedStandaloneWorker.WORKER_TYPE:
			result = closedStandalonePublix.finishComponent(studyId,
					componentId, successful, errorMsg);
			break;
		case OpenStandaloneWorker.WORKER_TYPE:
			result = openStandalonePublix.finishComponent(studyId, componentId,
					successful, errorMsg);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Result abortStudy(Long studyId, String message)
			throws PublixException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.abortStudy(studyId, message);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.abortStudy(studyId, message);
			break;
		case PMWorker.WORKER_TYPE:
			result = pmPublix.abortStudy(studyId, message);
			break;
		case ClosedStandaloneWorker.WORKER_TYPE:
			result = closedStandalonePublix.abortStudy(studyId, message);
			break;
		case OpenStandaloneWorker.WORKER_TYPE:
			result = openStandalonePublix.abortStudy(studyId, message);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.finishStudy(studyId, successful, errorMsg);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.finishStudy(studyId, successful, errorMsg);
			break;
		case PMWorker.WORKER_TYPE:
			result = pmPublix.finishStudy(studyId, successful, errorMsg);
			break;
		case ClosedStandaloneWorker.WORKER_TYPE:
			result = closedStandalonePublix.finishStudy(studyId, successful,
					errorMsg);
			break;
		case OpenStandaloneWorker.WORKER_TYPE:
			result = openStandalonePublix.finishStudy(studyId, successful,
					errorMsg);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		session().remove(WORKER_TYPE);
		return result;
	}

	@Override
	public Result logError(Long studyId, Long componentId)
			throws BadRequestPublixException {
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			return mtPublix.logError(studyId, componentId);
		case JatosWorker.WORKER_TYPE:
			return jatosPublix.logError(studyId, componentId);
		case PMWorker.WORKER_TYPE:
			return pmPublix.logError(studyId, componentId);
		case ClosedStandaloneWorker.WORKER_TYPE:
			return closedStandalonePublix.logError(studyId, componentId);
		case OpenStandaloneWorker.WORKER_TYPE:
			return openStandalonePublix.logError(studyId, componentId);
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
	}

	/**
	 * Checks session which type of worker is doing the study. Returns a String
	 * specifying the worker type.
	 */
	private String getWorkerTypeFromSession() throws BadRequestPublixException {
		// Check if we have workerType in session
		String workerType = session(WORKER_TYPE);
		if (workerType != null) {
			return workerType;
		}
		throw new BadRequestPublixException(
				PublixErrorMessages.NO_WORKER_IN_SESSION);
	}

	/**
	 * Checks the request's query string which type of worker is doing the
	 * study. Returns a String specifying the worker type. Before a study is
	 * started the worker type is specified via a parameter in the query string.
	 */
	private String getWorkerTypeFromQuery() throws BadRequestPublixException {
		// Check for JATOS worker
		String jatosWorkerId = Publix
				.getQueryString(JatosPublix.JATOS_WORKER_ID);
		if (jatosWorkerId != null) {
			return JatosWorker.WORKER_TYPE;
		}
		// Check for MT worker and MT Sandbox worker
		String mtWorkerId = Publix.getQueryString(MTPublix.MT_WORKER_ID);
		if (mtWorkerId != null) {
			String turkSubmitTo = request().getQueryString(
					MTPublix.TURK_SUBMIT_TO);
			if (turkSubmitTo != null
					&& turkSubmitTo.toLowerCase().contains(MTPublix.SANDBOX)) {
				return MTSandboxWorker.WORKER_TYPE;
			} else {
				return MTWorker.WORKER_TYPE;
			}
		}
		// Check for personal multiple worker
		String pmWorkerId = Publix
				.getQueryString(PMPublix.PERSONAL_MULTIPLE_ID);
		if (pmWorkerId != null) {
			return PMWorker.WORKER_TYPE;
		}
		// Check for closed standalone worker
		String closedStandaloneWorkerId = Publix
				.getQueryString(ClosedStandalonePublix.CLOSEDSTANDALONE_WORKER_ID);
		if (closedStandaloneWorkerId != null) {
			return ClosedStandaloneWorker.WORKER_TYPE;
		}
		// Check for open standalone worker
		String openStandalone = Publix
				.getQueryString(OpenStandalonePublix.OPENSTANDALONE);
		if (openStandalone != null) {
			return OpenStandaloneWorker.WORKER_TYPE;
		}
		throw new BadRequestPublixException(
				PublixErrorMessages.UNKNOWN_WORKER_TYPE);
	}

}
