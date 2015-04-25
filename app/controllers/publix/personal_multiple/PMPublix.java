package controllers.publix.personal_multiple;

import models.ComponentModel;
import models.StudyModel;
import models.workers.PMWorker;
import persistance.ComponentResultDao;
import persistance.StudyResultDao;
import play.Logger;
import play.mvc.Result;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import exceptions.publix.PublixException;

/**
 * Implementation of JATOS' public API for studies run by personal multiple
 * worker.
 * 
 * @author Kristian Lange
 */
@Singleton
public class PMPublix extends Publix<PMWorker> implements IPublix {

	public static final String PERSONAL_MULTIPLE_ID = "personalMultipleId";

	private static final String CLASS_NAME = PMPublix.class.getSimpleName();

	private final PMPublixUtils publixUtils;

	@Inject
	PMPublix(PMPublixUtils publixUtils, PMErrorMessages errorMessages,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao) {
		super(publixUtils, errorMessages, componentResultDao, jsonUtils,
				studyResultDao);
		this.publixUtils = publixUtils;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		String workerId = getQueryString(PERSONAL_MULTIPLE_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "workerId " + workerId);
		StudyModel study = publixUtils.retrieveStudy(studyId);

		PMWorker worker = publixUtils.retrieveTypedWorker(workerId);
		publixUtils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, workerId);

		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);

		ComponentModel firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
