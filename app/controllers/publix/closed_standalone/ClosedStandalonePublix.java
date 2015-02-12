package controllers.publix.closed_standalone;

import models.ComponentModel;
import models.StudyModel;
import models.workers.ClosedStandaloneWorker;
import play.Logger;
import play.mvc.Result;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import daos.IComponentResultDao;
import daos.IStudyResultDao;
import exceptions.PublixException;

/**
 * Implementation of JATOS' public API for closed standalone study runs
 * (standalone runs with invitation and pre-created worker).
 * 
 * @author Kristian Lange
 */
@Singleton
public class ClosedStandalonePublix extends Publix<ClosedStandaloneWorker>
		implements IPublix {

	public static final String CLOSEDSTANDALONE_WORKER_ID = "closedStandaloneWorkerId";

	private static final String CLASS_NAME = ClosedStandalonePublix.class
			.getSimpleName();

	private final ClosedStandalonePublixUtils publixUtils;

	@Inject
	ClosedStandalonePublix(ClosedStandaloneErrorMessages errorMessages,
			ClosedStandalonePublixUtils publixUtils,
			IComponentResultDao componentResultDao, JsonUtils jsonUtils,
			IStudyResultDao studyResultDao) {
		super(publixUtils, componentResultDao, jsonUtils, studyResultDao);
		this.publixUtils = publixUtils;
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		String workerIdStr = getQueryString(CLOSEDSTANDALONE_WORKER_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ CLOSEDSTANDALONE_WORKER_ID + " " + workerIdStr);
		StudyModel study = publixUtils.retrieveStudy(studyId);

		ClosedStandaloneWorker worker = publixUtils
				.retrieveTypedWorker(workerIdStr);
		publixUtils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, workerIdStr);

		publixUtils.finishAllPriorStudyResults(worker, study);
		studyResultDao.create(study, worker);

		ComponentModel firstComponent = publixUtils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
