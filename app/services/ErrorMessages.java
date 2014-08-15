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
	
	public static String noPreviewAvailable(Long studyId) {
		String errorMsg = "No preview available for study " + studyId + ".";
		return errorMsg;
	}

	public static String noWorkerIdInSession() {
		String errorMsg = "No worker id in session. Was the study started?";
		return errorMsg;
	}

	public static String workerNotExist(Long workerId) {
		return workerNotExist(String.valueOf(workerId));
	}

	public static String workerNotExist(String workerIdStr) {
		String errorMsg = "A worker with id " + workerIdStr + " doesn't exist.";
		return errorMsg;
	}

	public static String studyNotExist(Long studyId) {
		String errorMsg = "An study with id " + studyId + " doesn't exist.";
		return errorMsg;
	}

	public static String studyHasNoComponents(Long studyId) {
		String errorMsg = "The study with id " + studyId
				+ " has no components.";
		return errorMsg;
	}

	public static String componentNotExist(Long studyId, Long componentId) {
		String errorMsg = "An component with id " + componentId + " of study "
				+ studyId + " doesn't exist.";
		return errorMsg;
	}

	public static String componentNotBelongToStudy(Long studyId, Long componentId) {
		String errorMsg = "There is no study with id " + studyId
				+ " that has a component with id " + componentId + ".";
		return errorMsg;
	}

	public static String componentNotAllowedToReload(Long studyId, Long componentId) {
		String errorMsg = "It's not allowed to reload this component (id: "
				+ componentId + "). Unfortunately it is neccessary to finish "
				+ "this study (id: " + studyId + ") at this point.";
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

	public static String submittedDataUnknownFormat(Long studyId, Long componentId) {
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
		String errorMsg = "No assignment id specified in query parameters.";
		return errorMsg;
	}

	public static String workerNotInQueryParameter(String mtWorkerId) {
		String errorMsg = "MTurk's workerId is missing in the query parameters.";
		return errorMsg;
	}

	public static String workerNotFromMTurk(Long workerId) {
		String errorMsg = "The worker with id " + workerId
				+ " isn't a MTurk worker.";
		return errorMsg;
	}
	
	public static String userNotExist(String email) {
		String errorMsg = "An user with email " + email + " doesn't exist.";
		return errorMsg;
	}
	
	public static String componentNotExist(Long componentId) {
		String errorMsg = "An component with id " + componentId
				+ " doesn't exist.";
		return errorMsg;
	}
	
	public static String notMember(String username, String email, Long studyId,
			String studyTitle) {
		String errorMsg = username + " (" + email + ") isn't member of study "
				+ studyId + " \"" + studyTitle + "\".";
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
