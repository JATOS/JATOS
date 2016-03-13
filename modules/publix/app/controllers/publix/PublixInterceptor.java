package controllers.publix;

import java.io.IOException;

import javax.inject.Singleton;

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
import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import play.Logger;
import play.Play;
import play.db.jpa.Transactional;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import play.mvc.With;
import services.publix.PublixErrorMessages;

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

	@Override
	@Transactional
	public Result startStudy(Long studyId, Long batchId)
			throws PublixException {
		Result result = null;
		String workerType = getWorkerTypeFromQuery();
		// Put worker type into session so later Publix calls of this study
		// run know it too
		session(WORKER_TYPE, workerType);
		switch (workerType) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = instanceOfPublix(MTPublix.class).startStudy(studyId,
					batchId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class).startStudy(studyId,
					batchId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.startStudy(studyId, batchId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.startStudy(studyId, batchId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
					.startStudy(studyId, batchId);
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
			promise = instanceOfPublix(MTPublix.class).startComponent(studyId,
					componentId);
			break;
		case JatosWorker.WORKER_TYPE:
			promise = instanceOfPublix(JatosPublix.class)
					.startComponent(studyId, componentId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			promise = instanceOfPublix(PersonalMultiplePublix.class)
					.startComponent(studyId, componentId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			promise = instanceOfPublix(PersonalSinglePublix.class)
					.startComponent(studyId, componentId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			promise = instanceOfPublix(GeneralSinglePublix.class)
					.startComponent(studyId, componentId);
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
			promise = instanceOfPublix(MTPublix.class)
					.startComponentByPosition(studyId, position);
			break;
		case JatosWorker.WORKER_TYPE:
			promise = instanceOfPublix(JatosPublix.class)
					.startComponentByPosition(studyId, position);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			promise = instanceOfPublix(PersonalMultiplePublix.class)
					.startComponentByPosition(studyId, position);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			promise = instanceOfPublix(PersonalSinglePublix.class)
					.startComponentByPosition(studyId, position);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			promise = instanceOfPublix(GeneralSinglePublix.class)
					.startComponentByPosition(studyId, position);
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
			result = instanceOfPublix(MTPublix.class)
					.startNextComponent(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class)
					.startNextComponent(studyId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.startNextComponent(studyId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.startNextComponent(studyId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
					.startNextComponent(studyId);
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
			result = instanceOfPublix(MTPublix.class).getInitData(studyId,
					componentId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class).getInitData(studyId,
					componentId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.getInitData(studyId, componentId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.getInitData(studyId, componentId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
					.getInitData(studyId, componentId);
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
			throws BadRequestPublixException {
		WebSocket<JsonNode> result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = instanceOfPublix(MTPublix.class).joinGroup(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class).joinGroup(studyId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.joinGroup(studyId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.joinGroup(studyId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
					.joinGroup(studyId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Result reassignGroup(Long studyId) throws PublixException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = instanceOfPublix(MTPublix.class).reassignGroup(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class).reassignGroup(studyId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.reassignGroup(studyId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.reassignGroup(studyId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
					.reassignGroup(studyId);
			break;
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
		return result;
	}

	@Override
	@Transactional
	public Result leaveGroup(Long studyId)
			throws BadRequestPublixException, NotFoundPublixException,
			ForbiddenPublixException, InternalServerErrorPublixException {
		Result result = null;
		switch (getWorkerTypeFromSession()) {
		case MTWorker.WORKER_TYPE:
		case MTSandboxWorker.WORKER_TYPE:
			result = instanceOfPublix(MTPublix.class).leaveGroup(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class).leaveGroup(studyId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.leaveGroup(studyId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.leaveGroup(studyId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
					.leaveGroup(studyId);
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
			result = instanceOfPublix(MTPublix.class)
					.setStudySessionData(studyId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class)
					.setStudySessionData(studyId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.setStudySessionData(studyId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.setStudySessionData(studyId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
					.setStudySessionData(studyId);
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
			result = instanceOfPublix(MTPublix.class).submitResultData(studyId,
					componentId);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class)
					.submitResultData(studyId, componentId);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.submitResultData(studyId, componentId);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.submitResultData(studyId, componentId);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
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
			result = instanceOfPublix(MTPublix.class).finishComponent(studyId,
					componentId, successful, errorMsg);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class).finishComponent(
					studyId, componentId, successful, errorMsg);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.finishComponent(studyId, componentId, successful,
							errorMsg);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.finishComponent(studyId, componentId, successful,
							errorMsg);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
					.finishComponent(studyId, componentId, successful,
							errorMsg);
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
			result = instanceOfPublix(MTPublix.class).abortStudy(studyId,
					message);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class).abortStudy(studyId,
					message);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.abortStudy(studyId, message);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.abortStudy(studyId, message);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
					.abortStudy(studyId, message);
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
			result = instanceOfPublix(MTPublix.class).finishStudy(studyId,
					successful, errorMsg);
			break;
		case JatosWorker.WORKER_TYPE:
			result = instanceOfPublix(JatosPublix.class).finishStudy(studyId,
					successful, errorMsg);
			break;
		case PersonalMultipleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalMultiplePublix.class)
					.finishStudy(studyId, successful, errorMsg);
			break;
		case PersonalSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(PersonalSinglePublix.class)
					.finishStudy(studyId, successful, errorMsg);
			break;
		case GeneralSingleWorker.WORKER_TYPE:
			result = instanceOfPublix(GeneralSinglePublix.class)
					.finishStudy(studyId, successful, errorMsg);
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
			return instanceOfPublix(MTPublix.class).log(studyId, componentId);
		case JatosWorker.WORKER_TYPE:
			return instanceOfPublix(JatosPublix.class).log(studyId,
					componentId);
		case PersonalMultipleWorker.WORKER_TYPE:
			return instanceOfPublix(PersonalMultiplePublix.class).log(studyId,
					componentId);
		case PersonalSingleWorker.WORKER_TYPE:
			return instanceOfPublix(PersonalSinglePublix.class).log(studyId,
					componentId);
		case GeneralSingleWorker.WORKER_TYPE:
			return instanceOfPublix(GeneralSinglePublix.class).log(studyId,
					componentId);
		default:
			throw new BadRequestPublixException(
					PublixErrorMessages.UNKNOWN_WORKER_TYPE);
		}
	}

	/**
	 * Uses Guice to create a new instance of the given class, a class that must
	 * inherit from Publix.
	 */
	private <T extends Publix<?>> T instanceOfPublix(Class<T> publixClass) {
		return Play.application().injector().instanceOf(publixClass);
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
			String turkSubmitTo = request()
					.getQueryString(MTPublix.TURK_SUBMIT_TO);
			if (turkSubmitTo != null
					&& turkSubmitTo.toLowerCase().contains(MTPublix.SANDBOX)) {
				return MTSandboxWorker.WORKER_TYPE;
			} else {
				return MTWorker.WORKER_TYPE;
			}
		}
		// Check for Personal Multiple Worker
		String pmWorkerId = Publix.getQueryString(
				PersonalMultiplePublix.PERSONAL_MULTIPLE_WORKER_ID);
		if (pmWorkerId != null) {
			return PersonalMultipleWorker.WORKER_TYPE;
		}
		// Check for Personal Single Worker
		String personalSingleWorkerId = Publix
				.getQueryString(PersonalSinglePublix.PERSONAL_SINGLE_WORKER_ID);
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
