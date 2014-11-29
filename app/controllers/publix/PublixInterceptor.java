package controllers.publix;

import models.workers.JatosWorker;
import models.workers.MTWorker;
import models.workers.StandaloneWorker;
import play.db.jpa.JPA;
import play.db.jpa.Transactional;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Result;
import services.ErrorMessages;

import com.fasterxml.jackson.core.JsonProcessingException;

import exceptions.BadRequestPublixException;
import exceptions.PublixException;

/**
 * Interceptor for Publix: it intercepts requests for JATOS' public API and
 * forwards them to one of the implementations of the API. Right now two
 * implementations exists: MTPublix for studies originating from MTurk and
 * JatosPublix for studies and components started from within JATOS' UI.
 * 
 * TODO: Move @Transactional out of controller and get rid of synchronisation
 * and JPA transaction handling
 * 
 * @author Kristian Lange
 */
public class PublixInterceptor extends Controller implements IPublix {

	private IPublix jatosPublix = new JatosPublix();
	private IPublix mtPublix = new MTPublix();

	private static Object lock = new Object();

	@Override
	@Transactional
	public Result startStudy(Long studyId) throws PublixException {
		synchronized (lock) {
			Result result = null;
			switch (getWorkerType()) {
			case MTWorker.WORKER_TYPE:
				result = mtPublix.startStudy(studyId);
			case JatosWorker.WORKER_TYPE:
				result = jatosPublix.startStudy(studyId);
//			case StandaloneWorker.WORKER_TYPE:
//				break;
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	@Transactional
	public Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException {
		synchronized (lock) {
			Promise<Result> promise = null;
			switch (getWorkerType()) {
			case MTWorker.WORKER_TYPE:
				promise = mtPublix.startComponent(studyId, componentId);
			case JatosWorker.WORKER_TYPE:
				promise = jatosPublix.startComponent(studyId, componentId);
//			case StandaloneWorker.WORKER_TYPE:
//				break;
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return promise;
		}
	}

	@Override
	@Transactional
	public Promise<Result> startComponentByPosition(Long studyId,
			Integer position) throws PublixException {
		// This method calls startComponent(). Therefore no synchronisation
		// and JPA transaction handling
		Promise<Result> promise = null;
		switch (getWorkerType()) {
		case MTWorker.WORKER_TYPE:
			promise = mtPublix.startComponentByPosition(studyId, position);
		case JatosWorker.WORKER_TYPE:
			promise = jatosPublix.startComponentByPosition(studyId, position);
//		case StandaloneWorker.WORKER_TYPE:
//			break;
		}
		return promise;
	}

	@Override
	@Transactional
	public Result startNextComponent(Long studyId) throws PublixException {
		synchronized (lock) {
			Result result = null;
			switch (getWorkerType()) {
			case MTWorker.WORKER_TYPE:
				result = mtPublix.startNextComponent(studyId);
			case JatosWorker.WORKER_TYPE:
				result = jatosPublix.startNextComponent(studyId);
//			case StandaloneWorker.WORKER_TYPE:
//				break;
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	@Transactional
	public Result getStudyData(Long studyId) throws PublixException,
			JsonProcessingException {
		synchronized (lock) {
			Result result = null;
			switch (getWorkerType()) {
			case MTWorker.WORKER_TYPE:
				result = mtPublix.getStudyData(studyId);
			case JatosWorker.WORKER_TYPE:
				result = jatosPublix.getStudyData(studyId);
//			case StandaloneWorker.WORKER_TYPE:
//				break;
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	@Transactional
	public Result getComponentData(Long studyId, Long componentId)
			throws PublixException, JsonProcessingException {
		synchronized (lock) {
			Result result = null;
			switch (getWorkerType()) {
			case MTWorker.WORKER_TYPE:
				result = mtPublix.getComponentData(studyId, componentId);
			case JatosWorker.WORKER_TYPE:
				result = jatosPublix.getComponentData(studyId, componentId);
//			case StandaloneWorker.WORKER_TYPE:
//				break;
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	@Transactional
	public Result submitResultData(Long studyId, Long componentId)
			throws PublixException {
		synchronized (lock) {
			Result result = null;
			switch (getWorkerType()) {
			case MTWorker.WORKER_TYPE:
				result = mtPublix.submitResultData(studyId, componentId);
			case JatosWorker.WORKER_TYPE:
				result = jatosPublix.submitResultData(studyId, componentId);
//			case StandaloneWorker.WORKER_TYPE:
//				break;
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	@Transactional
	public Result finishComponent(Long studyId, Long componentId,
			Boolean successful, String errorMsg) throws PublixException {
		synchronized (lock) {
			Result result = null;
			switch (getWorkerType()) {
			case MTWorker.WORKER_TYPE:
				result = mtPublix.finishComponent(studyId, componentId,
						successful, errorMsg);
			case JatosWorker.WORKER_TYPE:
				result = jatosPublix.finishComponent(studyId, componentId,
						successful, errorMsg);
//			case StandaloneWorker.WORKER_TYPE:
//				break;
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	@Transactional
	public Result abortStudy(Long studyId, String message)
			throws PublixException {
		synchronized (lock) {
			Result result = null;
			switch (getWorkerType()) {
			case MTWorker.WORKER_TYPE:
				result = mtPublix.abortStudy(studyId, message);
			case JatosWorker.WORKER_TYPE:
				result = jatosPublix.abortStudy(studyId, message);
//			case StandaloneWorker.WORKER_TYPE:
//				break;
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	@Transactional
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException {
		synchronized (lock) {
			Result result = null;
			switch (getWorkerType()) {
			case MTWorker.WORKER_TYPE:
				result = mtPublix.finishStudy(studyId, successful, errorMsg);
			case JatosWorker.WORKER_TYPE:
				result = jatosPublix.finishStudy(studyId, successful, errorMsg);
//			case StandaloneWorker.WORKER_TYPE:
//				break;
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	public Result logError(Long studyId, Long componentId)
			throws BadRequestPublixException {
		switch (getWorkerType()) {
		case MTWorker.WORKER_TYPE:
			return mtPublix.logError(studyId, componentId);
		case JatosWorker.WORKER_TYPE:
			return jatosPublix.logError(studyId, componentId);
//		case StandaloneWorker.WORKER_TYPE:
//			break;
		}
		return null;
	}

	@Override
	public Result teapot() throws BadRequestPublixException {
		switch (getWorkerType()) {
		case MTWorker.WORKER_TYPE:
			return mtPublix.teapot();
		case JatosWorker.WORKER_TYPE:
			return jatosPublix.teapot();
//		case StandaloneWorker.WORKER_TYPE:
//			break;
		}
		return null;
	}

	/**
	 * Checks which type of worker is doing the study. Returns a String
	 * specifying the worker type.
	 */
	private String getWorkerType() throws BadRequestPublixException {
		if (session(JatosPublix.JATOS_SHOW) != null) {
			return JatosWorker.WORKER_TYPE;
		}
		String mtWorkerId = Publix.getQueryString("workerId");
		String mtTester = Publix.getQueryString("mtTester");
		if (mtWorkerId != null || mtTester != null) {
			return MTWorker.WORKER_TYPE;
		}
		String standaloneWorkerId = Publix.getQueryString("standaloneWorkerId");
		if (standaloneWorkerId != null) {
			return StandaloneWorker.WORKER_TYPE;
		}
		throw new BadRequestPublixException(ErrorMessages.UNKNOWN_WORKER_TYPE);
	}

}
