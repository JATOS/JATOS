package controllers.publix.open_standalone;

import models.ComponentModel;
import models.StudyModel;
import models.workers.OpenStandaloneWorker;
import play.Logger;
import play.mvc.Result;
import services.PersistanceUtils;
import controllers.publix.IPublix;
import controllers.publix.Publix;
import exceptions.PublixException;

/**
 * Implementation of JATOS' public API for open standalone study runs (open to
 * everyone).
 * 
 * @author Kristian Lange
 */
public class OpenStandalonePublix extends Publix<OpenStandaloneWorker>
		implements IPublix {

	public static final String COOKIE = "JATOS_OPENSTANDALONE";
	public static final String OPENSTANDALONE = "openStandalone";

	private static final String CLASS_NAME = OpenStandalonePublix.class
			.getSimpleName();

	protected static final OpenStandaloneErrorMessages errorMessages = new OpenStandaloneErrorMessages();
	protected static final OpenStandalonePublixUtils utils = new OpenStandalonePublixUtils(
			errorMessages);

	public OpenStandalonePublix() {
		super(utils);
	}

	@Override
	public Result startStudy(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startStudy: studyId " + studyId);
		StudyModel study = utils.retrieveStudy(studyId);
		utils.checkAllowedToDoStudy(study);
		utils.addStudyToCookie(study);

		OpenStandaloneWorker worker = new OpenStandaloneWorker();
		worker.persist();
		utils.checkWorkerAllowedToStartStudy(worker, study);
		session(WORKER_ID, worker.getId().toString());

		utils.finishAllPriorStudyResults(worker, study);
		PersistanceUtils.createStudyResult(study, worker);

		ComponentModel firstComponent = utils
				.retrieveFirstActiveComponent(study);
		return redirect(controllers.publix.routes.PublixInterceptor
				.startComponent(studyId, firstComponent.getId()));
	}

}
