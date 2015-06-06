package services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.StudyModel;
import models.StudyResult;
import models.UserModel;
import models.workers.PersonalSingleWorker;
import models.workers.JatosWorker;
import models.workers.PersonalMultipleWorker;
import models.workers.Worker;
import persistance.StudyResultDao;
import persistance.workers.WorkerDao;
import play.data.validation.ValidationError;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.BadRequestException;
import exceptions.ForbiddenException;

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

	@Inject
	WorkerService(StudyService studyService, StudyResultDao studyResultDao,
			WorkerDao workerDao) {
		this.studyService = studyService;
		this.studyResultDao = studyResultDao;
		this.workerDao = workerDao;
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
	public void checkRemovalAllowed(Worker worker, UserModel loggedInUser)
			throws ForbiddenException, BadRequestException {
		// JatosWorker associated to a JATOS user must not be removed
		if (worker instanceof JatosWorker) {
			JatosWorker maWorker = (JatosWorker) worker;
			String errorMsg = MessagesStrings.removeJatosWorkerNotAllowed(
					worker.getId(), maWorker.getUser().getName(), maWorker
							.getUser().getEmail());
			throw new ForbiddenException(errorMsg);
		}

		// Check for every study if removal is allowed
		for (StudyResult studyResult : worker.getStudyResultList()) {
			StudyModel study = studyResult.getStudy();
			studyService.checkStandardForStudy(study, study.getId(),
					loggedInUser);
			studyService.checkStudyLocked(study);
		}
	}

	/**
	 * Retrieve all workersProvider that did this study.
	 */
	public Set<Worker> retrieveWorkers(StudyModel study) {
		List<StudyResult> studyResultList = studyResultDao
				.findAllByStudy(study);
		Set<Worker> workerSet = new HashSet<>();
		for (StudyResult studyResult : studyResultList) {
			workerSet.add(studyResult.getWorker());
		}
		return workerSet;
	}

	/**
	 * Creates, validates and persists a PersonalSingleWorker.
	 */
	public PersonalSingleWorker createPersonalSingleWorker(String comment,
			Long studyId) throws BadRequestException {
		PersonalSingleWorker worker = new PersonalSingleWorker(comment);
		validateWorker(studyId, worker);
		workerDao.create(worker);
		return worker;
	}

	/**
	 * Creates, validates and persists a PersonalMultipleWorker (worker for a
	 * Personal Multiple Run).
	 */
	public PersonalMultipleWorker createPersonalMultipleWorker(String comment,
			Long studyId) throws BadRequestException {
		PersonalMultipleWorker worker = new PersonalMultipleWorker(comment);
		validateWorker(studyId, worker);
		workerDao.create(worker);
		return worker;
	}

	private void validateWorker(Long studyId, Worker worker)
			throws BadRequestException {
		List<ValidationError> errorList = worker.validate();
		if (errorList != null && !errorList.isEmpty()) {
			String errorMsg = errorList.get(0).message();
			throw new BadRequestException(errorMsg);
		}
	}

}
