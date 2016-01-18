package services.gui;

import java.util.List;
import java.util.Set;
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
		workerDao.create(worker);
		batch.addWorker(worker);
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
		workerDao.create(worker);
		batch.addWorker(worker);
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

}
