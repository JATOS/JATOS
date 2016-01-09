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
import exceptions.gui.ForbiddenException;
import general.common.MessagesStrings;
import models.common.Batch;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.JatosWorker;
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

	private final StudyService studyService;
	private final StudyResultDao studyResultDao;
	private final WorkerDao workerDao;
	private final BatchDao batchDao;

	@Inject
	WorkerService(StudyService studyService, StudyResultDao studyResultDao,
			WorkerDao workerDao, BatchDao batchDao) {
		this.studyService = studyService;
		this.studyResultDao = studyResultDao;
		this.workerDao = workerDao;
		this.batchDao = batchDao;
	}

	/**
	 * Throws a Exception in case the worker doesn't exist. Distinguishes
	 * between normal and Ajax request.
	 */
	public void checkWorker(Worker worker, Long workerId)
			throws BadRequestException {
		if (worker == null) {
			throw new BadRequestException(
					MessagesStrings.workerNotExist(workerId));
		}
	}

	/**
	 * Check whether the removal of this worker is allowed.
	 * 
	 * @throws BadRequestException
	 * @throws ForbiddenException
	 */
	public void checkRemovalAllowed(Worker worker, User loggedInUser)
			throws ForbiddenException, BadRequestException {
		// JatosWorker associated to a JATOS user must not be removed
		if (worker instanceof JatosWorker) {
			JatosWorker maWorker = (JatosWorker) worker;
			String errorMsg = MessagesStrings.removeJatosWorkerNotAllowed(
					worker.getId(), maWorker.getUser().getName(),
					maWorker.getUser().getEmail());
			throw new ForbiddenException(errorMsg);
		}

		// Check for every study if removal is allowed
		for (StudyResult studyResult : worker.getStudyResultList()) {
			Study study = studyResult.getStudy();
			studyService.checkStandardForStudy(study, study.getId(),
					loggedInUser);
			studyService.checkStudyLocked(study);
		}
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
	public PersonalSingleWorker createPersonalSingleWorker(String comment,
			Batch batch) throws BadRequestException {
		PersonalSingleWorker worker = new PersonalSingleWorker(comment);
		validateWorker(worker);
		workerDao.create(worker);
		batch.addAllowedWorker(worker);
		batchDao.update(batch);
		return worker;
	}

	/**
	 * Creates, validates and persists a PersonalMultipleWorker (worker for a
	 * Personal Multiple Run).
	 */
	public PersonalMultipleWorker createPersonalMultipleWorker(String comment,
			Batch batch) throws BadRequestException {
		PersonalMultipleWorker worker = new PersonalMultipleWorker(comment);
		validateWorker(worker);
		workerDao.create(worker);
		batch.addAllowedWorker(worker);
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
