package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.results.StudyResult;
import models.workers.TesterWorker;
import play.Logger;
import play.mvc.Result;
import services.PersistanceUtils;
import services.TesterErrorMessages;
import controllers.ControllerUtils;
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

		session(WORKER_ID, testerId);
		TesterWorker worker = utils.retrieveWorker();
		utils.checkWorkerAllowedToDoStudy(worker, study);

		utils.finishAllPriorStudyResults(worker, study);
		PersistanceUtils.createStudyResult(study, worker);

		ComponentModel firstComponent = utils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

	@Override
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException {
		Logger.info(CLASS_NAME + ".finishStudy: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID) + ", " + "successful "
				+ successful + ", " + "errorMsg \"" + errorMsg + "\"");
		StudyModel study = utils.retrieveStudy(studyId);
		TesterWorker worker = utils.retrieveWorker();
		utils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		if (!utils.studyDone(studyResult)) {
			utils.finishStudy(successful, errorMsg, studyResult);
		}

		PublixUtils.discardIdCookie();
		if (ControllerUtils.isAjax()) {
			return ok();
		} else {
			if (!successful) {
				return ok(views.html.publix.error.render(errorMsg));
			} else {
				return ok(views.html.publix.finishedAndThanks.render());
			}
		}
	}

}
