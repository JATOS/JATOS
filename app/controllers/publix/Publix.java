package controllers.publix;

import models.ComponentModel;
import models.ComponentResult;
import models.ComponentResult.ComponentState;
import models.StudyModel;
import models.StudyResult;
import models.StudyResult.StudyState;
import models.workers.Worker;
import persistance.IComponentResultDao;
import persistance.IStudyResultDao;
import play.Logger;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Result;
import utils.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Singleton;

import controllers.gui.ControllerUtils;
import controllers.gui.Users;
import exceptions.publix.ForbiddenReloadException;
import exceptions.publix.PublixException;

/**
 * Abstract controller class for all controller that implement the IPublix
 * interface. It defines common methods and constants.
 * 
 * @author Kristian Lange
 */
@Singleton
public abstract class Publix<T extends Worker> extends Controller implements
		IPublix {

	// ID cookie name and value names
	public static final String ID_COOKIE_NAME = "JATOS_IDS";
	public static final String WORKER_ID = "workerId";
	public static final String STUDY_ID = "studyId";
	public static final String COMPONENT_ID = "componentId";
	public static final String STUDY_RESULT_ID = "studyResultId";
	public static final String COMPONENT_RESULT_ID = "componentResultId";
	public static final String COMPONENT_POSITION = "componentPos";

	private static final String CLASS_NAME = Publix.class.getSimpleName();

	protected final PublixUtils<T> publixUtils;
	protected final JsonUtils jsonUtils;
	protected final IComponentResultDao componentResultDao;
	protected final IStudyResultDao studyResultDao;

	public Publix(PublixUtils<T> utils, IComponentResultDao componentResultDao,
			JsonUtils jsonUtils, IStudyResultDao studyResultDao) {
		this.publixUtils = utils;
		this.componentResultDao = componentResultDao;
		this.jsonUtils = jsonUtils;
		this.studyResultDao = studyResultDao;
	}

	@Override
	public Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = publixUtils.retrieveStudy(studyId);
		ComponentModel component = publixUtils.retrieveComponent(study,
				componentId);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
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
	public Promise<Result> startComponentByPosition(Long studyId,
			Integer position) throws PublixException {
		Logger.info(CLASS_NAME + ".startComponentByPosition: studyId "
				+ studyId + ", " + "position " + position + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		ComponentModel component = publixUtils.retrieveComponentByPosition(
				studyId, position);
		return startComponent(studyId, component.getId());
	}

	@Override
	public Result startNextComponent(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "workerId " + session(WORKER_ID));
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = publixUtils.retrieveStudy(studyId);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);

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
	public Result getStudyProperties(Long studyId) throws PublixException,
			JsonProcessingException {
		Logger.info(CLASS_NAME + ".getStudyProperties: studyId " + studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = publixUtils.retrieveStudy(studyId);
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		studyResultDao.update(studyResult);
		return ok(jsonUtils.asJsonForPublix(study));
	}

	@Override
	public Result getStudySessionData(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".getStudySessionData: studyId " + studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = publixUtils.retrieveStudy(studyId);
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		String studySessionData = studyResult.getStudySessionData();
		return ok(studySessionData);
	}

	@Override
	public Result setStudySessionData(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".setStudySessionData: studyId " + studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = publixUtils.retrieveStudy(studyId);
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		String studySessionData = publixUtils.getDataFromRequestBody(request()
				.body());
		studyResult.setStudySessionData(studySessionData);
		studyResultDao.update(studyResult);
		return ok();
	}

	@Override
	public Result getComponentProperties(Long studyId, Long componentId)
			throws PublixException, JsonProcessingException {
		Logger.info(CLASS_NAME + ".getComponentProperties: studyId " + studyId
				+ ", " + "componentId " + componentId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = publixUtils.retrieveStudy(studyId);
		ComponentModel component = publixUtils.retrieveComponent(study,
				componentId);
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);
		publixUtils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		ComponentState maxAllowedComponentState = ComponentState.STARTED;
		ComponentResult componentResult;
		try {
			componentResult = publixUtils.retrieveStartedComponentResult(
					component, studyResult, maxAllowedComponentState);
		} catch (ForbiddenReloadException e) {
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, e.getMessage()));
		}

		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		componentResultDao.update(componentResult);
		return ok(jsonUtils.asJsonForPublix(component));
	}

	@Override
	public Result submitResultData(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".submitResultData: studyId " + studyId + ", "
				+ "componentId " + componentId);
		StudyModel study = publixUtils.retrieveStudy(studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		ComponentModel component = publixUtils.retrieveComponent(study,
				componentId);
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);
		publixUtils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		ComponentState maxAllowedComponentState = ComponentState.DATA_RETRIEVED;
		ComponentResult componentResult;
		try {
			componentResult = publixUtils.retrieveStartedComponentResult(
					component, studyResult, maxAllowedComponentState);
		} catch (ForbiddenReloadException e) {
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, e.getMessage()));
		}

		String resultData = publixUtils
				.getDataFromRequestBody(request().body());
		componentResult.setData(resultData);
		componentResult.setComponentState(ComponentState.RESULTDATA_POSTED);
		componentResultDao.update(componentResult);
		return ok();
	}

	@Override
	public Result finishComponent(Long studyId, Long componentId,
			Boolean successful, String errorMsg) throws PublixException {
		Logger.info(CLASS_NAME + ".finishComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "logged-in user email "
				+ session(Users.SESSION_EMAIL) + ", " + "successful "
				+ successful + ", " + "errorMsg \"" + errorMsg + "\"");
		StudyModel study = publixUtils.retrieveStudy(studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		ComponentModel component = publixUtils.retrieveComponent(study,
				componentId);
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);
		publixUtils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		ComponentResult componentResult = publixUtils
				.retrieveCurrentComponentResult(studyResult);

		if (successful) {
			componentResult.setComponentState(ComponentState.FINISHED);
			componentResult.setErrorMsg(errorMsg);
		} else {
			componentResult.setComponentState(ComponentState.FAIL);
			componentResult.setErrorMsg(errorMsg);
		}
		componentResultDao.update(componentResult);
		return ok();
	}

	@Override
	public Result abortStudy(Long studyId, String message)
			throws PublixException {
		Logger.info(CLASS_NAME + ".abortStudy: studyId " + studyId + ", "
				+ "logged-in user email " + session(Users.SESSION_EMAIL) + ", "
				+ "message \"" + message + "\"");
		StudyModel study = publixUtils.retrieveStudy(studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		if (!publixUtils.studyDone(studyResult)) {
			publixUtils.abortStudy(message, studyResult);
		}

		publixUtils.discardIdCookie();
		if (ControllerUtils.isAjax()) {
			return ok();
		} else {
			return ok(views.html.publix.abort.render());
		}
	}

	@Override
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException {
		Logger.info(CLASS_NAME + ".finishStudy: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID) + ", " + "successful "
				+ successful + ", " + "errorMsg \"" + errorMsg + "\"");
		StudyModel study = publixUtils.retrieveStudy(studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		publixUtils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		if (!publixUtils.studyDone(studyResult)) {
			publixUtils.finishStudy(successful, errorMsg, studyResult);
		}

		publixUtils.discardIdCookie();
		if (ControllerUtils.isAjax()) {
			return ok();
		} else {
			if (!successful) {
				return ok(views.html.publix.error.render(errorMsg));
			} else {
				return ok(views.html.publix.finishedAndThanks.render());
			}
		}
	}

	@Override
	public Result logError(Long studyId, Long componentId) {
		String msg = request().body().asText();
		Logger.error(CLASS_NAME + " - logging component script error: studyId "
				+ studyId + ", " + "componentId " + componentId + ", "
				+ "error message \"" + msg + "\".");
		return ok();
	}

	/**
	 * Gets the value of to the given key in request's query string and trims
	 * whitespace.
	 */
	public static String getQueryString(String key) {
		String value = request().getQueryString(key);
		if (value != null) {
			value = value.trim();
		}
		return value;
	}

	/**
	 * Like an internal redirect or an proxy. The URL in the browser doesn't
	 * change.
	 */
	public Promise<Result> forwardTo(String url) {
		Promise<WS.Response> response = WS.url(url).get();
		return response.map(new Function<WS.Response, Result>() {
			public Result apply(WS.Response response) {
				// Prevent browser from caching pages - this would be an
				// security issue and additionally confuse the study flow
				response().setHeader("Cache-control", "no-cache, no-store");
				return ok(response.getBody()).as("text/html");
			}
		});
	}

}
