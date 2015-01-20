package controllers.publix.tester;

import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.routes;
import models.ComponentModel;
import models.StudyModel;
import models.workers.TesterWorker;
import play.Logger;
import play.mvc.Result;
import services.PersistanceUtils;
import exceptions.PublixException;

/**
 * Implementation of JATOS' public API for studies run by tester worker.
 * 
 * @author Kristian Lange
 */
public class TesterPublix extends Publix<TesterWorker> implements IPublix {

	public static final String TESTER_ID = "testerId";

	private static final String CLASS_NAME = TesterPublix.class.getSimpleName();

	protected static final TesterErrorMessages errorMessages = new TesterErrorMessages();
	protected static final TesterPublixUtils utils = new TesterPublixUtils(
			errorMessages);

	public TesterPublix() {
		super(utils);
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		String testerId = getQueryString(TESTER_ID);
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId + ", "
				+ "testerId " + testerId);
		StudyModel study = utils.retrieveStudy(studyId);

		TesterWorker worker = utils.retrieveTypedWorker(testerId);
		utils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, testerId);

		utils.finishAllPriorStudyResults(worker, study);
		PersistanceUtils.createStudyResult(study, worker);

		ComponentModel firstComponent = utils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
