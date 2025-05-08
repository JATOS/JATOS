package services.publix;

import models.common.workers.Worker;

/**
 * Helper class with methods that generate error strings.
 *
 * @author Kristian Lange
 */
public class PublixErrorMessages {

	public static final String ABANDONED_STUDY_BY_COOKIE = "Closed by JATOS: Too many unfinished studies open in the same browser.";
	public static final String STUDY_CAN_BE_DONE_ONLY_ONCE = "Study can be done only once.";
	public static final String IDCOOKIE_COLLECTION_FULL = "Can't generate a new ID cookie due to max number is reached. This can happen if multiple study runs are started at the same time in the same browser.";
	public static final String IDCOOKIE_COLLECTION_INDEX_OUT_OF_BOUND = "Max number of ID cookies is reached but a new index was requested.";

	public static String workerNotAllowedStudy(Worker worker, Long studyId) {
		return "Worker " + worker.getId() + " is not allowed to do study " + studyId + ".";
	}

	public static String workerNotExist(String workerIdStr) {
		return "A worker with ID " + workerIdStr + " doesn't exist.";
	}

	public static String workerTypeNotAllowed(String workerType, Long studyId,
			Long batchId) {
		return "It's not allowed to run this study (ID: " + studyId + ") in this batch (ID: " + batchId
				+ ") with a worker of type \"" + workerType + "\".";
	}

	public static String batchMaxTotalWorkerReached(Long batchId) {
		return "For the batch (ID: " + batchId + ") the maximum number of workers is already reached.";
	}

	public static String studyDeactivated(Long studyId) {
		return "The study (ID: " + studyId + ") was deactivated by an admin.";
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
		return "An component with ID " + componentId + " of study " + studyId + " doesn't exist.";
	}

	public static String componentNotBelongToStudy(Long studyId, Long componentId) {
		return "There is no study with ID " + studyId + " that has a component with ID " + componentId + ".";
	}

	public static String componentNotActive(Long studyId, Long componentId) {
		return "Component with ID " + componentId + " in study with ID " + studyId + " is not active.";
	}

	public static String studyFinishedWithMessage(String message) {
		return "Study finished with message: " + message;
	}

	public static String studyAssetsNotAllowedOutsideRun(String filePath) {
		return "You tried to access the file " + filePath + " but it seems you have no access rights.";
	}

	public static String couldntExtractFromIdCookie(String idCookieName, String key) {
		return "Couldn't extract " + key + " from JATOS ID cookie " + idCookieName + ". If this problem persists try " +
				"deleting JATOS cookies for this domain in your browser.";
	}

	public static String couldntExtractIndexFromIdCookieName(String idCookieName) {
		return "Couldn't extract index from ID cookie's name " + idCookieName + ". Try deleting JATOS cookies for " +
		"this domain in your browser.";
	}

	public static String idCookieForThisStudyResultNotExists(Long studyResultId) {
		return "Couldn't find cookie for study result ID " + studyResultId + ". Are cookies allowed in your browser?";
	}

	public static String idCookieExistsAlready(Long studyResultId) {
		return "An JATOS ID cookie with study result ID " + studyResultId + " already exists. Try deleting JATOS cookies for " +
				"this domain in your browser.";
	}

}
