package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.workers.StandaloneWorker;
import play.Logger;
import play.mvc.Result;
import services.PersistanceUtils;
import exceptions.PublixException;

/**
 * Implementation of JATOS' public API for standalone study runs.
 * 
 * @author Kristian Lange
 */
public class StandalonePublix extends Publix<StandaloneWorker> implements
		IPublix {

	public static final String STANDALONE_WORKER_ID = "standaloneWorkerId";

	private static final String CLASS_NAME = StandalonePublix.class
			.getSimpleName();

	protected static final StandaloneErrorMessages errorMessages = new StandaloneErrorMessages();
	protected static final StandalonePublixUtils utils = new StandalonePublixUtils(
			errorMessages);

	public StandalonePublix() {
		super(utils);
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		String standaloneWorkerIdStr = getQueryString(STANDALONE_WORKER_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "standaloneWorkerId " + standaloneWorkerIdStr);
		StudyModel study = utils.retrieveStudy(studyId);

		StandaloneWorker worker = utils
				.retrieveTypedWorker(standaloneWorkerIdStr);
		utils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, standaloneWorkerIdStr);

		utils.finishAllPriorStudyResults(worker, study);
		PersistanceUtils.createStudyResult(study, worker);

		ComponentModel firstComponent = utils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
