package services.publix;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.Worker;

public abstract class StudyAuthorisation<T extends Worker> {

	private PublixUtils<T> publixUtils;
	private PublixErrorMessages errorMessages;

	public StudyAuthorisation(PublixUtils<T> publixUtils,
			PublixErrorMessages errorMessages) {
		this.publixUtils = publixUtils;
		this.errorMessages = errorMessages;
	}

	/**
	 * Checks whether the given worker is allowed to start this study in this
	 * batch. If the worker has no permission an ForbiddenPublixException is
	 * thrown. This method should only be used during the start of a study.
	 */
	public abstract void checkWorkerAllowedToStartStudy(T worker, Study study,
			Batch batch) throws ForbiddenPublixException;

	/**
	 * Checks whether the given worker is allowed to do this study in this
	 * batch. If the worker has no permission an ForbiddenPublixException is
	 * thrown. This method can be used during all states of a StudyResult.
	 */
	public abstract void checkWorkerAllowedToDoStudy(T worker, Study study,
			Batch batch) throws ForbiddenPublixException;

	public void checkMaxTotalWorkers(Batch batch)
			throws ForbiddenPublixException {
		// Check max total workers
		int currentWorkerNumber = publixUtils
				.retrieveCurrentWorkerNumber(batch);
		// Since for the current run isn't a StudyResult created yet
		// currentWorkerNumber has to be smaller than maxTotalWorkers
		if (batch.getMaxTotalWorkers() != null
				&& currentWorkerNumber < batch.getMaxTotalWorkers()) {
			throw new ForbiddenPublixException(
					errorMessages.batchMaxTotalWorkerReached(batch.getId()));
		}
	}

}
