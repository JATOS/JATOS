package services.publix;

import models.common.Study;
import models.common.workers.Worker;
import exceptions.publix.ForbiddenPublixException;

public interface IStudyAuthorisation<T extends Worker> {

	/**
	 * Checks whether the given worker is allowed to start this study. If the
	 * worker has no permission an ForbiddenPublixException is thrown. This
	 * method should only be used during the start of a study.
	 */
	void checkWorkerAllowedToStartStudy(T worker, Study study)
			throws ForbiddenPublixException;

	/**
	 * Checks whether the given worker is allowed to do this study. If the
	 * worker has no permission an ForbiddenPublixException is thrown. This
	 * method can be used during all states of a StudyResult.
	 */
	void checkWorkerAllowedToDoStudy(T worker, Study study)
			throws ForbiddenPublixException;

}
