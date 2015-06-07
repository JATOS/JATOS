package publix.controllers.general_single;

import models.ComponentModel;
import models.StudyModel;
import models.workers.GeneralSingleWorker;
import persistance.ComponentResultDao;
import persistance.StudyResultDao;
import persistance.workers.WorkerDao;
import play.Logger;
import play.mvc.Http.Cookie;
import play.mvc.Result;
import publix.controllers.IPublix;
import publix.controllers.Publix;
import publix.exceptions.PublixException;
import publix.services.general_single.GeneralSingleErrorMessages;
import publix.services.general_single.GeneralSinglePublixUtils;
import publix.services.general_single.GeneralSingleStudyAuthorisation;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Implementation of JATOS' public API for general single study runs (open to
 * everyone). A general single run is done by a GeneralSingleWorker.
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublix extends Publix<GeneralSingleWorker> implements
		IPublix {

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
	GeneralSinglePublix(GeneralSinglePublixUtils publixUtils,
			GeneralSingleStudyAuthorisation studyAuthorisation,
			GeneralSingleErrorMessages errorMessages,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao, WorkerDao workerDao) {
		super(publixUtils, studyAuthorisation, errorMessages,
				componentResultDao, jsonUtils, studyResultDao);
		this.publixUtils = publixUtils;
		this.studyAuthorisation = studyAuthorisation;
		this.workerDao = workerDao;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId);
		StudyModel study = publixUtils.retrieveStudy(studyId);
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

		ComponentModel firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(publix.controllers.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
