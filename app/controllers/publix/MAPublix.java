package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.results.ComponentResult;
import models.results.StudyResult;
import models.workers.MAWorker;
import play.Logger;
import play.libs.F.Promise;
import play.mvc.Result;
import services.ErrorMessages;
import services.MAErrorMessages;
import services.PersistanceUtils;
import controllers.ControllerUtils;
import controllers.Users;
import controllers.routes;
import exceptions.ForbiddenPublixException;
import exceptions.ForbiddenReloadException;
import exceptions.PublixException;

/**
 * Implementation of MechArg's public API for studies and components that are
 * started via MechArg's UI.
 * 
 * @author Kristian Lange
 */
public class MAPublix extends Publix<MAWorker> implements IPublix {

	public static final String MECHARG_SHOW = "mecharg_show";
	public static final String SHOW_STUDY = "full_study";
	public static final String SHOW_COMPONENT_START = "single_component_start";
	public static final String SHOW_COMPONENT_FINISHED = "single_component_finished";

	private static final String CLASS_NAME = MAPublix.class.getSimpleName();

	protected static final MAErrorMessages errorMessages = new MAErrorMessages();
	protected static final MAPublixUtils utils = new MAPublixUtils(
			errorMessages);

	public MAPublix() {
		super(utils);
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		ComponentModel firstComponent = utils
				.retrieveFirstActiveComponent(study);
		utils.checkWorkerAllowedToDoStudy(worker, study);

		String mechArgShow = utils.retrieveMechArgShowCookie();
		if (!mechArgShow.equals(SHOW_STUDY)) {
			throw new ForbiddenPublixException(
					ErrorMessages.STUDY_NEVER_STARTED_FROM_MECHARG);
		}
		utils.finishAllPriorStudyResults(worker, study);
		PersistanceUtils.createStudyResult(study, worker);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

	@Override
	public Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		ComponentModel component = utils.retrieveComponent(study, componentId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		utils.checkComponentBelongsToStudy(study, component);

		// Check if it's a single component show or a whole study show
		String mechArgShow = utils.retrieveMechArgShowCookie();
		StudyResult studyResult = null;
		switch (mechArgShow) {
		case SHOW_STUDY:
			studyResult = utils.retrieveWorkersLastStudyResult(worker, study);
			break;
		case SHOW_COMPONENT_START:
			// Just create a StudyResult for this.
			studyResult = PersistanceUtils.createStudyResult(study, worker);
			session(MAPublix.MECHARG_SHOW, MAPublix.SHOW_COMPONENT_FINISHED);
			break;
		case SHOW_COMPONENT_FINISHED:
			studyResult = utils.retrieveWorkersLastStudyResult(worker, study);
			ComponentResult lastComponentResult = utils
					.retrieveLastComponentResult(studyResult);
			if (!lastComponentResult.getComponent().equals(component)) {
				// It's already the second component (first is finished and it
				// isn't a reload of the same one). Finish study after first
				// component.
				return Promise
						.pure((Result) redirect(controllers.publix.routes.PublixInterceptor
								.finishStudy(studyId, true, null)));
			}
			break;
		}

		ComponentResult componentResult = null;
		try {
			componentResult = utils.startComponent(component, studyResult);
		} catch (ForbiddenReloadException e) {
			return Promise
					.pure((Result) redirect(controllers.publix.routes.PublixInterceptor
							.finishStudy(studyId, false, e.getMessage())));
		}
		PublixUtils.setIdCookie(studyResult, componentResult, worker);
		String urlPath = ExternalAssets.getComponentUrlPath(study, component);
		String urlWithQueryStr = ExternalAssets
				.getUrlWithRequestQueryString(urlPath);
		return forwardTo(urlWithQueryStr);
	}

	@Override
	public Result startNextComponent(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		utils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);

		// Check if it's a single component show or a whole study show
		String mechArgShow = utils.retrieveMechArgShowCookie();
		switch (mechArgShow) {
		case SHOW_STUDY:
			studyResult = utils.retrieveWorkersLastStudyResult(worker, study);
			break;
		case SHOW_COMPONENT_START:
			// Should never happen
			session(MAPublix.MECHARG_SHOW, MAPublix.SHOW_COMPONENT_FINISHED);
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, null));
		case SHOW_COMPONENT_FINISHED:
			// It's already the second component (first is finished). Finish
			// study after first component.
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, true, null));
		}

		ComponentModel nextComponent = utils
				.retrieveNextActiveComponent(studyResult);
		if (nextComponent == null) {
			// Study has no more components -> finish it
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, true, null));
		}
		String urlWithQueryString = ExternalAssets
				.getUrlWithRequestQueryString(controllers.publix.routes.PublixInterceptor
						.startComponent(studyId, nextComponent.getId()).url());
		return redirect(urlWithQueryString);
	}

	@Override
	public Result abortStudy(Long studyId, String message)
			throws PublixException {
		Logger.info(CLASS_NAME + ".abortStudy: studyId " + studyId + ", "
				+ "logged-in user email " + session(Users.COOKIE_EMAIL) + ", "
				+ "message \"" + message + "\"");
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		utils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		if (!utils.studyDone(studyResult)) {
			utils.abortStudy(message, studyResult);
			Publix.session().remove(MAPublix.MECHARG_SHOW);
		}

		PublixUtils.discardIdCookie();
		if (ControllerUtils.isAjax()) {
			return ok();
		} else {
			return redirect(routes.Studies.index(study.getId(), message));
		}
	}

	@Override
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException {
		Logger.info(CLASS_NAME + ".finishStudy: studyId " + studyId + ", "
				+ "logged-in user email " + session(Users.COOKIE_EMAIL) + ", "
				+ "successful " + successful + ", " + "errorMsg \"" + errorMsg
				+ "\"");
		StudyModel study = utils.retrieveStudy(studyId);
		MAWorker worker = utils.retrieveWorker();
		utils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		if (!utils.studyDone(studyResult)) {
			utils.finishStudy(successful, errorMsg, studyResult);
			Publix.session().remove(MAPublix.MECHARG_SHOW);
		}
		PublixUtils.discardIdCookie();
		if (ControllerUtils.isAjax()) {
			return ok(errorMsg);
		} else {
			return redirect(routes.Studies.index(study.getId(), errorMsg));
		}
	}

}
