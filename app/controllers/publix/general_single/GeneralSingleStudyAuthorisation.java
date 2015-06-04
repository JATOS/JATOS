package controllers.publix.general_single;

import models.StudyModel;
import models.workers.GeneralSingleWorker;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.IStudyAuthorisation;
import controllers.publix.PublixErrorMessages;
import exceptions.publix.ForbiddenPublixException;

/**
 * GeneralSinglePublix's Implementation of IStudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSingleStudyAuthorisation implements
		IStudyAuthorisation<GeneralSingleWorker> {

	private final GeneralSingleErrorMessages errorMessages;
	private final GeneralSinglePublixUtils publixUtils;

	@Inject
	GeneralSingleStudyAuthorisation(GeneralSinglePublixUtils publixUtils,
			GeneralSingleErrorMessages errorMessages) {
		this.errorMessages = errorMessages;
		this.publixUtils = publixUtils;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(GeneralSingleWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(GeneralSingleWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		// General single workers can't repeat the same study
		if (publixUtils.finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

}
