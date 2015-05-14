package controllers.publix.jatos;

import models.ComponentModel;
import models.ComponentResult;
import models.StudyModel;
import models.StudyResult;
import models.workers.JatosWorker;
import persistance.ComponentResultDao;
import persistance.StudyResultDao;
import play.Logger;
import play.libs.F.Promise;
import play.mvc.Result;
import services.FlashScopeMessaging;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.ControllerUtils;
import controllers.gui.Users;
import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.ForbiddenReloadException;
import exceptions.publix.PublixException;

/**
 * Implementation of JATOS' public API for studies and components that are
 * started via JATOS' UI (show study or show component).
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosPublix extends Publix<JatosWorker> implements IPublix {

	public static final String JATOS_WORKER_ID = "jatosWorkerId";
	public static final String JATOS_SHOW = "jatos_show";
	public static final String SHOW_STUDY = "full_study";
	public static final String SHOW_COMPONENT_START = "single_component_start";
	public static final String SHOW_COMPONENT_FINISHED = "single_component_finished";
	public static final String SHOW_COMPONENT_ID = "show_component_id";

	private static final String CLASS_NAME = JatosPublix.class.getSimpleName();

	private final JatosPublixUtils publixUtils;
	private final JatosErrorMessages errorMessages;

	@Inject
	JatosPublix(JatosPublixUtils publixUtils,
			JatosErrorMessages errorMessages,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao) {
		super(publixUtils, errorMessages, componentResultDao, jsonUtils,
				studyResultDao);
		this.publixUtils = publixUtils;
		this.errorMessages = errorMessages;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = publixUtils.retrieveStudy(studyId);

		JatosWorker worker = publixUtils.retrieveUser().getWorker();
		publixUtils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, worker.getId().toString());
		Logger.info(CLASS_NAME + ".startStudy: study (ID " + studyId + ") "
				+ "assigned to worker with ID " + worker.getId());

		Long componentId = null;
		String jatosShow = publixUtils.retrieveJatosShowFromSession();
		switch (jatosShow) {
		case SHOW_STUDY:
			componentId = publixUtils.retrieveFirstActiveComponent(study)
					.getId();
			break;
		case SHOW_COMPONENT_START:
			componentId = Long.valueOf(session(SHOW_COMPONENT_ID));
			session().remove(SHOW_COMPONENT_ID);
			break;
		case SHOW_COMPONENT_FINISHED:
			throw new ForbiddenPublixException(
					JatosErrorMessages.STUDY_NEVER_STARTED_FROM_JATOS);
		}
		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, componentId));
	}

	@Override
	public Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID) + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = publixUtils.retrieveStudy(studyId);
		JatosWorker worker = publixUtils
				.retrieveTypedWorker(session(WORKER_ID));
		ComponentModel component = publixUtils.retrieveComponent(study,
				componentId);
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);
		publixUtils.checkComponentBelongsToStudy(study, component);

		// Check if it's a single component show or a whole study show
		String jatosShow = publixUtils.retrieveJatosShowFromSession();
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		switch (jatosShow) {
		case SHOW_STUDY:
			break;
		case SHOW_COMPONENT_START:
			session(JatosPublix.JATOS_SHOW, JatosPublix.SHOW_COMPONENT_FINISHED);
			break;
		case SHOW_COMPONENT_FINISHED:
			ComponentResult lastComponentResult = publixUtils
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
			componentResult = publixUtils
					.startComponent(component, studyResult);
		} catch (ForbiddenReloadException e) {
			return Promise
					.pure((Result) redirect(controllers.publix.routes.PublixInterceptor
							.finishStudy(studyId, false, e.getMessage())));
		}
		response().setCookie(
				Publix.ID_COOKIE_NAME,
				publixUtils.getIdCookieValue(studyResult, componentResult,
						worker));
		String urlPath = StudyAssets.getComponentUrlPath(study.getDirName(),
				component);
		String urlWithQueryStr = StudyAssets.getUrlWithQueryString(request()
				.uri(), request().host(), urlPath);
		return forwardTo(urlWithQueryStr);
	}

	@Override
	public Result startNextComponent(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "workerId " + session(WORKER_ID) + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = publixUtils.retrieveStudy(studyId);
		JatosWorker worker = publixUtils
				.retrieveTypedWorker(session(WORKER_ID));
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);

		// Check if it's a single component show or a whole study show
		String jatosShow = publixUtils.retrieveJatosShowFromSession();
		switch (jatosShow) {
		case SHOW_STUDY:
			studyResult = publixUtils.retrieveWorkersLastStudyResult(worker,
					study);
			break;
		case SHOW_COMPONENT_START:
			// Should never happen
			session(JatosPublix.JATOS_SHOW, JatosPublix.SHOW_COMPONENT_FINISHED);
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, null));
		case SHOW_COMPONENT_FINISHED:
			// It's already the second component (first is finished). Finish
			// study after first component.
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, true, null));
		}

		ComponentModel nextComponent = publixUtils
				.retrieveNextActiveComponent(studyResult);
		if (nextComponent == null) {
			// Study has no more components -> finish it
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, true, null));
		}
		String startComponentUrlPath = controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, nextComponent.getId()).url();
		String urlWithQueryString = StudyAssets.getUrlWithQueryString(request()
				.uri(), request().host(), startComponentUrlPath);
		return redirect(urlWithQueryString);
	}

	@Override
	public Result abortStudy(Long studyId, String message)
			throws PublixException {
		Logger.info(CLASS_NAME + ".abortStudy: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID) + ", "
				+ "logged-in user email " + session(Users.SESSION_EMAIL) + ", "
				+ "message \"" + message + "\"");
		StudyModel study = publixUtils.retrieveStudy(studyId);
		JatosWorker worker = publixUtils
				.retrieveTypedWorker(session(WORKER_ID));
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		if (!publixUtils.studyDone(studyResult)) {
			publixUtils.abortStudy(message, studyResult);
			Publix.session().remove(JatosPublix.JATOS_SHOW);
		}

		publixUtils.discardIdCookie();
		if (ControllerUtils.isAjax()) {
			return ok().as("text/html");
		} else {
			if (message != null) {
				FlashScopeMessaging.info(errorMessages
						.studyFinishedWithMessage(message));
			}
			return redirect(controllers.gui.routes.Studies.index(study.getId()));
		}
	}

	@Override
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException {
		Logger.info(CLASS_NAME + ".finishStudy: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID) + ", "
				+ "logged-in user email " + session(Users.SESSION_EMAIL) + ", "
				+ "successful " + successful + ", " + "errorMsg \"" + errorMsg
				+ "\"");
		StudyModel study = publixUtils.retrieveStudy(studyId);
		JatosWorker worker = publixUtils
				.retrieveTypedWorker(session(WORKER_ID));
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		if (!publixUtils.studyDone(studyResult)) {
			publixUtils.finishStudy(successful, errorMsg, studyResult);
			Publix.session().remove(JatosPublix.JATOS_SHOW);
		}

		publixUtils.discardIdCookie();
		if (ControllerUtils.isAjax()) {
			return ok(errorMsg);
		} else {
			if (errorMsg != null) {
				FlashScopeMessaging.info(errorMessages
						.studyFinishedWithMessage(errorMsg));
			}
			return redirect(controllers.gui.routes.Studies.index(study.getId()));
		}
	}

}
