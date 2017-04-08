package controllers.publix;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;

import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.ForbiddenReloadException;
import exceptions.publix.InternalServerErrorPublixException;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Controller;
import play.mvc.LegacyWebSocket;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixHelpers;
import services.publix.PublixUtils;
import services.publix.StudyAuthorisation;
import services.publix.group.ChannelService;
import services.publix.group.GroupAdministration;
import services.publix.group.WebSocketBuilder;
import services.publix.idcookie.IdCookieModel;
import services.publix.idcookie.IdCookieService;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

/**
 * Abstract controller class for all controllers that implement the IPublix
 * interface. It defines common methods and constants.
 * 
 * @author Kristian Lange
 */
@Singleton
public abstract class Publix<T extends Worker> extends Controller
		implements IPublix {

	private static final ALogger LOGGER = Logger.of(Publix.class);

	protected final JPAApi jpa;
	protected final PublixUtils<T> publixUtils;
	protected final StudyAuthorisation<T> studyAuthorisation;
	protected final GroupAdministration groupAdministration;
	protected final ChannelService channelService;
	protected final IdCookieService idCookieService;
	protected final PublixErrorMessages errorMessages;
	protected final StudyAssets studyAssets;
	protected final JsonUtils jsonUtils;
	protected final ComponentResultDao componentResultDao;
	protected final StudyResultDao studyResultDao;
	protected final GroupResultDao groupResultDao;

	public Publix(JPAApi jpa, PublixUtils<T> publixUtils,
			StudyAuthorisation<T> studyAuthorisation,
			GroupAdministration groupAdministration,
			ChannelService channelService, IdCookieService idCookieService,
			PublixErrorMessages errorMessages, StudyAssets studyAssets,
			JsonUtils jsonUtils, ComponentResultDao componentResultDao,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		this.jpa = jpa;
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.groupAdministration = groupAdministration;
		this.channelService = channelService;
		this.idCookieService = idCookieService;
		this.errorMessages = errorMessages;
		this.studyAssets = studyAssets;
		this.jsonUtils = jsonUtils;
		this.componentResultDao = componentResultDao;
		this.studyResultDao = studyResultDao;
		this.groupResultDao = groupResultDao;
	}

	@Override
	public Result startComponent(Long studyId, Long componentId,
			Long studyResultId) throws PublixException {
		LOGGER.info(".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "studyResultId "
				+ studyResultId);
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		Component component = publixUtils.retrieveComponent(study, componentId);
		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		publixUtils.setPreStudyStateByComponentId(studyResult, study,
				componentId);

		ComponentResult componentResult = null;
		try {
			componentResult = publixUtils.startComponent(component,
					studyResult);
		} catch (ForbiddenReloadException e) {
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, studyResult.getId(), false,
							e.getMessage()));
		}

		idCookieService.writeIdCookie(worker, batch, studyResult,
				componentResult);
		return studyAssets.retrieveComponentHtmlFile(study.getDirName(),
				component.getHtmlFilePath());
	}

	@Override
	public Result startComponentByPosition(Long studyId, Integer position,
			Long studyResultId) throws PublixException {
		LOGGER.info(".startComponentByPosition: studyId " + studyId + ", "
				+ "position " + position + ", " + ", " + "studyResultId "
				+ studyResultId);
		Component component = publixUtils.retrieveComponentByPosition(studyId,
				position);
		return startComponent(studyId, component.getId(), studyResultId);
	}

	@Override
	public Result startNextComponent(Long studyId, Long studyResultId)
			throws PublixException {
		LOGGER.info(".startNextComponent: studyId " + studyId + ", "
				+ "studyResultId " + studyResultId);
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		Study study = publixUtils.retrieveStudy(studyId);
		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);

		Component nextComponent = publixUtils
				.retrieveNextActiveComponent(studyResult);
		if (nextComponent == null) {
			// Study has no more components -> finish it
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, studyResult.getId(), true, null));
		}
		return redirect(
				controllers.publix.routes.PublixInterceptor.startComponent(
						studyId, nextComponent.getId(), studyResult.getId()));
	}

	@Override
	public Result getInitData(Long studyId, Long componentId,
			Long studyResultId) throws PublixException, IOException {
		LOGGER.info(".getInitData: studyId " + studyId + ", " + "componentId "
				+ componentId + ", " + "studyResultId " + studyResultId);
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		Component component = publixUtils.retrieveComponent(study, componentId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
		publixUtils.checkComponentBelongsToStudy(study, component);
		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		ComponentResult componentResult;
		try {
			componentResult = publixUtils
					.retrieveStartedComponentResult(component, studyResult);
		} catch (ForbiddenReloadException e) {
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, studyResult.getId(), false,
							e.getMessage()));
		}
		if (studyResult.getStudyState() != StudyState.PRE) {
			studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		}
		studyResultDao.update(studyResult);
		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		componentResultDao.update(componentResult);

		return ok(jsonUtils.initData(batch, studyResult, study, component));
	}

	@Override
	// Due to returning a WebSocket and not a Result we don't throw exceptions
	public LegacyWebSocket<JsonNode> joinGroup(Long studyId,
			Long studyResultId) {
		LOGGER.info(".joinGroup: studyId " + studyId + ", " + "studyResultId "
				+ studyResultId);
		// The @Transactional annotation can only be used with Actions.
		// Since WebSockets aren't considered Actions in Play we have to do
		// it manually. Additionally we have to catch the PublixExceptions
		// manually because the PublixAction wouldn't send a rejected WebSocket
		// but normal HTTP responses.
		StudyResult studyResult = jpa.withTransaction(() -> {
			try {
				return joinGroupTransactional(studyId, studyResultId);
			} catch (NotFoundPublixException e) {
				LOGGER.info(".joinGroup: " + e.getMessage());
				// return WebSocketBuilder.reject(notFound());
				return null;
			} catch (ForbiddenPublixException e) {
				LOGGER.info(".joinGroup: " + e.getMessage());
				// return WebSocketBuilder.reject(forbidden());
				return null;
			} catch (Throwable e) {
				LOGGER.error(".joinGroup: ", e);
				// return WebSocketBuilder.reject(internalServerError());
				return null;
			}
		});
		// openGroupChannel has to be outside of the transaction
		if (studyResult != null) {
			try {
				return channelService.openGroupChannel(studyResult);
			} catch (InternalServerErrorPublixException e) {
				return WebSocketBuilder.reject(internalServerError());
			}
		} else {
			return WebSocketBuilder.reject(internalServerError());
		}
	}

	private StudyResult joinGroupTransactional(Long studyId, Long studyResultId)
			throws PublixException {
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
		publixUtils.checkStudyIsGroupStudy(study);
		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		groupAdministration.checkHistoryGroupResult(studyResult);

		if (studyResult.getActiveGroupResult() != null) {
			GroupResult groupResult = studyResult.getActiveGroupResult();
			LOGGER.info(".joinGroup: studyId " + studyId + ", " + "workerId "
					+ idCookie.getWorkerId()
					+ " already member of group result " + groupResult.getId());
		} else {
			GroupResult groupResult = groupAdministration.join(studyResult,
					batch);
			channelService.sendJoinedMsg(studyResult);
			LOGGER.info(".joinGroup: studyId " + studyId + ", " + "workerId "
					+ idCookie.getWorkerId() + " joined group result "
					+ groupResult.getId());
		}
		return studyResult;
	}

	@Override
	public Result reassignGroup(Long studyId, Long studyResultId)
			throws PublixException {
		LOGGER.info(".reassignGroup: studyId " + studyId + ", "
				+ "studyResultId " + studyResultId);
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
		publixUtils.checkStudyIsGroupStudy(study);
		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		groupAdministration.checkHistoryGroupResult(studyResult);

		GroupResult currentGroupResult = studyResult.getActiveGroupResult();
		GroupResult differentGroupResult = groupAdministration
				.reassign(studyResult, batch);
		channelService.reassignGroupChannel(studyResult, currentGroupResult,
				differentGroupResult);
		LOGGER.info(".reassignGroup: studyId " + studyId + ", " + "workerId "
				+ idCookie.getWorkerId() + " reassigned to group result "
				+ differentGroupResult.getId());
		return ok();
	}

	@Override
	public Result leaveGroup(Long studyId, Long studyResultId)
			throws PublixException {
		LOGGER.info(".leaveGroup: studyId " + studyId + ", " + "studyResultId "
				+ studyResultId);
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		publixUtils.checkStudyIsGroupStudy(study);
		GroupResult groupResult = studyResult.getActiveGroupResult();
		if (groupResult == null) {
			LOGGER.info(".leaveGroup: studyId " + studyId + ", " + "workerId "
					+ idCookie.getWorkerId()
					+ " isn't member of a group result - can't leave.");
			return ok();
		}
		groupAdministration.leave(studyResult);
		channelService.closeGroupChannel(studyResult, groupResult);
		channelService.sendLeftMsg(studyResult, groupResult);
		LOGGER.info(".leaveGroup: studyId " + studyId + ", " + "workerId "
				+ idCookie.getWorkerId() + " left group result "
				+ groupResult.getId());
		return ok();
	}

	@Override
	public Result setStudySessionData(Long studyId, Long studyResultId)
			throws PublixException {
		LOGGER.info(".setStudySessionData: studyId " + studyId + ", "
				+ "studyResultId " + studyResultId);
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		String studySessionData = request().body().asText();
		studyResult.setStudySessionData(studySessionData);
		studyResultDao.update(studyResult);
		return ok();
	}

	@Override
	public Result heartbeat(Long studyId, Long studyResultId)
			throws PublixException {
		LOGGER.debug(".heartbeat: studyId " + studyId + ", " + "studyResultId "
				+ studyResultId);
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		studyResult.setLastSeenDate(new Timestamp(new Date().getTime()));
		studyResultDao.update(studyResult);
		return ok();
	}

	@Override
	public Result submitResultData(Long studyId, Long componentId,
			Long studyResultId) throws PublixException {
		LOGGER.info(".submitResultData: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "studyResultId "
				+ studyResultId);
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		Component component = publixUtils.retrieveComponent(study, componentId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
		publixUtils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		ComponentResult componentResult = publixUtils
				.retrieveCurrentComponentResult(studyResult);
		if (componentResult == null) {
			String error = PublixErrorMessages.componentNeverStarted(studyId,
					componentId, "submitResultData");
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, studyResult.getId(), false, error));
		}

		String resultData = request().body().asText();
		componentResult.setData(resultData);
		componentResult.setComponentState(ComponentState.RESULTDATA_POSTED);
		componentResultDao.update(componentResult);
		return ok();
	}

	@Override
	public Result finishComponent(Long studyId, Long componentId,
			Long studyResultId, Boolean successful, String errorMsg)
			throws PublixException {
		LOGGER.info(".finishComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "studyResultId "
				+ studyResultId + ", " + "successful " + successful + ", "
				+ "errorMsg \"" + errorMsg + "\"");
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		Component component = publixUtils.retrieveComponent(study, componentId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
		publixUtils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		ComponentResult componentResult = publixUtils
				.retrieveCurrentComponentResult(studyResult);
		if (componentResult == null) {
			String error = PublixErrorMessages.componentNeverStarted(studyId,
					componentId, "submitResultData");
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, studyResult.getId(), false, error));
		}

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
	public Result abortStudy(Long studyId, Long studyResultId, String message)
			throws PublixException {
		LOGGER.info(".abortStudy: studyId " + studyId + ", " + ", "
				+ "studyResultId " + studyResultId + ", " + "message \""
				+ message + "\"");
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		if (!PublixHelpers.studyDone(studyResult)) {
			publixUtils.abortStudy(message, studyResult);
			groupAdministration.finishStudyResultInGroup(studyResult);
		}
		idCookieService.discardIdCookie(studyResult.getId());
		if (HttpUtils.isAjax()) {
			return ok();
		} else {
			return ok(views.html.publix.abort.render());
		}
	}

	@Override
	public Result finishStudy(Long studyId, Long studyResultId,
			Boolean successful, String errorMsg) throws PublixException {
		LOGGER.info(".finishStudy: studyId " + studyId + ", " + "studyResultId "
				+ studyResultId + ", " + "successful " + successful + ", "
				+ "errorMsg \"" + errorMsg + "\"");
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		StudyResult studyResult = publixUtils.retrieveStudyResult(worker, study,
				studyResultId);
		if (!PublixHelpers.studyDone(studyResult)) {
			publixUtils.finishStudyResult(successful, errorMsg, studyResult);
			groupAdministration.finishStudyResultInGroup(studyResult);
		}
		idCookieService.discardIdCookie(studyResult.getId());
		if (HttpUtils.isAjax()) {
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
	public Result log(Long studyId, Long componentId, Long studyResultId)
			throws PublixException {
		IdCookieModel idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		T worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
		String msg = request().body().asText();
		LOGGER.info("logging from client: study ID " + studyId
				+ ", component ID " + componentId + ", worker ID "
				+ worker.getId() + ", study result ID " + studyResultId
				+ ", message \"" + msg + "\".");
		return ok();
	}

}
