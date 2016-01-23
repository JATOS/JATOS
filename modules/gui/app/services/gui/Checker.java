package services.gui;

import java.util.List;

import javax.inject.Singleton;

import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import general.common.MessagesStrings;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;

/**
 * Service class that provides checks for different entities
 * 
 * @author Kristian Lange
 */
@Singleton
public class Checker {

	/**
	 * Checks the component of this study and throws an Exception in case of a
	 * problem.
	 */
	public void checkStandardForComponents(Long studyId, Long componentId,
			Component component) throws BadRequestException {
		if (component == null) {
			throw new BadRequestException(
					MessagesStrings.componentNotExist(componentId));
		}
		if (component.getStudy() == null) {
			throw new BadRequestException(
					MessagesStrings.componentHasNoStudy(componentId));
		}
		// Check component belongs to the study
		if (!component.getStudy().getId().equals(studyId)) {
			throw new BadRequestException(MessagesStrings
					.componentNotBelongToStudy(studyId, componentId));
		}
	}

	/**
	 * Checks the batch and throws an Exception in case of a problem.
	 */
	public void checkStandardForBatch(Batch batch, Study study, Long batchId)
			throws ForbiddenException, BadRequestException {
		if (batch == null) {
			String errorMsg = MessagesStrings.batchNotExist(batchId);
			throw new BadRequestException(errorMsg);
		}
		// Check that the study has this batch
		if (!study.hasBatch(batch)) {
			String errorMsg = MessagesStrings.batchNotInStudy(batchId,
					study.getId());
			throw new ForbiddenException(errorMsg);
		}
	}

	/**
	 * Throws an ForbiddenException if this batch is the default batch of it's
	 * study.
	 */
	public void checkDefaultBatch(Batch batch) throws ForbiddenException {
		if (batch.equals(batch.getStudy().getDefaultBatch())) {
			String errorMsg = MessagesStrings.BATCH_NOT_ALLOWED_DELETE_DEFAULT;
			throw new ForbiddenException(errorMsg);
		}
	}

	/**
	 * Throws an ForbiddenException if a study is locked.
	 */
	public void checkStudyLocked(Study study) throws ForbiddenException {
		if (study.isLocked()) {
			String errorMsg = MessagesStrings.studyLocked(study.getId());
			throw new ForbiddenException(errorMsg);
		}
	}

	/**
	 * Checks the study and throws an Exception in case of a problem.
	 */
	public void checkStandardForStudy(Study study, Long studyId, User user)
			throws ForbiddenException, BadRequestException {
		if (study == null) {
			String errorMsg = MessagesStrings.studyNotExist(studyId);
			throw new BadRequestException(errorMsg);
		}
		// Check that the user is a user of the study
		if (!study.hasUser(user)) {
			String errorMsg = MessagesStrings.studyNotUser(user.getName(),
					user.getEmail(), studyId, study.getTitle());
			throw new ForbiddenException(errorMsg);
		}
	}

	/**
	 * Checks a list of ComponentResult. Checks each ComponentResult whether the
	 * belonging Study and Component are fine (checkStandard). It also checks
	 * whether the study is locked.
	 * 
	 * @param componentResultList
	 *            A list of ComponentResults
	 * @param user
	 *            The study that corresponds to the results must have this user
	 *            otherwise ForbiddenException will be thrown.
	 * @param studyMustNotBeLocked
	 *            If true the study that corresponds to the results must not be
	 *            locked and it will throw an ForbiddenException.
	 * @throws ForbiddenException
	 * @throws BadRequestException
	 */
	public void checkComponentResults(List<ComponentResult> componentResultList,
			User user, boolean studyMustNotBeLocked)
					throws ForbiddenException, BadRequestException {
		for (ComponentResult componentResult : componentResultList) {
			Component component = componentResult.getComponent();
			Study study = component.getStudy();
			checkStandardForStudy(study, study.getId(), user);
			checkStandardForComponents(study.getId(), component.getId(),
					component);
			if (studyMustNotBeLocked) {
				checkStudyLocked(study);
			}
		}
	}

	/**
	 * Checks a list of StudyResult. Checks each StudyResult whether the
	 * belonging Study is fine (checkStandard). It also checks whether the study
	 * is locked.
	 * 
	 * @param studyResultList
	 *            A list of StudyResults
	 * @param user
	 *            The study that corresponds to the results must have this user
	 *            otherwise ForbiddenException will be thrown.
	 * @param studyMustNotBeLocked
	 *            If true the study that corresponds to the results must not be
	 *            locked and it will throw an ForbiddenException.
	 * @throws ForbiddenException
	 * @throws BadRequestException
	 */
	public void checkStudyResults(List<StudyResult> studyResultList, User user,
			boolean studyMustNotBeLocked)
					throws ForbiddenException, BadRequestException {
		for (StudyResult studyResult : studyResultList) {
			Study study = studyResult.getStudy();
			checkStandardForStudy(study, study.getId(), user);
			if (studyMustNotBeLocked) {
				checkStudyLocked(study);
			}
		}
	}

	/**
	 * Throws an Exception in case the user isn't equal to the loggedInUser.
	 */
	public void checkUserLoggedIn(User user, User loggedInUser)
			throws ForbiddenException {
		if (!user.equals(loggedInUser)) {
			throw new ForbiddenException(
					MessagesStrings.userMustBeLoggedInToSeeProfile(user));
		}
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
			checkStandardForStudy(study, study.getId(), loggedInUser);
			checkStudyLocked(study);
		}
	}

}
