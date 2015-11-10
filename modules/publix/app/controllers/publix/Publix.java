package controllers.publix;

import groupservices.publix.ChannelService;
import groupservices.publix.GroupService;
import groupservices.publix.WebSocketBuilder;

import java.io.IOException;

import javax.inject.Singleton;

import models.common.Component;
import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.Group;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.Worker;
import play.Logger;
import play.db.jpa.JPA;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import services.publix.IStudyAuthorisation;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import utils.common.ControllerUtils;
import utils.common.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.ForbiddenReloadException;
import exceptions.publix.InternalServerErrorPublixException;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;

/**
 * Abstract controller class for all controllers that implement the IPublix
 * interface. It defines common methods and constants.
 * 
 * @author Kristian Lange
 */
@Singleton
public abstract class Publix<T extends Worker> extends Controller implements
		IPublix {

	/**
	 * ID cookie name and value names. The ID cookie is set by JATOS to let the
	 * study running in the browser know about the current IDs. The ID cookie is
	 * set during component start.
	 */
	public static final String ID_COOKIE_NAME = "JATOS_IDS";
	public static final String WORKER_ID = "workerId";
	public static final String GROUP_ID = "groupId";
	public static final String GROUP_RESULT_ID = "groupResultId";
	public static final String STUDY_ID = "studyId";
	public static final String STUDY_RESULT_ID = "studyResultId";
	public static final String COMPONENT_ID = "componentId";
	public static final String COMPONENT_RESULT_ID = "componentResultId";
	public static final String COMPONENT_POSITION = "componentPos";

	private static final String CLASS_NAME = Publix.class.getSimpleName();

	protected final PublixUtils<T> publixUtils;
	protected final IStudyAuthorisation<T> studyAuthorisation;
	protected final GroupService groupService;
	protected final ChannelService channelService;
	protected final PublixErrorMessages errorMessages;
	protected final StudyAssets studyAssets;
	protected final JsonUtils jsonUtils;
	protected final ComponentResultDao componentResultDao;
	protected final StudyResultDao studyResultDao;
	protected final GroupResultDao groupResultDao;

	public Publix(PublixUtils<T> utils,
			IStudyAuthorisation<T> studyAuthorisation,
			GroupService groupService, ChannelService channelService,
			PublixErrorMessages errorMessages, StudyAssets studyAssets,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		this.publixUtils = utils;
		this.studyAuthorisation = studyAuthorisation;
		this.groupService = groupService;
		this.channelService = channelService;
		this.errorMessages = errorMessages;
		this.studyAssets = studyAssets;
		this.componentResultDao = componentResultDao;
		this.jsonUtils = jsonUtils;
		this.studyResultDao = studyResultDao;
		this.groupResultDao = groupResultDao;
	}

	@Override
	public Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		Study study = publixUtils.retrieveStudy(studyId);
		Component component = publixUtils.retrieveComponent(study,
				componentId);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		ComponentResult componentResult = null;
		try {
			componentResult = publixUtils
					.startComponent(component, studyResult);
		} catch (ForbiddenReloadException e) {
			return Promise
					.pure(redirect(controllers.publix.routes.PublixInterceptor
							.finishStudy(studyId, false, e.getMessage())));
		}
		GroupResult groupResult = studyResult.getGroupResult();
		String cookieValue = publixUtils.generateIdCookieValue(studyResult,
				componentResult, worker, groupResult);
		response().setCookie(Publix.ID_COOKIE_NAME, cookieValue);
		String urlPath = StudyAssets.getComponentUrlPath(study.getDirName(),
				component);
		String urlWithQueryStr = StudyAssets.getUrlWithQueryString(request()
				.uri(), request().host(), urlPath);
		return studyAssets.forwardTo(urlWithQueryStr);
	}

	@Override
	public Promise<Result> startComponentByPosition(Long studyId,
			Integer position) throws PublixException {
		Logger.info(CLASS_NAME + ".startComponentByPosition: studyId "
				+ studyId + ", " + "position " + position + ", " + ", "
				+ "workerId " + session(WORKER_ID));
		Component component = publixUtils.retrieveComponentByPosition(
				studyId, position);
		return startComponent(studyId, component.getId());
	}

	@Override
	public Result startNextComponent(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "workerId " + session(WORKER_ID));
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		Study study = publixUtils.retrieveStudy(studyId);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);

		Component nextComponent = publixUtils
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
	public Result getInitData(Long studyId, Long componentId)
			throws PublixException, IOException {
		Logger.info(CLASS_NAME + ".getInitData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		Study study = publixUtils.retrieveStudy(studyId);
		Component component = publixUtils.retrieveComponent(study,
				componentId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
		publixUtils.checkComponentBelongsToStudy(study, component);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		ComponentResult componentResult;
		try {
			componentResult = publixUtils.retrieveStartedComponentResult(
					component, studyResult);
		} catch (ForbiddenReloadException e) {
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, e.getMessage()));
		}
		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		studyResultDao.update(studyResult);
		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		componentResultDao.update(componentResult);

		return ok(jsonUtils.initData(studyResult, study, component));
	}

	@Override
	public WebSocket<JsonNode> joinGroup(Long studyId)
			throws NotFoundPublixException, ForbiddenPublixException {
		Logger.info(CLASS_NAME + ".joinGroup: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID));
		String workerIdStr = session(Publix.WORKER_ID);
		// The @Transactional annotation can only be used with Actions.
		// Since WebSockets aren't considered Actions in Play we have to do
		// it manually. Additionally we have to catch the PublixExceptions
		// manually because the PublixAction wouldn't send a rejected WebSocket
		// but normal HTTP responses.
		try {
			return JPA.withTransaction(() -> joinGroup(studyId, workerIdStr));
		} catch (NotFoundPublixException e) {
			Logger.info(CLASS_NAME + ".openGroupChannel: " + e.getMessage());
			return WebSocketBuilder.reject(notFound());
		} catch (ForbiddenPublixException e) {
			Logger.info(CLASS_NAME + ".openGroupChannel: " + e.getMessage());
			return WebSocketBuilder.reject(forbidden());
		} catch (Throwable e) {
			Logger.info(CLASS_NAME + ".openGroupChannel: ", e);
			return WebSocketBuilder.reject(internalServerError());
		}
	}

	private WebSocket<JsonNode> joinGroup(Long studyId, String workerIdStr)
			throws ForbiddenPublixException, NotFoundPublixException,
			InternalServerErrorPublixException {
		T worker = publixUtils.retrieveTypedWorker(workerIdStr);
		Study study = publixUtils.retrieveStudy(studyId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		GroupResult groupResult;
		if (groupService.hasUnfinishedGroupResult(studyResult)) {
			groupResult = studyResult.getGroupResult();
			Logger.info(CLASS_NAME + ".joinGroup: studyId " + studyId + ", "
					+ "workerId " + workerIdStr
					+ " already member of group result " + groupResult.getId());
		} else {
			// TODO 
			Group group = study.getGroupList().get(0);
			groupResult = groupService.joinGroup(studyResult, group);
			channelService.sendJoinedMsg(studyResult);
			Logger.info(CLASS_NAME + ".joinGroup: studyId " + studyId + ", "
					+ "workerId " + workerIdStr + " joined group result "
					+ groupResult.getId());
		}
		groupService.checkGroupAllowsMessaging(groupResult.getGroup());
		return channelService.openGroupChannel(studyResult);
	}

	@Override
	public Result leaveGroup(Long studyId) throws ForbiddenPublixException,
			InternalServerErrorPublixException, NotFoundPublixException {
		Logger.info(CLASS_NAME + ".leaveGroup: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID));
		T worker = publixUtils.retrieveTypedWorker(session(Publix.WORKER_ID));
		Study study = publixUtils.retrieveStudy(studyId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		GroupResult groupResult = studyResult.getGroupResult();
		if (groupResult == null) {
			Logger.info(CLASS_NAME + ".leaveGroup: studyId " + studyId + ", "
					+ "workerId " + session(WORKER_ID)
					+ " isn't member of a group result - can't leave.");
			return ok().as("text/html");
		}
		groupService.leaveGroupResult(studyResult);
		channelService.closeGroupChannel(studyResult, groupResult);
		channelService.sendLeftMsg(studyResult, groupResult);
		Logger.info(CLASS_NAME + ".leaveGroup: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID) + " left group result "
				+ groupResult.getId());
		return ok().as("text/html");
	}

	@Override
	public Result setStudySessionData(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".setStudySessionData: studyId " + studyId
				+ ", " + "workerId " + session(WORKER_ID));
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		Study study = publixUtils.retrieveStudy(studyId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		String studySessionData = publixUtils.getDataFromRequestBody(request()
				.body());
		studyResult.setStudySessionData(studySessionData);
		studyResultDao.update(studyResult);
		return ok().as("text/html");
	}

	@Override
	public Result submitResultData(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".submitResultData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));
		Study study = publixUtils.retrieveStudy(studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		Component component = publixUtils.retrieveComponent(study,
				componentId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
		publixUtils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		ComponentResult componentResult = publixUtils
				.retrieveCurrentComponentResult(studyResult);
		if (componentResult == null) {
			String error = errorMessages.componentNeverStarted(studyId,
					componentId, "submitResultData");
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, error));
		}

		String resultData = publixUtils
				.getDataFromRequestBody(request().body());
		componentResult.setData(resultData);
		componentResult.setComponentState(ComponentState.RESULTDATA_POSTED);
		componentResultDao.update(componentResult);
		return ok().as("text/html");
	}

	@Override
	public Result finishComponent(Long studyId, Long componentId,
			Boolean successful, String errorMsg) throws PublixException {
		Logger.info(CLASS_NAME + ".finishComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID) + ", " + "successful " + successful + ", "
				+ "errorMsg \"" + errorMsg + "\"");
		Study study = publixUtils.retrieveStudy(studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		Component component = publixUtils.retrieveComponent(study,
				componentId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
		publixUtils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		ComponentResult componentResult = publixUtils
				.retrieveCurrentComponentResult(studyResult);
		if (componentResult == null) {
			String error = errorMessages.componentNeverStarted(studyId,
					componentId, "submitResultData");
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, error));
		}

		if (successful) {
			componentResult.setComponentState(ComponentState.FINISHED);
			componentResult.setErrorMsg(errorMsg);
		} else {
			componentResult.setComponentState(ComponentState.FAIL);
			componentResult.setErrorMsg(errorMsg);
		}
		componentResultDao.update(componentResult);
		return ok().as("text/html");
	}

	@Override
	public Result abortStudy(Long studyId, String message)
			throws PublixException {
		Logger.info(CLASS_NAME + ".abortStudy: studyId " + studyId + ", "
				+ ", " + "workerId " + session(WORKER_ID) + ", " + "message \""
				+ message + "\"");
		Study study = publixUtils.retrieveStudy(studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		if (!publixUtils.studyDone(studyResult)) {
			publixUtils.abortStudy(message, studyResult);
		}
		GroupResult groupResult = studyResult.getGroupResult();
		groupService.leaveGroupResult(studyResult);
		channelService.closeGroupChannel(studyResult, groupResult);
		channelService.sendLeftMsg(studyResult, groupResult);
		Publix.response().discardCookie(Publix.ID_COOKIE_NAME);
		if (ControllerUtils.isAjax()) {
			return ok().as("text/html");
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
		Study study = publixUtils.retrieveStudy(studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = publixUtils.retrieveWorkersLastStudyResult(
				worker, study);
		if (!publixUtils.studyDone(studyResult)) {
			publixUtils.finishStudyResult(successful, errorMsg, studyResult);
		}
		GroupResult groupResult = studyResult.getGroupResult();
		groupService.leaveGroupResult(studyResult);
		channelService.closeGroupChannel(studyResult, groupResult);
		channelService.sendLeftMsg(studyResult, groupResult);
		Publix.response().discardCookie(Publix.ID_COOKIE_NAME);
		if (ControllerUtils.isAjax()) {
			return ok().as("text/html");
		} else {
			if (!successful) {
				return ok(views.html.publix.error.render(errorMsg));
			} else {
				return ok(views.html.publix.finishedAndThanks.render());
			}
		}
	}

	@Override
	public Result log(Long studyId, Long componentId) throws PublixException {
		Study study = publixUtils.retrieveStudy(studyId);
		T worker = publixUtils.retrieveTypedWorker(session(WORKER_ID));
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
		String msg = request().body().asText();
		Logger.info(CLASS_NAME + " - logging from client: study ID " + studyId
				+ ", component ID " + componentId + ", worker ID "
				+ worker.getId() + ", message \"" + msg + "\".");
		return ok().as("text/html");
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

}
