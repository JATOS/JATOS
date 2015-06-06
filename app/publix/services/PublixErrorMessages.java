package publix.services;

import models.workers.Worker;

import com.google.inject.Singleton;

/**
 * Helper class with methods that generate error strings.
 * 
 * @author Kristian Lange
 */
@Singleton
public class PublixErrorMessages {

	public static final String NO_MORE_COMPONENTS_IN_STUDY = "There aren't any more components in this study.";
	public static final String STUDY_NEVER_FINSHED = "Study never finished, but new study started by the same worker";
	public static final String NO_WORKERID_IN_SESSION = "No worker ID in session. Was the study started?";
	public static final String COMPONENTS_POSITION_NOT_NULL = "Component's positions can't be null.";
	public static final String UNKNOWN_WORKER_TYPE = "Unknown worker type";
	public static final String STUDY_CAN_BE_DONE_ONLY_ONCE = "Study can be done only once.";
	public static final String SUBMITTED_DATA_UNKNOWN_FORMAT = "Submitted data have an unknown format.";
	public static final String NO_WORKER_IN_SESSION = "Sorry this study is not available to you (any more). Maybe you tried to reload a component that wasn't allowed to be reloaded?";

	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't the right worker type.";
		return errorMsg;
	}

	public String workerNeverStartedStudy(Worker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " never started study "
				+ studyId + ".";
		return errorMsg;
	}

	public String workerNeverDidStudy(Worker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " never did study "
				+ studyId + ".";
		return errorMsg;
	}

	public String workerNotAllowedStudy(Worker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " is not allowed to do "
				+ "study " + studyId + ".";
		return errorMsg;
	}

	public String workerFinishedStudyAlready(Worker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " finished study "
				+ studyId + " already.";
		return errorMsg;
	}

	public String workerNotExist(Long workerId) {
		return workerNotExist(String.valueOf(workerId));
	}

	public String workerNotExist(String workerIdStr) {
		String errorMsg = "A worker with ID " + workerIdStr + " doesn't exist.";
		return errorMsg;
	}

	public String workerTypeNotAllowed(String workerType) {
		String errorMsg = "It's not allowed to run this study with a worker of type \""
				+ workerType + "\".";
		return errorMsg;
	}

	public String componentNotAllowedToReload(Long studyId, Long componentId) {
		String errorMsg = "It's not allowed to reload this component (ID: "
				+ componentId + "). Unfortunately it is neccessary to finish "
				+ "this study (ID: " + studyId + ") at this point.";
		return errorMsg;
	}

	public String studyHasNoActiveComponents(Long studyId) {
		String errorMsg = "The study with ID " + studyId
				+ " has no active components.";
		return errorMsg;
	}

	public String componentNotExist(Long componentId) {
		String errorMsg = "An component with ID " + componentId
				+ " doesn't exist.";
		return errorMsg;
	}

	public String componentNotExist(Long studyId, Long componentId) {
		String errorMsg = "An component with ID " + componentId + " of study "
				+ studyId + " doesn't exist.";
		return errorMsg;
	}

	public String componentNotBelongToStudy(Long studyId, Long componentId) {
		String errorMsg = "There is no study with ID " + studyId
				+ " that has a component with ID " + componentId + ".";
		return errorMsg;
	}

	public String componentNotActive(Long studyId, Long componentId) {
		String errorMsg = "Component with ID " + componentId
				+ " in study with ID " + studyId + " is not active.";
		return errorMsg;
	}

	public String noComponentAtPosition(Long studyId, Integer position) {
		String errorMsg = "There is no component at position " + position
				+ " in study " + studyId + ".";
		return errorMsg;
	}

	public String componentNeverStarted(Long studyId, Long componentId,
			String methodName) {
		String errorMsg = "Illegal function call " + methodName
				+ ": component (ID " + componentId + ") of study (ID "
				+ studyId + ") was never started.";
		return errorMsg;
	}

	public String studyNotExist(Long studyId) {
		String errorMsg = "An study with ID " + studyId + " doesn't exist.";
		return errorMsg;
	}

	public String studyFinishedWithMessage(String message) {
		String msg = "Study finished with message: " + message;
		return msg;
	}

}
