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
import models.common.workers.GeneralSingleWorker;
import play.Logger;
import play.db.jpa.JPAApi;
import play.mvc.Http.Cookie;
import play.mvc.Result;
import services.publix.ChannelService;
import services.publix.GroupService;
import services.publix.ResultCreator;
import services.publix.WorkerCreator;
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

	/**
	 * Cookie name where all study's UUIDs are stored.
	 */
	public static final String COOKIE = "JATOS_GENERALSINGLE_UUIDS";

	public static final String GENERALSINGLE = "generalSingle";

	private static final String CLASS_NAME = GeneralSinglePublix.class
			.getSimpleName();

	private final GeneralSinglePublixUtils publixUtils;
	private final GeneralSingleStudyAuthorisation studyAuthorisation;
	private final ResultCreator resultCreator;
	private final WorkerCreator workerCreator;

	@Inject
	GeneralSinglePublix(JPAApi jpa, GeneralSinglePublixUtils publixUtils,
			GeneralSingleStudyAuthorisation studyAuthorisation,
			ResultCreator resultCreator, WorkerCreator workerCreator,
			GroupService groupService, ChannelService channelService,
			GeneralSingleErrorMessages errorMessages, StudyAssets studyAssets,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, groupService,
				channelService, errorMessages, studyAssets, componentResultDao,
				jsonUtils, studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.resultCreator = resultCreator;
		this.workerCreator = workerCreator;
	}

	@Override
	public Result startStudy(Long studyId, Long batchId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "batchId " + batchId);
		Study study = publixUtils.retrieveStudy(studyId);
		Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
		Cookie cookie = Publix.request().cookie(GeneralSinglePublix.COOKIE);
		publixUtils.checkStudyInCookie(study, cookie);

		GeneralSingleWorker worker = workerCreator
				.createAndPersistGeneralSingleWorker();
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);
		session(WORKER_ID, worker.getId().toString());
		session(BATCH_ID, batch.getId().toString());
		Logger.info(CLASS_NAME + ".startStudy: study (study ID " + studyId
				+ ", batch ID " + batchId + ") " + "assigned to worker with ID "
				+ worker.getId());

		publixUtils.finishAllPriorStudyResults(worker, study);
		resultCreator.createStudyResult(study, batch, worker);

		String cookieValue = publixUtils.addStudyToCookie(study, cookie);
		Publix.response().setCookie(GeneralSinglePublix.COOKIE, cookieValue);

		Component firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
