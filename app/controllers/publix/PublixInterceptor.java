package controllers.publix;

import play.db.jpa.JPA;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;

import com.fasterxml.jackson.core.JsonProcessingException;

import exceptions.PublixException;

/**
 * Interceptor for Publix: it intercepts requests for MechArg's public API and
 * forwards them to one of the implementations of the API. Right now two
 * implementations exists: MTPublix for studies originating from MTurk and
 * MAPublix for studies and components started from within MechArg's UI.
 * 
 * TODO: Move @Transactional out of controller and get rid off synchronisation
 * and JPA transaction handling
 * 
 * @author Kristian Lange
 */
public class PublixInterceptor extends Controller implements IPublix {

	private IPublix maPublix = new MAPublix();
	private IPublix mtPublix = new MTPublix();

	private static Object lock = new Object();

	@Override
	@Transactional
	public Result startStudy(Long studyId) throws PublixException {
		synchronized (lock) {
			Result result;
			if (isFromMechArg()) {
				result = maPublix.startStudy(studyId);
			} else {
				result = mtPublix.startStudy(studyId);
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	@Transactional
	public Result startComponent(Long studyId, Long componentId)
			throws PublixException {
		synchronized (lock) {
			Result result;
			if (isFromMechArg()) {
				result = maPublix.startComponent(studyId, componentId);
			} else {
				result = mtPublix.startComponent(studyId, componentId);
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	@Transactional
	public Result startNextComponent(Long studyId) throws PublixException {
		synchronized (lock) {
			Result result;
			if (isFromMechArg()) {
				result = maPublix.startNextComponent(studyId);
			} else {
				result = mtPublix.startNextComponent(studyId);
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
			Result result;
			if (isFromMechArg()) {
				result = maPublix.getStudyData(studyId);
			} else {
				result = mtPublix.getStudyData(studyId);
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
			Result result;
			if (isFromMechArg()) {
				result = maPublix.getComponentData(studyId, componentId);
			} else {
				result = mtPublix.getComponentData(studyId, componentId);
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
			Result result;
			if (isFromMechArg()) {
				result = maPublix.submitResultData(studyId, componentId);
			} else {
				result = mtPublix.submitResultData(studyId, componentId);
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
			Result result;
			if (isFromMechArg()) {
				result = maPublix.finishStudy(studyId, successful, errorMsg);
			} else {
				result = mtPublix.finishStudy(studyId, successful, errorMsg);
			}
			JPA.em().flush();
			JPA.em().getTransaction().commit();
			JPA.em().getTransaction().begin();
			return result;
		}
	}

	@Override
	public Result logError() {
		if (isFromMechArg()) {
			return maPublix.logError();
		} else {
			return mtPublix.logError();
		}
	}

	@Override
	public Result teapot() {
		if (isFromMechArg()) {
			return maPublix.teapot();
		} else {
			return mtPublix.teapot();
		}
	}

	/**
	 * Check if this request originates from within MechArg.
	 */
	private boolean isFromMechArg() {
		if (session(MAPublix.MECHARG_SHOW) != null) {
			return true;
		}
		return false;
	}

}
