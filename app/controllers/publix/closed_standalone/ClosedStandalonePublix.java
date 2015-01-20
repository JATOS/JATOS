package controllers.publix.closed_standalone;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.routes;
import models.ComponentModel;
import models.StudyModel;
import models.workers.ClosedStandaloneWorker;
import play.Logger;
import play.mvc.Result;
import services.PersistanceUtils;
import exceptions.PublixException;

/**
 * Implementation of JATOS' public API for closed standalone study runs
 * (standalone runs with invitation and pre-created worker).
 * 
 * @author Kristian Lange
 */
public class ClosedStandalonePublix extends Publix<ClosedStandaloneWorker>
		implements IPublix {

	public static final String CLOSEDSTANDALONE_WORKER_ID = "closedStandaloneWorkerId";

	private static final String CLASS_NAME = ClosedStandalonePublix.class
			.getSimpleName();

	protected static final ClosedStandaloneErrorMessages errorMessages = new ClosedStandaloneErrorMessages();
	protected static final ClosedStandalonePublixUtils utils = new ClosedStandalonePublixUtils(
			errorMessages);

	public ClosedStandalonePublix() {
		super(utils);
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		String workerIdStr = getQueryString(CLOSEDSTANDALONE_WORKER_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ CLOSEDSTANDALONE_WORKER_ID + " " + workerIdStr);
		StudyModel study = utils.retrieveStudy(studyId);

		ClosedStandaloneWorker worker = utils
				.retrieveTypedWorker(workerIdStr);
		utils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, workerIdStr);

		utils.finishAllPriorStudyResults(worker, study);
		PersistanceUtils.createStudyResult(study, worker);

		ComponentModel firstComponent = utils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
