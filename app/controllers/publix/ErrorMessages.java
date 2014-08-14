package controllers.publix;

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
	
	public String noPreviewAvailable(Long studyId) {
		String errorMsg = "No preview available for study " + studyId + ".";
		return errorMsg;
	}

	public String noWorkerIdInSession() {
		String errorMsg = "No worker id in session. Was the study started?";
		return errorMsg;
	}

	public String workerNotExist(Long workerId) {
		return workerNotExist(String.valueOf(workerId));
	}

	public String workerNotExist(String workerIdStr) {
		String errorMsg = "A worker with id " + workerIdStr + " doesn't exist.";
		return errorMsg;
	}

	public String studyNotExist(Long studyId) {
		String errorMsg = "An study with id " + studyId + " doesn't exist.";
		return errorMsg;
	}

	public String studyHasNoComponents(Long studyId) {
		String errorMsg = "The study with id " + studyId
				+ " has no components.";
		return errorMsg;
	}

	public String componentNotExist(Long studyId, Long componentId) {
		String errorMsg = "An component with id " + componentId + " of study "
				+ studyId + " doesn't exist.";
		return errorMsg;
	}

	public String componentNotBelongToStudy(Long studyId, Long componentId) {
		String errorMsg = "There is no study with id " + studyId
				+ " that has a component with id " + componentId + ".";
		return errorMsg;
	}

	public String componentNotAllowedToReload(Long studyId, Long componentId) {
		String errorMsg = "It's not allowed to reload this component (id: "
				+ componentId + "). Unfortunately it is neccessary to finish "
				+ "this study (id: " + studyId + ") at this point.";
		return errorMsg;
	}

	public String componentAlreadyFinishedOrFailed(Long studyId,
			Long componentId) {
		String errorMsg = "Component " + componentId + " of study " + studyId
				+ " is already finished or failed.";
		return errorMsg;
	}

	public String componentAlreadyStarted(Long studyId, Long componentId) {
		String errorMsg = "Component " + componentId + " of study " + studyId
				+ " was already started.";
		return errorMsg;
	}
	
	public String componentNeverStarted(Long studyId, Long componentId) {
		String errorMsg = "Component " + componentId + " of study " + studyId
				+ " was never started.";
		return errorMsg;
	}

	public String submittedDataUnknownFormat(Long studyId, Long componentId) {
		String errorMsg = "Unknown format of submitted data for component "
				+ componentId + " of study " + studyId + ".";
		return errorMsg;
	}

	public String noMoreComponents() {
		String errorMsg = "There aren't any more components in this study.";
		return errorMsg;
	}

}
