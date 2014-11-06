package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.results.ComponentResult;
import models.results.ComponentResult.ComponentState;
import models.results.StudyResult;
import models.results.StudyResult.StudyState;
import models.workers.Worker;
import play.Logger;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Result;
import services.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import controllers.Users;
import exceptions.ForbiddenReloadException;
import exceptions.PublixException;

/**
 * Abstract controller class for all controller that implement the IPublix
 * interface. It defines common methods and constants.
 * 
 * @author Kristian Lange
 */
public abstract class Publix<T extends Worker> extends Controller implements
		IPublix {

	// ID cookie name and value names
	public static final String ID_COOKIE_NAME = "MechArg_IDs";
	public static final String WORKER_ID = "workerId";
	public static final String STUDY_ID = "studyId";
	public static final String COMPONENT_ID = "componentId";
	public static final String STUDY_RESULT_ID = "studyResultId";
	public static final String COMPONENT_RESULT_ID = "componentResultId";
	public static final String POSITION = "position";

	private static final String CLASS_NAME = Publix.class.getSimpleName();

	protected PublixUtils<T> utils;

	public Publix(PublixUtils<T> utils) {
		this.utils = utils;
	}

	@Override
	public Result getStudyData(Long studyId) throws PublixException,
			JsonProcessingException {
		Logger.info(CLASS_NAME + ".getStudyData: studyId " + studyId);
		T worker = utils.retrieveWorker();
		StudyModel study = utils.retrieveStudy(studyId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		studyResult.merge();
		return ok(JsonUtils.asJsonForPublix(study));
	}

	@Override
	public Result getComponentData(Long studyId, Long componentId)
			throws PublixException, JsonProcessingException {
		Logger.info(CLASS_NAME + ".getComponentData: studyId " + studyId + ", "
				+ "componentId " + componentId);
		T worker = utils.retrieveWorker();
		StudyModel study = utils.retrieveStudy(studyId);
		ComponentModel component = utils.retrieveComponent(study, componentId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		utils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		ComponentState maxAllowedComponentState = ComponentState.STARTED;
		ComponentResult componentResult;
		try {
			componentResult = utils.retrieveStartedComponentResult(component,
					studyResult, maxAllowedComponentState);
		} catch (ForbiddenReloadException e) {
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, e.getMessage()));
		}

		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		componentResult.merge();
		return ok(JsonUtils.asJsonForPublix(component));
	}

	@Override
	public Result submitResultData(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".submitResultData: studyId " + studyId + ", "
				+ "componentId " + componentId);
		StudyModel study = utils.retrieveStudy(studyId);
		T worker = utils.retrieveWorker();
		ComponentModel component = utils.retrieveComponent(study, componentId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		utils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		ComponentState maxAllowedComponentState = ComponentState.DATA_RETRIEVED;
		ComponentResult componentResult;
		try {
			componentResult = utils.retrieveStartedComponentResult(component,
					studyResult, maxAllowedComponentState);
		} catch (ForbiddenReloadException e) {
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, e.getMessage()));
		}

		String data = utils.getDataFromRequestBody(request().body(), component);
		componentResult.setData(data);
		componentResult.setComponentState(ComponentState.RESULTDATA_POSTED);
		componentResult.merge();
		return ok();
	}
	
	@Override
	public Result finishComponent(Long studyId, Long componentId,
			Boolean successful, String errorMsg) throws PublixException {
		Logger.info(CLASS_NAME + ".finishComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "logged-in user email "
				+ session(Users.COOKIE_EMAIL) + ", " + "successful "
				+ successful + ", " + "errorMsg \"" + errorMsg + "\"");
		StudyModel study = utils.retrieveStudy(studyId);
		T worker = utils.retrieveWorker();
		ComponentModel component = utils.retrieveComponent(study, componentId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		utils.checkComponentBelongsToStudy(study, component);
		
		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		ComponentResult componentResult = utils.retrieveCurrentComponentResult(
					studyResult);
		
		if (successful) {
			componentResult.setComponentState(ComponentState.FINISHED);
			componentResult.setErrorMsg(errorMsg);
		} else {
			componentResult.setComponentState(ComponentState.FAIL);
			componentResult.setErrorMsg(errorMsg);
		}
		componentResult.merge();
		return ok();
	}

	@Override
	public Promise<Result> startComponentByPosition(Long studyId,
			Integer position) throws PublixException {
		Logger.info(CLASS_NAME + ".startComponentByPosition: studyId "
				+ studyId + ", " + "position " + position + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		ComponentModel component = utils.retrieveComponentByPosition(studyId,
				position);
		return startComponent(studyId, component.getId());
	}

	@Override
	public Result logError(Long studyId, Long componentId) {
		String msg = request().body().asText();
		Logger.error(CLASS_NAME + " - logging component script error: studyId "
				+ studyId + ", " + "componentId " + componentId + ", "
				+ "error message \"" + msg + "\".");
		return ok();
	}

	@Override
	public Result teapot() {
		return status(418, "I'm a teapot");
	}

	/**
	 * Like an internal redirect or an proxy. The URL in the browser doesn't
	 * change.
	 */
	public static Promise<Result> forwardTo(String url) {
		Promise<WS.Response> response = WS.url(url).get();
		return response.map(new Function<WS.Response, Result>() {
			public Result apply(WS.Response response) {
				return ok(response.getBody()).as("text/html");
			}
		});
	}

}
