package services.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.BatchDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import models.common.Batch;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import play.data.validation.ValidationError;

/**
 * Service class for JATOS Controllers (not Publix)..
 * 
 * @author Kristian Lange
 */
@Singleton
public class WorkerService {

	private final StudyResultDao studyResultDao;
	private final WorkerDao workerDao;
	private final BatchDao batchDao;

	@Inject
	WorkerService(StudyResultDao studyResultDao,
			WorkerDao workerDao, BatchDao batchDao) {
		this.studyResultDao = studyResultDao;
		this.workerDao = workerDao;
		this.batchDao = batchDao;
	}

	/**
	 * Retrieve all workersProvider that did this study.
	 */
	public Set<Worker> retrieveWorkers(Study study) {
		List<StudyResult> studyResultList = studyResultDao
				.findAllByStudy(study);
		return studyResultList.stream().map(StudyResult::getWorker)
				.collect(Collectors.toSet());
	}

	/**
	 * Creates, validates and persists a PersonalSingleWorker.
	 */
	public PersonalSingleWorker createAndPersistPersonalSingleWorker(
			String comment, Batch batch) throws BadRequestException {
		PersonalSingleWorker worker = new PersonalSingleWorker(comment);
		validateWorker(worker);
		batch.addWorker(worker);
		workerDao.create(worker);
		batchDao.update(batch);
		return worker;
	}

	/**
	 * Creates, validates and persists a PersonalMultipleWorker (worker for a
	 * Personal Multiple Run).
	 */
	public PersonalMultipleWorker createAndPersistPersonalMultipleWorker(
			String comment, Batch batch) throws BadRequestException {
		PersonalMultipleWorker worker = new PersonalMultipleWorker(comment);
		validateWorker(worker);
		batch.addWorker(worker);
		workerDao.create(worker);
		batchDao.update(batch);
		return worker;
	}

	private void validateWorker(Worker worker) throws BadRequestException {
		List<ValidationError> errorList = worker.validate();
		if (errorList != null && !errorList.isEmpty()) {
			String errorMsg = errorList.get(0).message();
			throw new BadRequestException(errorMsg);
		}
	}
	
	/**
	 * Retrieves the count of StudyResults for each worker type in a map (
	 * workerType -> count).
	 */
	public Map<String, Integer> retrieveStudyResultCountsPerWorker(
			Batch batch) {
		Map<String, Integer> resultsPerWorker = new HashMap<>();
		Consumer<String> putCountToMap = (String workerType) -> resultsPerWorker
				.put(workerType, studyResultDao.countByBatchAndWorkerType(batch,
						workerType));
		putCountToMap.accept(JatosWorker.WORKER_TYPE);
		putCountToMap.accept(GeneralSingleWorker.WORKER_TYPE);
		putCountToMap.accept(PersonalSingleWorker.WORKER_TYPE);
		putCountToMap.accept(PersonalMultipleWorker.WORKER_TYPE);
		putCountToMap.accept(MTWorker.WORKER_TYPE);
		putCountToMap.accept(MTSandboxWorker.WORKER_TYPE);
		return resultsPerWorker;
	}
	
	/**
	 * Get all workers (PersonalSingleWorker, PersonalMultipleWorker,
	 * GeneralSingle, MTWorker, MTSandboxWorker and JatosWorker) that belong to
	 * the given batch.
	 */
	public Set<Worker> retrieveAllWorkers(Study study, Batch batch) {
		// Batch's workerList has all workers except JatosWorkers
		Set<Worker> workerSet = batch.getWorkerList();

		// Add Jatos workers of this study.
		study.getUserList().stream().map(u -> u.getWorker())
				.forEachOrdered(workerSet::add);
		return workerSet;
	}

}
