package services.publix.personal_multiple;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Study;
import models.common.workers.PersonalMultipleWorker;
import services.publix.IStudyAuthorisation;
import exceptions.publix.ForbiddenPublixException;

/**
 * PersonalMultiplePublix's Implementation of IStudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultipleStudyAuthorisation implements
		IStudyAuthorisation<PersonalMultipleWorker> {

	private final PersonalMultipleErrorMessages errorMessages;

	@Inject
	PersonalMultipleStudyAuthorisation(
			PersonalMultipleErrorMessages errorMessages) {	
		this.errorMessages = errorMessages;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(PersonalMultipleWorker worker,
			Study study) throws ForbiddenPublixException {
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(PersonalMultipleWorker worker,
			Study study) throws ForbiddenPublixException {
		if (!study.hasAllowedWorkerType(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
	}

}
