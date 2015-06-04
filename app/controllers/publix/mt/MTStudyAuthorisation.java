package controllers.publix.mt;

import models.StudyModel;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.IStudyAuthorisation;
import controllers.publix.PublixErrorMessages;
import exceptions.publix.ForbiddenPublixException;

/**
 * PersonalMultiplePublix's Implementation of IStudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class MTStudyAuthorisation implements IStudyAuthorisation<MTWorker> {

	private final MTPublixUtils publixUtils;
	private final MTErrorMessages errorMessages;

	@Inject
	MTStudyAuthorisation(MTPublixUtils publixUtils,
			MTErrorMessages errorMessages) {
		this.publixUtils = publixUtils;
		this.errorMessages = errorMessages;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(MTWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		if (!(worker instanceof MTSandboxWorker)
				&& publixUtils.didStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(MTWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		// Sandbox workers can repeat studies
		if (worker instanceof MTSandboxWorker) {
			return;
		}
		// MTurk workers can't repeat studies
		if (publixUtils.finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

}
