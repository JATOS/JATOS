package controllers.publix;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import play.Logger;
import play.db.jpa.Transactional;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import play.mvc.With;
import services.publix.PublixErrorMessages;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.publix.workers.GeneralSinglePublix;
import controllers.publix.workers.JatosPublix;
import controllers.publix.workers.MTPublix;
import controllers.publix.workers.PersonalMultiplePublix;
import controllers.publix.workers.PersonalSinglePublix;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.InternalServerErrorPublixException;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;

/**
 * Interceptor for Publix: it intercepts requests for JATOS' public API (Publix)
 * and forwards them to one of the implementations of the API (all extend
 * Publix). Each implementation deals with different workers (e.g. workers from
 * MechTurk, Personal Multiple workers).
 * 
 * When a study is started the implementation to use is determined by parameters
 * in the request's query string. Then the worker type is put into the session
 * and used in subsequent requests of the same study run.
 * 
 * 1. Requests coming from MechTurk or MechTurk Sandbox will be forwarded to
 * MTPublix. They use MTWorker and MTSandboxWorker.<br>
 * 2. Requests coming from Jatos' UI run (if clicked on show study/component
 * button) run will be forwarded to JatosPublix. They use JatosWorker.<br>
 * 3. Requests coming from a Personal Multiple run will be forwarded to
 * PersonalMultiplePublix. They use PersonalMultipleWorker.<br>
 * 4. Requests coming from a Personal Single run will be forwarded to
 * PersonalSinglePublix. They use PersonalSingleWorker.<br>
 * 5. Requests coming from an General Single run will be forwarded to
 * GeneralSinglePublix. They use the GeneralSingleWorker.<br>
 * 
 * @author Kristian Lange
 */
@Singleton
@With(PublixAction.class)
public class PublixInterceptor extends Controller implements IPublix {

	private static final String CLASS_NAME = PublixInterceptor.class
			.getSimpleName();

	public static final String WORKER_TYPE = "workerType";

	private final JatosPublix jatosPublix;
	private final MTPublix mtPublix;
	private final PersonalMultiplePublix pmPublix;
	private final PersonalSinglePublix personalSinglePublix;
	private final GeneralSinglePublix generalSinglePublix;

	@Inject
	PublixInterceptor(JatosPublix jatosPublix, MTPublix mtPublix,
			PersonalMultiplePublix pmPublix,
			PersonalSinglePublix personalSinglePublix,
			GeneralSinglePublix generalSinglePublix) {
		this.jatosPublix = jatosPublix;
		this.mtPublix = mtPublix;
		this.pmPublix = pmPublix;
		this.personalSinglePublix = personalSinglePublix;
		this.generalSinglePublix = generalSinglePublix;
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
		case PersonalMultipleWorker.WORKER_TYPE:
			result = pmPublix.startStudy(studyId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = personalSinglePublix.startStudy(studyId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = generalSinglePublix.startStudy(studyId);
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
		case PersonalMultipleWorker.WORKER_TYPE:
			promise = pmPublix.startComponent(studyId, componentId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			promise = personalSinglePublix.startComponent(studyId, componentId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			promise = generalSinglePublix.startComponent(studyId, componentId);
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
		case PersonalMultipleWorker.WORKER_TYPE:
			promise = pmPublix.startComponentByPosition(studyId, position);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			promise = personalSinglePublix.startComponentByPosition(studyId,
					position);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			promise = generalSinglePublix.startComponentByPosition(studyId,
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
		case PersonalMultipleWorker.WORKER_TYPE:
			result = pmPublix.startNextComponent(studyId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = personalSinglePublix.startNextComponent(studyId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = generalSinglePublix.startNextComponent(studyId);
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
		case PersonalMultipleWorker.WORKER_TYPE:
			result = pmPublix.getInitData(studyId, componentId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = personalSinglePublix.getInitData(studyId, componentId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = generalSinglePublix.getInitData(studyId, componentId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public WebSocket<JsonNode> joinGroup(Long studyId)
			throws BadRequestPublixException, NotFoundPublixException,
			ForbiddenPublixException {
		WebSocket<JsonNode> result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.joinGroup(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.joinGroup(studyId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = pmPublix.joinGroup(studyId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = personalSinglePublix.joinGroup(studyId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = generalSinglePublix.joinGroup(studyId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Result leaveGroup(Long studyId) throws BadRequestPublixException,
			NotFoundPublixException, ForbiddenPublixException,
			InternalServerErrorPublixException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.leaveGroup(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.leaveGroup(studyId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = pmPublix.leaveGroup(studyId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = personalSinglePublix.leaveGroup(studyId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = generalSinglePublix.leaveGroup(studyId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Result setStudySessionData(Long studyId) throws PublixException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = mtPublix.setStudySessionData(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = jatosPublix.setStudySessionData(studyId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = pmPublix.setStudySessionData(studyId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = personalSinglePublix.setStudySessionData(studyId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = generalSinglePublix.setStudySessionData(studyId);
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
		case PersonalMultipleWorker.WORKER_TYPE:
			result = pmPublix.submitResultData(studyId, componentId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = personalSinglePublix
					.submitResultData(studyId, componentId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = generalSinglePublix.submitResultData(studyId, componentId);
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
		case PersonalMultipleWorker.WORKER_TYPE:
			result = pmPublix.finishComponent(studyId, componentId, successful,
					errorMsg);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = personalSinglePublix.finishComponent(studyId, componentId,
					successful, errorMsg);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = generalSinglePublix.finishComponent(studyId, componentId,
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
		case PersonalMultipleWorker.WORKER_TYPE:
			result = pmPublix.abortStudy(studyId, message);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = personalSinglePublix.abortStudy(studyId, message);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = generalSinglePublix.abortStudy(studyId, message);
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
		case PersonalMultipleWorker.WORKER_TYPE:
			result = pmPublix.finishStudy(studyId, successful, errorMsg);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = personalSinglePublix.finishStudy(studyId, successful,
					errorMsg);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = generalSinglePublix.finishStudy(studyId, successful,
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
	@Transactional
	public Result log(Long studyId, Long componentId) throws PublixException {
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			return mtPublix.log(studyId, componentId);
		case JatosWorker.WORKER_TYPE:
			return jatosPublix.log(studyId, componentId);
		case PersonalMultipleWorker.WORKER_TYPE:
			return pmPublix.log(studyId, componentId);
		case PersonalSingleWorker.WORKER_TYPE:
			return personalSinglePublix.log(studyId, componentId);
		case GeneralSingleWorker.WORKER_TYPE:
			return generalSinglePublix.log(studyId, componentId);
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
		Logger.warn(CLASS_NAME + ".getWorkerTypeFromSession: Could not find "
				+ "a worker type in session for URI " + request().uri());
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
		// Check for Personal Multiple Worker
		String pmWorkerId = Publix
				.getQueryString(PersonalMultiplePublix.PERSONAL_MULTIPLE_ID);
		if (pmWorkerId != null) {
			return PersonalMultipleWorker.WORKER_TYPE;
		}
		// Check for Personal Single Worker
		String personalSingleWorkerId = Publix
				.getQueryString(PersonalSinglePublix.PERSONALSINGLE_WORKER_ID);
		if (personalSingleWorkerId != null) {
			return PersonalSingleWorker.WORKER_TYPE;
		}
		// Check for General Single Worker
		String generalSingle = Publix
				.getQueryString(GeneralSinglePublix.GENERALSINGLE);
		if (generalSingle != null) {
			return GeneralSingleWorker.WORKER_TYPE;
		}
		throw new BadRequestPublixException(
				PublixErrorMessages.UNKNOWN_WORKER_TYPE);
	}

}