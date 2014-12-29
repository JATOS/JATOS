package controllers.publix;

import models.workers.Worker;

/**
 * Helper class with methods that generate error strings.
 * 
 * @author Kristian Lange
 */
public abstract class PublixErrorMessages<T extends Worker> {

	public abstract String workerNeverStartedStudy(T worker, Long studyId);

	public abstract String workerFinishedStudyAlready(T worker, Long studyId);

	public abstract String workerNeverDidStudy(T worker, Long studyId);

	public abstract String workerNotAllowedStudy(T worker, Long studyId);
	
	public abstract String workerNotCorrectType(Long workerId);

	// Publix
	public static final String NO_MORE_COMPONENTS_IN_STUDY = "There aren't any more components in this study.";
	public static final String STUDY_OR_COMPONENT_NEVER_STARTED_FROM_JATOS = "This study or component was never started from within JATOS.";
	public static final String STUDY_NEVER_STARTED_FROM_JATOS = "This study was never started from within JATOS.";
	public static final String STUDY_NEVER_FINSHED = "Study never finished, but new study started by the same worker";
	public static final String NO_ASSIGNMENT_ID = "No assignment ID specified in query parameters";
	public static final String NO_MTURK_WORKERID = "MTurk's workerId is missing in the query parameters.";
	public static final String NO_TESTER_WORKERID = "Tester's workerId is missing in the query parameters.";
	public static final String NO_WORKERID_IN_SESSION = "No worker ID in session. Was the study started?";
	public static final String COMPONENTS_POSITION_NOT_NULL = "Component's positions can't be null.";
	public static final String UNKNOWN_WORKER_TYPE = "Unknown worker type";
	public static final String STUDY_CAN_BE_DONE_ONLY_ONCE = "Study can be done only once.";

	public static String workerNotExist(Long workerId) {
		return workerNotExist(String.valueOf(workerId));
	}

	public static String workerNotExist(String workerIdStr) {
		String errorMsg = "A worker with ID " + workerIdStr + " doesn't exist.";
		return errorMsg;
	}
	
	public static String componentNotAllowedToReload(Long studyId,
			Long componentId) {
		String errorMsg = "It's not allowed to reload this component (ID: "
				+ componentId + "). Unfortunately it is neccessary to finish "
				+ "this study (ID: " + studyId + ") at this point.";
		return errorMsg;
	}
	
	public static String studyHasNoActiveComponents(Long studyId) {
		String errorMsg = "The study with ID " + studyId
				+ " has no active components.";
		return errorMsg;
	}
	
	public static String componentNotExist(Long componentId) {
		String errorMsg = "An component with ID " + componentId
				+ " doesn't exist.";
		return errorMsg;
	}

	public static String componentNotExist(Long studyId, Long componentId) {
		String errorMsg = "An component with ID " + componentId + " of study "
				+ studyId + " doesn't exist.";
		return errorMsg;
	}
	
	public static String componentNotBelongToStudy(Long studyId,
			Long componentId) {
		String errorMsg = "There is no study with ID " + studyId
				+ " that has a component with ID " + componentId + ".";
		return errorMsg;
	}
	
	public static String componentNotActive(Long studyId, Long componentId) {
		String errorMsg = "Component with ID " + componentId
				+ " in study with ID " + studyId + " is not active.";
		return errorMsg;
	}
	
	public static String noComponentAtPosition(Long studyId, Integer position) {
		String errorMsg = "There is no component at position " + position
				+ " in study " + studyId + ".";
		return errorMsg;
	}
	
	public static String studyNotExist(Long studyId) {
		String errorMsg = "An study with ID " + studyId + " doesn't exist.";
		return errorMsg;
	}
	
	public static String submittedDataUnknownFormat(Long studyId,
			Long componentId) {
		String errorMsg = "Unknown format of submitted data for component "
				+ componentId + " of study " + studyId + ".";
		return errorMsg;
	}
	
}
