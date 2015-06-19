package publix.services.personal_multiple;

import publix.exceptions.ForbiddenPublixException;
import publix.services.IStudyAuthorisation;
import models.StudyModel;
import models.workers.PersonalMultipleWorker;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * PersonalMultiplePublix's Implementation of IStudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultipleStudyAuthorisation
		implements IStudyAuthorisation<PersonalMultipleWorker> {

	private final PersonalMultipleErrorMessages errorMessages;

	@Inject
	PersonalMultipleStudyAuthorisation(PersonalMultipleErrorMessages errorMessages) {
		this.errorMessages = errorMessages;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(PersonalMultipleWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(PersonalMultipleWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
	}

}
