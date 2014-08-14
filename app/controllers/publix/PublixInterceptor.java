package controllers.publix;

import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * Interceptor for Publix: it intercepts requests for MechArg's public API and
 * forwards them to one of the implementations of the API. Right now two
 * implementations exists: MTPublix for studies originating from MTurk and
 * MAPublix for studies and components started from within MechArg's UI.
 * 
 * @author madsen
 */
public class PublixInterceptor extends Controller implements IPublix {

	private IPublix maPublix = new MAPublix();
	private IPublix mtPublix = new MTPublix();

	@Override
	@Transactional
	public Result startStudy(Long studyId) throws Exception {
		if (isFromMechArg()) {
			return maPublix.startStudy(studyId);
		} else {
			return mtPublix.startStudy(studyId);
		}
	}

	@Override
	@Transactional
	public Result startComponent(Long studyId, Long componentId)
			throws Exception {
		if (isFromMechArg()) {
			return maPublix.startComponent(studyId, componentId);
		} else {
			return mtPublix.startComponent(studyId, componentId);
		}
	}

	@Override
	@Transactional
	public Result startNextComponent(Long studyId) throws Exception {
		if (isFromMechArg()) {
			return maPublix.startNextComponent(studyId);
		} else {
			return mtPublix.startNextComponent(studyId);
		}
	}

	@Override
	@Transactional
	public Result getComponentData(Long studyId, Long componentId)
			throws Exception {
		if (isFromMechArg()) {
			return maPublix.getComponentData(studyId, componentId);
		} else {
			return mtPublix.getComponentData(studyId, componentId);
		}
	}

	@Override
	@Transactional
	public Result submitResultData(Long studyId, Long componentId)
			throws Exception {
		if (isFromMechArg()) {
			return maPublix.submitResultData(studyId, componentId);
		} else {
			return mtPublix.submitResultData(studyId, componentId);
		}
	}

	@Override
	@Transactional
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws Exception {
		if (isFromMechArg()) {
			return maPublix.finishStudy(studyId, successful, errorMsg);
		} else {
			return mtPublix.finishStudy(studyId, successful, errorMsg);
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

	/**
	 * Check if this request originates from within MechArg.
	 */
	private boolean isFromMechArg() {
		String playCookie = request().cookie("PLAY_SESSION").value();
		boolean isFromMechArg = playCookie.contains(MAPublix.MECHARG_TRY);
		return isFromMechArg;
	}

}
