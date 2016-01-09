package services.publix;

import models.common.workers.Worker;

/**
 * Helper class with methods that generate error strings.
 *
 * @author Kristian Lange
 */
public class PublixErrorMessages {

	public static final String STUDY_NEVER_FINSHED = "Study not completed, but new study started by the same worker";
	public static final String NO_WORKERID_IN_SESSION = "No worker ID in session. Was the study started?";
	public static final String NO_BATCHID_IN_SESSION = "No batch ID in session. Was the study started?";
	public static final String COMPONENTS_POSITION_NOT_NULL = "Component's positions can't be null.";
	public static final String UNKNOWN_WORKER_TYPE = "Unknown worker type";
	public static final String STUDY_CAN_BE_DONE_ONLY_ONCE = "Study can be done only once.";
	public static final String SUBMITTED_DATA_UNKNOWN_FORMAT = "Submitted data have an unknown format.";
	public static final String NO_WORKER_IN_SESSION = "Sorry this study is not available to you (any more). Maybe you tried to reload a component that wasn't allowed to be reloaded?";

	public String workerNotCorrectType(Long workerId) {
		return "The worker with ID " + workerId
				+ " isn't the right worker type.";
	}

	public String workerNeverDidStudy(Worker worker, Long studyId) {
		return "Worker " + worker.getId() + " never did study " + studyId + ".";
	}

	public String workerNotAllowedStudy(Worker worker, Long studyId) {
		return "Worker " + worker.getId() + " is not allowed to do " + "study "
				+ studyId + ".";
	}

	public String workerFinishedStudyAlready(Worker worker, Long studyId) {
		return "Worker " + worker.getId() + " finished study " + studyId
				+ " already.";
	}

	public String workerNotExist(Long workerId) {
		return workerNotExist(String.valueOf(workerId));
	}

	public String workerNotExist(String workerIdStr) {
		return "A worker with ID " + workerIdStr + " doesn't exist.";
	}

	public String workerTypeNotAllowed(String workerType) {
		return "It's not allowed to run this study with a worker of type \""
				+ workerType + "\".";
	}

	public String batchNotExist(Long batchId) {
		return workerNotExist(String.valueOf(batchId));
	}
	
	public String batchNotExist(String batchIdStr) {
		return "A batch with ID " + batchIdStr + " doesn't exist.";
	}
	
	public String batchMaxTotalWorkerReached(Long batchId) {
		return "For the batch (ID: " + batchId
				+ ") the maximum number of workers is already reached.";
	}
	
	public String batchInactive(Long batchId) {
		return "The batch (ID: " + batchId + ") is inactive.";
	}

	public String componentNotAllowedToReload(Long studyId, Long componentId) {
		return "It's not allowed to reload this component (ID: " + componentId
				+ "). Unfortunately it is neccessary to finish "
				+ "this study (ID: " + studyId + ") at this point.";
	}

	public String studyHasNoActiveComponents(Long studyId) {
		return "The study with ID " + studyId + " has no active components.";
	}

	public String studyNotGroupStudy(Long studyId) {
		return "The study with ID " + studyId + " doesn't allow group studies.";
	}

	public String componentNotExist(Long studyId, Long componentId) {
		return "An component with ID " + componentId + " of study " + studyId
				+ " doesn't exist.";

	}

	public String componentNotBelongToStudy(Long studyId, Long componentId) {
		return "There is no study with ID " + studyId
				+ " that has a component with ID " + componentId + ".";
	}

	public String componentNotActive(Long studyId, Long componentId) {
		return "Component with ID " + componentId + " in study with ID "
				+ studyId + " is not active.";
	}

	public String noComponentAtPosition(Long studyId, Integer position) {
		return "There is no component at position " + position + " in study "
				+ studyId + ".";
	}

	public String componentNeverStarted(Long studyId, Long componentId,
			String methodName) {
		return "Illegal function call " + methodName + ": component (ID "
				+ componentId + ") of study (ID " + studyId
				+ ") was never started.";
	}

	public String studyNotExist(Long studyId) {
		return "An study with ID " + studyId + " doesn't exist.";
	}

	public String studyFinishedWithMessage(String message) {
		return "Study finished with message: " + message;
	}

}
