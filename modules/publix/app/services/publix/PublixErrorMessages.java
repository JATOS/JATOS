package services.publix;

import models.common.workers.Worker;

/**
 * Helper class with methods that generate error strings.
 *
 * @author Kristian Lange
 */
public class PublixErrorMessages {

	public static final String ABANDONED_STUDY_BY_COOKIE = "Closed by JATOS: Too many unfinished studies open in the same browser.";
	public static final String NO_WORKERID_IN_SESSION = "No worker ID in session. Was the study started?";
	public static final String COMPONENTS_POSITION_NOT_NULL = "Component's positions can't be null.";
	public static final String UNKNOWN_WORKER_TYPE = "Unknown worker type";
	public static final String STUDY_CAN_BE_DONE_ONLY_ONCE = "Study can be done only once.";
	public static final String SUBMITTED_DATA_UNKNOWN_FORMAT = "Submitted data have an unknown format.";
	public static final String NO_WORKER_IN_QUERY_STRING = "Missing worker ID in query string";
	public static final String GROUP_STUDY_NOT_POSSIBLE_TWICE = "It's not allowed to join a group after it was explicitly left.";
	public static final String IDCOOKIE_COLLECTION_FULL = "Can't generate a new ID cookie due to max number is reached. This should never happen.";
	public static final String IDCOOKIE_COLLECTION_INDEX_OUT_OF_BOUND = "Max number of ID cookies is reached but a new index was requested.";
	public static final String STUDY_RESULT_DOESN_T_BELONG_TO_THIS_STUDY = "Study result doesn't belong to this study.";
	public static final String STUDY_RESULT_DOESN_T_EXIST = "Study result doesn't exist.";

	public String workerNotCorrectType(Long workerId) {
		return "The worker with ID " + workerId
				+ " isn't the right worker type.";
	}

	public static String workerNeverDidStudy(Worker worker, Long studyId) {
		return "Worker " + worker.getId() + " never did study " + studyId + ".";
	}

	public static String workerNotAllowedStudy(Worker worker, Long studyId) {
		return "Worker " + worker.getId() + " is not allowed to do study " + studyId + ".";
	}

	public static String workerFinishedStudyAlready(Worker worker,
			Long studyId) {
		return "Worker " + worker.getId() + " finished study " + studyId + " already.";
	}

	public static String workerNotExist(Long workerId) {
		return workerNotExist(String.valueOf(workerId));
	}

	public static String workerNotExist(String workerIdStr) {
		return "A worker with ID " + workerIdStr + " doesn't exist.";
	}

	public static String workerTypeNotAllowed(String workerType, Long studyId,
			Long batchId) {
		return "It's not allowed to run this study (ID: " + studyId
				+ ") in this batch (ID: " + batchId
				+ ") with a worker of type \"" + workerType + "\".";
	}

	public static String batchNotExist(Long batchId) {
		return "A batch with ID " + batchId + " doesn't exist.";
	}

	public static String batchMaxTotalWorkerReached(Long batchId) {
		return "For the batch (ID: " + batchId
				+ ") the maximum number of workers is already reached.";
	}

	public static String batchInactive(Long batchId) {
		return "The batch (ID: " + batchId + ") is inactive.";
	}

	public static String forbiddenNonLinearStudyFlow(String studyTitle, Long studyId, Long lastComponentId,
			Long componentId) {
		return "Study '" + studyTitle + "' (ID: " + studyId + ") allows only linear study flow. "
				+ "But component " + lastComponentId + " attempted to start component " + componentId
				+ ". The study is finished.";
	}

	public static String componentNotAllowedToReload(Long studyId, Long componentId) {
		return "It's not allowed to reload this component (ID: " + componentId
				+ "). Study (ID: " + studyId + ") is finished.";
	}

	public static String studyHasNoActiveComponents(Long studyId) {
		return "The study with ID " + studyId + " has no active components.";
	}

	public static String studyNotGroupStudy(Long studyId) {
		return "The study with ID " + studyId + " doesn't allow group studies.";
	}

	public static String componentNotExist(Long studyId, Long componentId) {
		return "An component with ID " + componentId + " of study " + studyId
				+ " doesn't exist.";
	}

	public static String componentNotBelongToStudy(Long studyId,
			Long componentId) {
		return "There is no study with ID " + studyId
				+ " that has a component with ID " + componentId + ".";
	}

	public static String componentNotActive(Long studyId, Long componentId) {
		return "Component with ID " + componentId + " in study with ID "
				+ studyId + " is not active.";
	}

	public static String noComponentAtPosition(Long studyId, Integer position) {
		return "There is no component at position " + position + " in study "
				+ studyId + ".";
	}

	public static String componentNeverStarted(Long studyId, Long componentId,
			String methodName) {
		return "Illegal function call " + methodName + ": component (ID "
				+ componentId + ") of study (ID " + studyId
				+ ") was never started.";
	}

	public static String studyNotExist(Long studyId) {
		return "An study with ID " + studyId + " doesn't exist.";
	}

	public static String studyFinishedWithMessage(String message) {
		return "Study finished with message: " + message;
	}

	public String groupNotFoundForReassigning(Long studyResultId) {
		return "Couldn't reassign the study result with ID " + studyResultId
				+ " to any other group.";
	}

	public static String groupStudyResultNotMember(Long studyResultId) {
		return "The study result with ID " + studyResultId
				+ " isn't member in any group.";
	}

	public static String studyAssetsNotAllowedOutsideRun(String filePath) {
		return "You tried to access the file " + filePath
				+ " but it seems you have no access rights."
				+ " Maybe this study was never started?";
	}

	public static String couldntExtractFromIdCookie(String idCookieName,
			String key) {
		return "Couldn't extract " + key + " from JATOS ID cookie "
				+ idCookieName + ".";
	}

	public static String couldntExtractIndexFromIdCookieName(
			String idCookieName) {
		return "Couldn't extract index from ID cookie's name " + idCookieName
				+ ".";
	}

	public static String idCookieForThisStudyResultNotExists(
			Long studyResultId) {
		return "JATOS isn't allowed to run a study with the study result ID "
				+ studyResultId + ".";
	}

	public static String idCookieExistsAlready(Long studyResultId) {
		return "An IdCookie with study result ID " + studyResultId
				+ " exists already.";
	}

}
