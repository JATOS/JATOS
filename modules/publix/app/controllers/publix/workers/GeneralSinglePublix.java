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
import services.publix.group.ChannelService;
import services.publix.group.GroupService;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.GeneralSingleErrorMessages;
import services.publix.workers.GeneralSinglePublixUtils;
import services.publix.workers.GeneralSingleStudyAuthorisation;
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

	/**
	 * Cookie name where all study's UUIDs are stored.
	 */
	public static final String COOKIE = "JATOS_GENERALSINGLE_UUIDS";

	public static final String GENERALSINGLE = "generalSingle";

	private final GeneralSinglePublixUtils publixUtils;
	private final GeneralSingleStudyAuthorisation studyAuthorisation;
	private final ResultCreator resultCreator;
	private final WorkerCreator workerCreator;

	@Inject
	GeneralSinglePublix(JPAApi jpa, GeneralSinglePublixUtils publixUtils,
			GeneralSingleStudyAuthorisation studyAuthorisation,
			ResultCreator resultCreator, WorkerCreator workerCreator,
			GroupService groupService, ChannelService channelService,
			IdCookieService idCookieService,
			GeneralSingleErrorMessages errorMessages, StudyAssets studyAssets,
			JsonUtils jsonUtils, ComponentResultDao componentResultDao,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, groupService,
				channelService, idCookieService, errorMessages, studyAssets,
				jsonUtils, componentResultDao, studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.resultCreator = resultCreator;
		this.workerCreator = workerCreator;
	}

	@Override
	public Result startStudy(Long studyId, Long batchId)
			throws PublixException {
		LOGGER.info(".startStudy: studyId " + studyId + ", " + "batchId "
				+ batchId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
		publixUtils.checkStudyInGeneralSingleCookie(study,
				request().cookie(GeneralSinglePublix.COOKIE));

		GeneralSingleWorker worker = workerCreator
				.createAndPersistGeneralSingleWorker(batch);
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);
		LOGGER.info(".startStudy: study (study ID " + studyId + ", batch ID "
				+ batchId + ") " + "assigned to worker with ID "
				+ worker.getId());

		groupService.finishStudyInAllPriorGroups(worker, study);
		publixUtils.finishAbandonedStudyResults();
		StudyResult studyResult = resultCreator.createStudyResult(study, batch,
				worker);
		idCookieService.writeIdCookie(worker, batch, studyResult);

		publixUtils.setGeneralSingleCookie(study);

		Component firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(
				controllers.publix.routes.PublixInterceptor.startComponent(
						studyId, firstComponent.getId(), studyResult.getId()));
	}

}
