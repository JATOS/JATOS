package services.publix;

import java.util.Set;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.Worker;

public abstract class StudyAuthorisation<T extends Worker> {

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

	/**
	 * Check if the max total worker number is reached for this batch. Only
	 * non-JatosWorker count here.
	 */
	public void checkMaxTotalWorkers(Batch batch, Worker worker)
			throws ForbiddenPublixException {
		Set<Worker> workerSet = batch.getWorkerList();
		// Add the worker who wants to run the study (he might have run it
		// already)
		workerSet.add(worker);
		int potentialWorkerNumber = workerSet.size();
		if (batch.getMaxTotalWorkers() != null
				&& potentialWorkerNumber > batch.getMaxTotalWorkers()) {
			throw new ForbiddenPublixException(PublixErrorMessages
					.batchMaxTotalWorkerReached(batch.getId()));
		}
	}

}
