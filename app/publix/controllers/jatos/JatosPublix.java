package publix.controllers.jatos;

import models.ComponentModel;
import models.ComponentResult;
import models.GroupResult;
import models.StudyModel;
import models.StudyResult;
import models.workers.JatosWorker;
import persistance.ComponentResultDao;
import persistance.StudyResultDao;
import play.Logger;
import play.libs.F.Promise;
import play.mvc.Result;
import publix.controllers.IPublix;
import publix.controllers.Publix;
import publix.controllers.StudyAssets;
import publix.exceptions.ForbiddenPublixException;
import publix.exceptions.ForbiddenReloadException;
import publix.exceptions.PublixException;
import publix.services.jatos.JatosErrorMessages;
import publix.services.jatos.JatosPublixUtils;
import publix.services.jatos.JatosStudyAuthorisation;
import utils.ControllerUtils;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import common.FlashScopeMessaging;
import controllers.Users;

/**
 * Implementation of JATOS' public API for studies and components that are
 * started via JATOS' UI (show study or show component). A JATOS run is done by
 * a JatosWorker.
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosPublix extends Publix<JatosWorker> implements IPublix {

	/**
	 * Parameter name that is used in a URL query string for an JATOS run.
	 */
	public static final String JATOS_WORKER_ID = "jatosWorkerId";

	/**
	 * Name of the session variable that stores the kind of JATOS run.
	 */
	public static final String JATOS_RUN = "jatos_run";

	/**
	 * Kind of JATOS run: full study.
	 */
	public static final String RUN_STUDY = "full_study";

	/**
	 * Kind of JATOS run: single component. It also indicates that the component
	 * isn't finished.
	 */
	public static final String RUN_COMPONENT_START = "single_component_start";

	/**
	 * Kind of JATOS run: single component. It also indicates that the component
	 * is already finished.
	 */
	public static final String RUN_COMPONENT_FINISHED = "single_component_finished";

	/**
	 * In case of a component JATOS run the Component ID is stored in the
	 * session together with JATOS_RUN. It uses this name.
	 */
	public static final String RUN_COMPONENT_ID = "run_component_id";

	private static final String CLASS_NAME = JatosPublix.class.getSimpleName();

	private final JatosPublixUtils publixUtils;
	private final JatosStudyAuthorisation studyAuthorisation;
	private final JatosErrorMessages errorMessages;

	@Inject
	JatosPublix(JatosPublixUtils publixUtils,
			JatosStudyAuthorisation studyAuthorisation,
			JatosErrorMessages errorMessages,
			StudyAssets studyAssets,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao) {
		super(publixUtils, studyAuthorisation, errorMessages, studyAssets,
				componentResultDao, jsonUtils, studyResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.errorMessages = errorMessages;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = publixUtils.retrieveStudy(studyId);

		JatosWorker worker = publixUtils.retrieveLoggedInUser().getWorker();
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, worker.getId().toString());
		Logger.info(CLASS_NAME + ".startStudy: study (ID " + studyId + ") "
				+ "assigned to worker with ID " + worker.getId());

		Long componentId = null;
		String jatosShow = publixUtils.retrieveJatosShowFromSession();
		switch (jatosShow) {
		case RUN_STUDY:
			componentId = publixUtils.retrieveFirstActiveComponent(study)
					.getId();
			break;
		case RUN_COMPONENT_START:
			componentId = Long.valueOf(session(RUN_COMPONENT_ID));
			session().remove(RUN_COMPONENT_ID);
			break;
		case RUN_COMPONENT_FINISHED:
			throw new ForbiddenPublixException(
					JatosErrorMessages.STUDY_NEVER_STARTED_FROM_JATOS);
		}
		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);
		return redirect(publix.controllers.routes.PublixInterceptor
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
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
		publixUtils.checkComponentBelongsToStudy(study, component);

		// Check if it's a single component show or a whole study show
		String jatosShow = publixUtils.retrieveJatosShowFromSession();
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		switch (jatosShow) {
		case RUN_STUDY:
			break;
		case RUN_COMPONENT_START:
			session(JatosPublix.JATOS_RUN, JatosPublix.RUN_COMPONENT_FINISHED);
			break;
		case RUN_COMPONENT_FINISHED:
			ComponentResult lastComponentResult = publixUtils
					.retrieveLastComponentResult(studyResult);
			if (!lastComponentResult.getComponent().equals(component)) {
				// It's already the second component (first is finished and it
				// isn't a reload of the same one). Finish study after first
				// component.
				return Promise
						.pure(redirect(publix.controllers.routes.PublixInterceptor
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
					.pure(redirect(publix.controllers.routes.PublixInterceptor
							.finishStudy(studyId, false, e.getMessage())));
		}
		GroupResult groupResult = studyResult.getGroupResult();
		response().setCookie(
				Publix.ID_COOKIE_NAME,
				publixUtils.generateIdCookieValue(studyResult, componentResult,
						worker, groupResult));
		String urlPath = StudyAssets.getComponentUrlPath(study.getDirName(),
				component);
		String urlWithQueryStr = StudyAssets.getUrlWithQueryString(request()
				.uri(), request().host(), urlPath);
		return studyAssets.forwardTo(urlWithQueryStr);
	}

	@Override
	public Result startNextComponent(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "workerId " + session(WORKER_ID) + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = publixUtils.retrieveStudy(studyId);
		JatosWorker worker = publixUtils
				.retrieveTypedWorker(session(WORKER_ID));
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);

		// Check if it's a single component show or a whole study show
		String jatosShow = publixUtils.retrieveJatosShowFromSession();
		switch (jatosShow) {
		case RUN_STUDY:
			studyResult = publixUtils.retrieveWorkersLastStudyResult(worker,
					study);
			break;
		case RUN_COMPONENT_START:
			// Should never happen
			session(JatosPublix.JATOS_RUN, JatosPublix.RUN_COMPONENT_FINISHED);
			return redirect(publix.controllers.routes.PublixInterceptor
					.finishStudy(studyId, false, null));
		case RUN_COMPONENT_FINISHED:
			// It's already the second component (first is finished). Finish
			// study after first component.
			return redirect(publix.controllers.routes.PublixInterceptor
					.finishStudy(studyId, true, null));
		}

		ComponentModel nextComponent = publixUtils
				.retrieveNextActiveComponent(studyResult);
		if (nextComponent == null) {
			// Study has no more components -> finish it
			return redirect(publix.controllers.routes.PublixInterceptor
					.finishStudy(studyId, true, null));
		}
		String startComponentUrlPath = publix.controllers.routes.PublixInterceptor
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
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		if (!publixUtils.studyDone(studyResult)) {
			publixUtils.abortStudy(message, studyResult);
			Publix.session().remove(JatosPublix.JATOS_RUN);
		}

		Publix.response().discardCookie(Publix.ID_COOKIE_NAME);
		if (ControllerUtils.isAjax()) {
			return ok().as("text/html");
		} else {
			if (message != null) {
				FlashScopeMessaging.info(errorMessages
						.studyFinishedWithMessage(message));
			}
			return redirect(controllers.routes.Studies.index(study.getId()));
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
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		if (!publixUtils.studyDone(studyResult)) {
			publixUtils.finishStudyResult(successful, errorMsg, studyResult);
			Publix.session().remove(JatosPublix.JATOS_RUN);
		}

		Publix.response().discardCookie(Publix.ID_COOKIE_NAME);
		if (ControllerUtils.isAjax()) {
			return ok(errorMsg);
		} else {
			if (errorMsg != null) {
				FlashScopeMessaging.info(errorMessages
						.studyFinishedWithMessage(errorMsg));
			}
			return redirect(controllers.routes.Studies.index(study.getId()));
		}
	}

}
