package controllers.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.GeneralSingleWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Result;
import services.publix.ResultCreator;
import services.publix.WorkerCreator;
import services.publix.group.GroupChannelService;
import services.publix.group.GroupAdministration;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.GeneralSingleCookieService;
import services.publix.workers.GeneralSingleErrorMessages;
import services.publix.workers.GeneralSinglePublixUtils;
import services.publix.workers.GeneralSingleStudyAuthorisation;
import session.batch.BatchChannelService;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

/**
 * Implementation of JATOS' public API for general single study runs (open to
 * everyone). A general single run is done by a GeneralSingleWorker.
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublix extends Publix<GeneralSingleWorker>
		implements IPublix {

	private static final ALogger LOGGER = Logger.of(GeneralSinglePublix.class);

	public static final String GENERALSINGLE = "generalSingle";

	private final GeneralSinglePublixUtils publixUtils;
	private final GeneralSingleStudyAuthorisation studyAuthorisation;
	private final ResultCreator resultCreator;
	private final WorkerCreator workerCreator;
	private final GeneralSingleCookieService generalSingleCookieService;

	@Inject
	GeneralSinglePublix(JPAApi jpa, GeneralSinglePublixUtils publixUtils,
			GeneralSingleStudyAuthorisation studyAuthorisation,
			ResultCreator resultCreator, WorkerCreator workerCreator,
			BatchChannelService batchChannelService,
			GroupAdministration groupAdministration,
			GroupChannelService groupChannelService,
			IdCookieService idCookieService,
			GeneralSingleCookieService generalSingleCookieService,
			GeneralSingleErrorMessages errorMessages, StudyAssets studyAssets,
			JsonUtils jsonUtils, ComponentResultDao componentResultDao,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, batchChannelService,
				groupAdministration, groupChannelService, idCookieService,
				errorMessages, studyAssets, jsonUtils, componentResultDao,
				studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.resultCreator = resultCreator;
		this.workerCreator = workerCreator;
		this.generalSingleCookieService = generalSingleCookieService;
	}

	/**
	 * {@inheritDoc}<br>
	 * <br>
	 * 
	 * Only a general single run or a personal single run has the special
	 * StudyState PRE. Only with the corresponding workers (GeneralSingleWorker
	 * and PersonalSingleWorker) it's possible to have a preview of the study.
	 * To get into the preview mode one has to add 'pre' to the URL query
	 * string. In the preview mode a worker can start the study (with 'pre') and
	 * start the first component as often as he wants. The study result switches
	 * into 'STARTED' and back to normal behavior by starting the study without
	 * the 'pre' in the query string or by going on and start a component
	 * different then the first.
	 */
	@Override
	public Result startStudy(Long studyId, Long batchId)
			throws PublixException {
		boolean pre = HttpUtils.getQueryString("pre") != null;
		LOGGER.info(".startStudy: studyId " + studyId + ", " + "batchId "
				+ batchId + ", " + "pre " + pre);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
		Long workerId = generalSingleCookieService.retrieveWorkerByStudy(study);

		StudyResult studyResult;
		if (workerId != null) {
			studyResult = publixUtils.retrievePreStudyResult(workerId);
		} else {
			GeneralSingleWorker worker = workerCreator
					.createAndPersistGeneralSingleWorker(batch);
			studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study,
					batch);
			LOGGER.info(
					".startStudy: study (study ID " + studyId + ", batch ID "
							+ batchId + ") " + "assigned to worker with ID "
							+ worker.getId() + ", " + "pre " + pre);

			publixUtils.finishAbandonedStudyResults();
			studyResult = resultCreator.createStudyResult(study, batch, worker);
			idCookieService.writeIdCookie(worker, batch, studyResult);
			generalSingleCookieService.set(study, worker);
		}
		publixUtils.setPreStudyStateByPre(pre, studyResult);

		Component firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(
				controllers.publix.routes.PublixInterceptor.startComponent(
						studyId, firstComponent.getId(), studyResult.getId()));
	}

}
