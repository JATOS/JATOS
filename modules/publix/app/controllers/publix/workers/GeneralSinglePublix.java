package controllers.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Component;
import models.common.Study;
import models.common.workers.GeneralSingleWorker;
import play.Logger;
import play.db.jpa.JPAApi;
import play.mvc.Http.Cookie;
import services.publix.ChannelService;
import services.publix.GroupMessagingService;
import services.publix.workers.GeneralSingleErrorMessages;
import services.publix.workers.GeneralSinglePublixUtils;
import services.publix.workers.GeneralSingleStudyAuthorisation;
import play.mvc.Result;
import utils.common.JsonUtils;
import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.publix.PublixException;

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
	private final WorkerDao workerDao;

	@Inject
	GeneralSinglePublix(JPAApi jpa, GeneralSinglePublixUtils publixUtils,
			GeneralSingleStudyAuthorisation studyAuthorisation,
			GroupMessagingService groupMessagingService,
			ChannelService channelService,
			GeneralSingleErrorMessages errorMessages, StudyAssets studyAssets,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao, WorkerDao workerDao,
			GroupResultDao groupResultDao) {
		super(jpa, publixUtils, studyAuthorisation, groupMessagingService,
				channelService, errorMessages, studyAssets, componentResultDao,
				jsonUtils, studyResultDao, groupResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.workerDao = workerDao;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId);
		Study study = publixUtils.retrieveStudy(studyId);
		Cookie cookie = Publix.request().cookie(GeneralSinglePublix.COOKIE);
		publixUtils.checkStudyInCookie(study, cookie);

		GeneralSingleWorker worker = new GeneralSingleWorker();
		workerDao.create(worker);
		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, worker.getId().toString());
		Logger.info(CLASS_NAME + ".startStudy: study (ID " + studyId + ") "
				+ "assigned to worker with ID " + worker.getId());

		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);

		String cookieValue = publixUtils.addStudyToCookie(study, cookie);
		Publix.response().setCookie(GeneralSinglePublix.COOKIE, cookieValue);

		Component firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
