package services;

import models.UserModel;
import models.workers.Worker;

/**
 * Helper class with methods that generate error strings.
 * 
 * @author madsen
 */
public abstract class ErrorMessages<T extends Worker> {

	public abstract String workerNeverStartedStudy(T worker, Long studyId);

	public abstract String workerNeverDidStudy(T worker, Long studyId);

	public abstract String workerNotAllowedStudy(T worker, Long studyId);

	public static final String PROBLEMS_DESERIALIZING_JSON_DATA_STRING_INVALID_JSON_FORMAT = "Problems deserializing JSON data string: invalid JSON format.";
	public static final String MISSING_TITLE = "Missing title";
	public static final String MISSING_URL = "Missing URL";
	public static final String MISSING_NAME = "Missing name";
	public static final String MISSING_EMAIL = "Missing email";
	public static final String NEITHER_A_PATH_NOR_AN_URL_YOU_CAN_LEAVE_IT_EMPTY = "Neither a path nor an URL (you can leave it empty)";
	public static final String JSON_DATA_MISSING_OR_INVALID_JSON_FORMAT = "JSON data missing or invalid JSON format.";
	public static final String WRONG_OLD_PASSWORD = "Wrong old password";
	public static final String PASSWORDS_ARENT_THE_SAME = "Passwords aren't the same.";
	public static final String PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS = "Passwords shouldn't be empty strings.";
	public static final String THIS_EMAIL_IS_ALREADY_REGISTERED = "This email is already registered.";

	public static String noPreviewAvailable(Long studyId) {
		String errorMsg = "No preview available for study " + studyId + ".";
		return errorMsg;
	}

	public static String noWorkerIdInSession() {
		String errorMsg = "No worker ID in session. Was the study started?";
		return errorMsg;
	}

	public static String workerNotExist(Long workerId) {
		return workerNotExist(String.valueOf(workerId));
	}

	public static String workerNotExist(String workerIdStr) {
		String errorMsg = "A worker with ID " + workerIdStr + " doesn't exist.";
		return errorMsg;
	}

	public static String studyNotExist(Long studyId) {
		String errorMsg = "An study with ID " + studyId + " doesn't exist.";
		return errorMsg;
	}

	public static String studyHasNoComponents(Long studyId) {
		String errorMsg = "The study with ID " + studyId
				+ " has no components.";
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

	public static String componentNotAllowedToReload(Long studyId,
			Long componentId) {
		String errorMsg = "It's not allowed to reload this component (ID: "
				+ componentId + "). Unfortunately it is neccessary to finish "
				+ "this study (ID: " + studyId + ") at this point.";
		return errorMsg;
	}

	public static String componentAlreadyFinishedOrFailed(Long studyId,
			Long componentId) {
		String errorMsg = "Component " + componentId + " of study " + studyId
				+ " is already finished or failed.";
		return errorMsg;
	}

	public static String componentAlreadyStarted(Long studyId, Long componentId) {
		String errorMsg = "Component " + componentId + " of study " + studyId
				+ " was already started.";
		return errorMsg;
	}

	public static String componentNeverStarted(Long studyId, Long componentId) {
		String errorMsg = "Component " + componentId + " of study " + studyId
				+ " was never started.";
		return errorMsg;
	}

	public static String submittedDataUnknownFormat(Long studyId,
			Long componentId) {
		String errorMsg = "Unknown format of submitted data for component "
				+ componentId + " of study " + studyId + ".";
		return errorMsg;
	}

	public static String noMoreComponents() {
		String errorMsg = "There aren't any more components in this study.";
		return errorMsg;
	}

	public static String noUserLoggedIn() {
		String errorMsg = "No user logged in.";
		return errorMsg;
	}

	public static String userNotExists() {
		String errorMsg = "User doesn't exists.";
		return errorMsg;
	}

	public static String noMechArgTry() {
		String errorMsg = "This study or component was never started from within MechArg.";
		return errorMsg;
	}

	public static String noMechArgStudyTry() {
		String errorMsg = "This study was never started from within MechArg.";
		return errorMsg;
	}

	public static String assignmentIdNotSpecified() {
		String errorMsg = "No assignment ID specified in query parameters.";
		return errorMsg;
	}

	public static String workerNotInQueryParameter(String mtWorkerId) {
		String errorMsg = "MTurk's workerId is missing in the query parameters.";
		return errorMsg;
	}

	public static String workerNotFromMTurk(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a MTurk worker.";
		return errorMsg;
	}

	public static String userNotExist(String email) {
		String errorMsg = "An user with email " + email + " doesn't exist.";
		return errorMsg;
	}

	public static String componentNotExist(Long componentId) {
		String errorMsg = "An component with ID " + componentId
				+ " doesn't exist.";
		return errorMsg;
	}

	public static String componentResultNotExist(Long componentResultId) {
		String errorMsg = "An component result with ID " + componentResultId
				+ " doesn't exist.";
		return errorMsg;
	}

	public static String studyResultNotExist(Long studyResultId) {
		String errorMsg = "A study result with ID " + studyResultId
				+ " doesn't exist.";
		return errorMsg;
	}

	public static String notMember(String username, String email, Long studyId,
			String studyTitle) {
		String errorMsg = username + " (" + email + ") isn't member of study "
				+ studyId + " \"" + studyTitle + "\".";
		return errorMsg;
	}

	public static String removeMAWorker(Long workerId, String username,
			String email) {
		String errorMsg = "Worker (ID: " + workerId + ") is a worker of the "
				+ "Mechanical Argentinian, associated to the user "
				+ username + " (" + email + ") and can't be deleted.";
		return errorMsg;
	}

	public static String urlViewEmpty(Long componentId) {
		String errorMsg = "Component " + componentId + "'s URL field is empty.";
		return errorMsg;
	}

	public static String studyAtLeastOneMember() {
		String errorMsg = "An study should have at least one member.";
		return errorMsg;
	}

	public static String mustBeLoggedInAsUser(UserModel user) {
		return "You must be logged in as " + user.toString()
				+ " to update this user.";
	}

}
