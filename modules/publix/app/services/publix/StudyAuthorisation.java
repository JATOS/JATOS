package services.publix;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;

public abstract class StudyAuthorisation<T extends Worker> {

	private PublixErrorMessages errorMessages;
	private StudyResultDao studyResultDao;

	public StudyAuthorisation(PublixErrorMessages errorMessages,
			StudyResultDao studyResultDao) {
		this.errorMessages = errorMessages;
		this.studyResultDao = studyResultDao;
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

	public void checkMaxTotalWorkers(Batch batch, Worker worker)
			throws ForbiddenPublixException {
		int potentialWorkerNumber = retievePotentialWorkerNumber(batch, worker);
		if (batch.getMaxTotalWorkers() != null
				&& potentialWorkerNumber > batch.getMaxTotalWorkers()) {
			throw new ForbiddenPublixException(
					errorMessages.batchMaxTotalWorkerReached(batch.getId()));
		}
	}

	/**
	 * Returns the number of workers who run (at least started a study) in this
	 * batch in case the given worker would have started this study already. The
	 * same worker can run the same study several times - in this case the
	 * number stays unchanged.
	 */
	public int retievePotentialWorkerNumber(Batch batch, Worker worker) {
		List<StudyResult> studyResultList = studyResultDao
				.findAllByBatch(batch);
		// Worker can run the same study several times -> get the nr of workers
		Set<Worker> workerSet = studyResultList.stream()
				.map(sr -> sr.getWorker()).collect(Collectors.toSet());
		workerSet.add(worker);
		return workerSet.size();
	}

}
