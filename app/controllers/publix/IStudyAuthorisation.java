package controllers.publix;

import models.StudyModel;
import models.workers.Worker;
import exceptions.publix.ForbiddenPublixException;

public interface IStudyAuthorisation<T extends Worker> {

	/**
	 * Checks whether the given worker is allowed to start this study. If the
	 * worker has no permission an ForbiddenPublixException is thrown. This
	 * method should only be used during the start of a study.
	 */
	public void checkWorkerAllowedToStartStudy(T worker, StudyModel study)
			throws ForbiddenPublixException;

	/**
	 * Checks whether the given worker is allowed to do this study. If the
	 * worker has no permission an ForbiddenPublixException is thrown. This
	 * method can be used during all states of a StudyResult.
	 */
	public void checkWorkerAllowedToDoStudy(T worker, StudyModel study)
			throws ForbiddenPublixException;

}
