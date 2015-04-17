package controllers.publix.tester;

import models.ComponentModel;
import models.StudyModel;
import models.workers.TesterWorker;
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
 * Implementation of JATOS' public API for studies run by tester worker.
 * 
 * @author Kristian Lange
 */
@Singleton
public class TesterPublix extends Publix<TesterWorker> implements IPublix {

	public static final String TESTER_ID = "testerId";

	private static final String CLASS_NAME = TesterPublix.class.getSimpleName();

	private final TesterPublixUtils publixUtils;

	@Inject
	TesterPublix(TesterPublixUtils publixUtils,
			TesterErrorMessages errorMessages,
			ComponentResultDao componentResultDao, JsonUtils jsonUtils,
			StudyResultDao studyResultDao) {
		super(publixUtils, errorMessages, componentResultDao, jsonUtils,
				studyResultDao);
		this.publixUtils = publixUtils;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		String testerId = getQueryString(TESTER_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "testerId " + testerId);
		StudyModel study = publixUtils.retrieveStudy(studyId);

		TesterWorker worker = publixUtils.retrieveTypedWorker(testerId);
		publixUtils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, testerId);

		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);

		ComponentModel firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
