package services.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Study;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import services.publix.IStudyAuthorisation;
import services.publix.PublixErrorMessages;
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
	public void checkWorkerAllowedToStartStudy(MTWorker worker, Study study)
			throws ForbiddenPublixException {
		if (!(worker instanceof MTSandboxWorker)
				&& publixUtils.didStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(MTWorker worker, Study study)
			throws ForbiddenPublixException {
		if (!study.getGroupList().get(0)
				.hasAllowedWorkerType(worker.getWorkerType())) {
			throw new ForbiddenPublixException(errorMessages
					.workerTypeNotAllowed(worker.getUIWorkerType()));
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
