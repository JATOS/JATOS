package controllers.publix.general_single;

import models.ComponentModel;
import models.StudyModel;
import models.workers.GeneralSingleWorker;
import persistance.ComponentResultDao;
import persistance.StudyResultDao;
import persistance.workers.WorkerDao;
import play.Logger;
import play.mvc.Result;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import exceptions.publix.PublixException;

/**
 * Implementation of JATOS' public API for general single study runs (open to
 * everyone).
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublix extends Publix<GeneralSingleWorker>
		implements IPublix {

	public static final String COOKIE = "JATOS_GENERALSINGLE";
	public static final String GENERALSINGLE = "generalSingle";

	private static final String CLASS_NAME = GeneralSinglePublix.class
			.getSimpleName();

	private final GeneralSinglePublixUtils publixUtils;
	private final WorkerDao workerDao;

	@Inject
	GeneralSinglePublix(GeneralSinglePublixUtils publixUtils,
			GeneralSingleErrorMessages errorMessages,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao, WorkerDao workerDao) {
		super(publixUtils, errorMessages, componentResultDao, jsonUtils,
				studyResultDao);
		this.publixUtils = publixUtils;
		this.workerDao = workerDao;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId);
		StudyModel study = publixUtils.retrieveStudy(studyId);
		publixUtils.checkAllowedToDoStudy(study);
		publixUtils.addStudyToCookie(study);

		GeneralSingleWorker worker = new GeneralSingleWorker();
		workerDao.create(worker);
		publixUtils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, worker.getId().toString());
		Logger.info(CLASS_NAME + ".startStudy: study (ID " + studyId + ") "
				+ "assigned to worker with ID " + worker.getId());

		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);

		ComponentModel firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
