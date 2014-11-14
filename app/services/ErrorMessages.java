package services;

import models.UserModel;
import models.workers.Worker;

/**
 * Helper class with methods that generate error strings.
 * 
 * @author Kristian Lange
 */
public abstract class ErrorMessages<T extends Worker> {

	public abstract String workerNeverStartedStudy(T worker, Long studyId);

	public abstract String workerFinishedStudyAlready(T worker, Long studyId);

	public abstract String workerNeverDidStudy(T worker, Long studyId);

	public abstract String workerNotAllowedStudy(T worker, Long studyId);

	// General
	public static final String MISSING_TITLE = "Missing title";
	public static final String MISSING_FILE_PATH = "Missing file path";
	public static final String MISSING_NAME = "Missing name";
	public static final String MISSING_EMAIL = "Missing email";
	public static final String NO_USER_LOGGED_IN = "No user logged in";
	public static final String MISSING_DIRNAME = "Missing directory name";
	public static final String INVALID_DIR_NAME = "Invalid directory name";

	// Edit HTML forms
	public static final String INVALID_JSON_FORMAT = "Problems deserializing JSON data string: invalid JSON format";
	public static final String NO_HTML_ALLOWED = "No HTML allowed";
	public static final String NOT_A_VALID_PATH_YOU_CAN_LEAVE_IT_EMPTY = "Not a valid path (\"/\" as File separator; You can leave it empty)";
	public static final String JSON_DATA_MISSING_OR_INVALID_JSON_FORMAT = "JSON data missing or invalid JSON format";
	public static final String STUDY_AT_LEAST_ONE_MEMBER = "An study should have at least one member.";
	public static final String STUDY_IS_LOCKED = "Study is locked. It's not possible to edit.";
	
	// User
	public static final String WRONG_OLD_PASSWORD = "Wrong old password";
	public static final String PASSWORDS_DONT_MATCH = "Passwords don't match";
	public static final String PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS = "Passwords shouldn't be empty strings";
	public static final String THIS_EMAIL_IS_ALREADY_REGISTERED = "This email address is already registered.";

	// Export / import
	public static final String NO_COMPONENT_UPLOAD = "Uploaded file isn't intended for components";
	public static final String NO_STUDY_UPLOAD = "Uploaded file isn't intended for studies";
	public static final String MORE_THAN_ONE_DIR_IN_ZIP = "There are more than one directory in the ZIP file.";
	public static final String COULDNT_READ_JSON = "Couldn't read JSON string";
	public static final String COULDNT_READ_FILE = "Couldn't read file";
	public static final String FILE_MISSING = "File missing";
	public static final String FILE_NOT_EXIST_OR_DIR = "File doesn't exists or is a directory";
	public static final String IMPORT_OF_STUDY_FAILED = "Import of study failed";
	public static final String NO_RESULTS_SELECTED = "No results selected";
	public static final String PROBLEM_GENERATING_JSON_DATA = "Problem generate JSON data";

	// Publix
	public static final String COMPONENT_INVALID = "Component is invalid";
	public static final String NO_MORE_COMPONENTS_IN_STUDY = "There aren't any more components in this study.";
	public static final String STUDY_OR_COMPONENT_NEVER_STARTED_FROM_MECHARG = "This study or component was never started from within MechArg.";
	public static final String STUDY_NEVER_STARTED_FROM_MECHARG = "This study was never started from within MechArg.";
	public static final String STUDY_NEVER_FINSHED = "Study never finished, but new study started by the same worker";
	public static final String NO_ASSIGNMENT_ID = "No assignment ID specified in query parameters";
	public static final String NO_MTURK_WORKERID = "MTurk's workerId is missing in the query parameters.";
	public static final String NO_WORKERID_IN_SESSION = "No worker ID in session. Was the study started?";
	public static final String COMPONENTS_POSITION_NOT_NULL = "Component's positions can't be null.";

	public static String noPreviewAvailable(Long studyId) {
		String errorMsg = "No preview available for study " + studyId + ".";
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

	public static String studyHasNoActiveComponents(Long studyId) {
		String errorMsg = "The study with ID " + studyId
				+ " has no active components.";
		return errorMsg;
	}

	public static String componentExportFailure(Long componentId) {
		String errorMsg = "Failure during export of component with ID "
				+ componentId + ".";
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

	public static String componentNotExist(Long studyId, Long componentId) {
		String errorMsg = "An component with ID " + componentId + " of study "
				+ studyId + " doesn't exist.";
		return errorMsg;
	}

	public static String noComponentAtPosition(Long studyId, Integer position) {
		String errorMsg = "There is no component at position " + position
				+ " in study " + studyId + ".";
		return errorMsg;
	}

	public static String componentResultNotExist(Long componentResultId) {
		String errorMsg = "An component result with ID " + componentResultId
				+ " doesn't exist.";
		return errorMsg;
	}

	public static String componentResultNotExist(String componentResultIdStr) {
		String errorMsg = "An component result with ID \""
				+ componentResultIdStr + "\" doesn't exist.";
		return errorMsg;
	}

	public static String resultNotExist(String resultIdStr) {
		String errorMsg = "An result with ID \"" + resultIdStr
				+ "\" doesn't exist.";
		return errorMsg;
	}

	public static String studyResultNotFromWorker(Long studyResultId,
			Long workerId) {
		String errorMsg = "Study result (ID " + studyResultId
				+ ") doesn't belong to worker (ID " + workerId + ")";
		return errorMsg;
	}

	public static String studyResultNotExist(Long studyResultId) {
		String errorMsg = "A study result with ID " + studyResultId
				+ " doesn't exist.";
		return errorMsg;
	}

	public static String studyResultNotExist(String studyResultIdStr) {
		String errorMsg = "A study result with ID \"" + studyResultIdStr
				+ "\" doesn't exist.";
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
				+ "Mechanical Argentine, associated to the user " + username
				+ " (" + email + ") and can't be deleted.";
		return errorMsg;
	}

	public static String urlViewEmpty(Long componentId) {
		String errorMsg = "Component " + componentId + "'s URL field is empty.";
		return errorMsg;
	}

	public static String studyExportFailure(Long studyId) {
		String errorMsg = "Export of study with ID " + studyId + " failed.";
		return errorMsg;
	}

	public static String mustBeLoggedInAsUser(UserModel user) {
		return "You must be logged in as " + user.toString()
				+ " to see the profile of this user.";
	}

	public static String studyLocked(Long studyId) {
		return "Study " + studyId
				+ " is locked. Unlock it if you want to make changes.";
	}

	public static String couldntGeneratePathToFileOrDir(String fileStr) {
		return "Couldn't generate path to file or directory \"" + fileStr
				+ "\".";
	}

	public static String couldntGenerateStudysDir(String path) {
		return "Couldn't generate path to study directory (" + path + ").";
	}

	public static String studysDirPathIsntDir(String dirName) {
		return "Study's directory path \""
				+ dirName + "\"doesn't lead to a directory.";
	}

	public static String clonedStudysDirNotCreatedBecauseExists(String dirName) {
		return "Cloned study's directory (" + dirName
				+ ") couldn't be created because it already exists.";
	}

	public static String studysDirNotCreatedBecauseExists(String dirName) {
		return "Study's directory (" + dirName
				+ ") couldn't be created because it already exists.";
	}

	public static String studysDirNotRenamed(String oldDirName,
			String newDirName) {
		return "Couldn't rename study's directory from \"" + oldDirName
				+ "\" to \"" + newDirName + "\".";
	}

	public static String fileNotRenamed(String oldFileName, String newFileName) {
		return "Couldn't rename file from \"" + oldFileName + "\" to \""
				+ newFileName + "\".";
	}

	public static String studysDirNotCreated(String dirName) {
		return "Couldn't create new study's directory (" + dirName + ").";
	}
	
	public static String fileNotUploaded(String fileName) {
		return "Couldn't upload file " + fileName + ".";
	}

}
