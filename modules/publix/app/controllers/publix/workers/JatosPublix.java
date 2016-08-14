package controllers.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.ForbiddenReloadException;
import exceptions.publix.PublixException;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.JatosWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Controller;
import play.mvc.Result;
import services.publix.PublixHelpers;
import services.publix.ResultCreator;
import services.publix.group.ChannelService;
import services.publix.group.GroupService;
import services.publix.idcookie.IdCookie;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.JatosErrorMessages;
import services.publix.workers.JatosPublixUtils;
import services.publix.workers.JatosStudyAuthorisation;
import utils.common.ControllerUtils;
import utils.common.JsonUtils;

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

	public static final String SESSION_EMAIL = "email";

	private static final ALogger LOGGER = Logger.of(JatosPublix.class);

	private final JatosPublixUtils publixUtils;
	private final JatosStudyAuthorisation studyAuthorisation;
	private final ResultCreator resultCreator;
	private final JatosErrorMessages errorMessages;

	@Inject
	JatosPublix(JPAApi jpa, JatosPublixUtils publixUtils,
			JatosStudyAuthorisation studyAuthorisation,
			ResultCreator resultCreator, GroupService groupService,
			ChannelService channelService, IdCookieService idCookieService,
			JatosErrorMessages errorMessages, StudyAssets studyAssets,
			JsonUtils jsonUtils, ComponentResultDao componentResultDao,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, groupService,
				channelService, idCookieService, errorMessages, studyAssets,
				jsonUtils, componentResultDao, studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.resultCreator = resultCreator;
		this.errorMessages = errorMessages;
	}

	@Override
	public Result startStudy(Long studyId, Long batchId)
			throws PublixException {
		LOGGER.info(".startStudy: studyId " + studyId + ", " + "batchId "
				+ batchId + ", " + "logged-in user's email "
				+ session(SESSION_EMAIL));
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
		JatosWorker worker = publixUtils.retrieveLoggedInUser().getWorker();
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);
		LOGGER.info(".startStudy: study (study ID " + studyId + ", batch ID "
				+ batchId + ") " + "assigned to worker with ID "
				+ worker.getId());

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
		publixUtils.finishAbandonedStudyResults();
		StudyResult studyResult = resultCreator.createStudyResult(study, batch,
				worker);
		idCookieService.writeIdCookie(worker, batch, studyResult);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, componentId, studyResult.getId()));
	}

	@Override
	public Result startComponent(Long studyId, Long componentId,
			Long studyResultId) throws PublixException {
		LOGGER.info(".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "studyResultId "
				+ studyResultId + ", " + "logged-in user's email "
				+ session(SESSION_EMAIL));
		IdCookie idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		JatosWorker worker = publixUtils
				.retrieveTypedWorker(idCookie.getWorkerId());
		Component component = publixUtils.retrieveComponent(study, componentId);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
		publixUtils.checkComponentBelongsToStudy(study, component);

		// Check if it's a single component show or a whole study show
		String jatosShow = publixUtils.retrieveJatosShowFromSession();
		StudyResult studyResult = publixUtils.retrieveWorkersStudyResult(worker,
				study, studyResultId);
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
				return redirect(controllers.publix.routes.PublixInterceptor
						.finishStudy(studyId, true, null, studyResult.getId()));
			}
			break;
		}

		ComponentResult componentResult = null;
		try {
			componentResult = publixUtils.startComponent(component,
					studyResult);
		} catch (ForbiddenReloadException e) {
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, e.getMessage(),
							studyResult.getId()));
		}
		idCookieService.writeIdCookie(worker, batch, studyResult,
				componentResult);
		return studyAssets.retrieveComponentHtmlFile(study.getDirName(),
				component.getHtmlFilePath());
	}

	@Override
	public Result startNextComponent(Long studyId, Long studyResultId)
			throws PublixException {
		LOGGER.info(".startNextComponent: studyId " + studyId + ", "
				+ "studyResultId " + studyResultId + ", "
				+ "logged-in user's email " + session(SESSION_EMAIL));
		IdCookie idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		JatosWorker worker = publixUtils
				.retrieveTypedWorker(idCookie.getWorkerId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		StudyResult studyResult = publixUtils.retrieveWorkersStudyResult(worker,
				study, studyResultId);

		// Check if it's a single component show or a whole study show
		String jatosShow = publixUtils.retrieveJatosShowFromSession();
		switch (jatosShow) {
		case RUN_STUDY:
			studyResult = publixUtils.retrieveWorkersStudyResult(worker, study,
					studyResultId);
			break;
		case RUN_COMPONENT_START:
			// Should never happen
			session(JatosPublix.JATOS_RUN, JatosPublix.RUN_COMPONENT_FINISHED);
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, null, studyResult.getId()));
		case RUN_COMPONENT_FINISHED:
			// It's already the second component (first is finished). Finish
			// study after first component.
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, true, null, studyResult.getId()));
		}

		Component nextComponent = publixUtils
				.retrieveNextActiveComponent(studyResult);
		if (nextComponent == null) {
			// Study has no more components -> finish it
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, true, null, studyResult.getId()));
		}
		return redirect(
				controllers.publix.routes.PublixInterceptor.startComponent(
						studyId, nextComponent.getId(), studyResult.getId()));
	}

	@Override
	public Result abortStudy(Long studyId, String message, Long studyResultId)
			throws PublixException {
		LOGGER.info(".abortStudy: studyId " + studyId + ", " + "studyResultId "
				+ studyResultId + ", " + "logged-in user email "
				+ session(SESSION_EMAIL) + ", " + "message \"" + message
				+ "\"");
		IdCookie idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		JatosWorker worker = publixUtils
				.retrieveTypedWorker(idCookie.getWorkerId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		StudyResult studyResult = publixUtils.retrieveWorkersStudyResult(worker,
				study, studyResultId);
		if (!PublixHelpers.studyDone(studyResult)) {
			publixUtils.abortStudy(message, studyResult);
			groupService.finishStudyResultInGroup(studyResult);
			Publix.session().remove(JatosPublix.JATOS_RUN);
		}
		idCookieService.discardIdCookie(studyResult.getId());
		if (ControllerUtils.isAjax()) {
			return ok();
		} else {
			if (message != null) {
				Controller.flash("info",
						errorMessages.studyFinishedWithMessage(message));
			}
			return redirect("/jatos/" + study.getId());
		}
	}

	@Override
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg,
			Long studyResultId) throws PublixException {
		LOGGER.info(".finishStudy: studyId " + studyId + ", " + "studyResultId "
				+ studyResultId + ", " + "logged-in user email "
				+ session(SESSION_EMAIL) + ", " + "successful " + successful
				+ ", " + "errorMsg \"" + errorMsg + "\"");
		IdCookie idCookie = idCookieService.getIdCookie(studyResultId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatch(idCookie.getBatchId());
		JatosWorker worker = publixUtils
				.retrieveTypedWorker(idCookie.getWorkerId());
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		StudyResult studyResult = publixUtils.retrieveWorkersStudyResult(worker,
				study, studyResultId);
		if (!PublixHelpers.studyDone(studyResult)) {
			publixUtils.finishStudyResult(successful, errorMsg, studyResult);
			groupService.finishStudyResultInGroup(studyResult);
			Publix.session().remove(JatosPublix.JATOS_RUN);
		}
		idCookieService.discardIdCookie(studyResult.getId());
		if (ControllerUtils.isAjax()) {
			return ok(errorMsg);
		} else {
			if (errorMsg != null) {
				Controller.flash("info",
						errorMessages.studyFinishedWithMessage(errorMsg));
			}
			return redirect("/jatos/" + study.getId());
		}
	}

}
