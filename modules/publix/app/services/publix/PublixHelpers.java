package services.publix;

import java.util.List;

import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.Worker;

/**
 * @author Kristian Lange
 */
public abstract class PublixHelpers {

	/**
	 * Checks if the worker finished this study already. 'Finished' includes
	 * failed and aborted.
	 */
	public static boolean finishedStudyAlready(Worker worker, Study study) {
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().equals(study)
					&& studyDone(studyResult)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the worker ever did this study (independent of the study
	 * result's state).
	 */
	public static boolean didStudyAlready(Worker worker, Study study) {
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().equals(study)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True if StudyResult's state is in FINISHED or ABORTED or FAIL. False
	 * otherwise.
	 */
	public static boolean studyDone(StudyResult studyResult) {
		StudyState state = studyResult.getStudyState();
		return state == StudyState.FINISHED || state == StudyState.ABORTED
				|| state == StudyState.FAIL;
	}

	/**
	 * Returns true only if every single StudyResult's state from the given list
	 * is in FINISHED or ABORTED or FAIL. False otherwise.
	 */
	public boolean allStudiesDone(List<StudyResult> studyResultList) {
		for (StudyResult studyResult : studyResultList) {
			if (!PublixHelpers.studyDone(studyResult)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * True if ComponentResult's state is in FINISHED or ABORTED or FAIL or
	 * RELOADED. False otherwise.
	 */
	public static boolean componentDone(ComponentResult componentResult) {
		ComponentState state = componentResult.getComponentState();
		return ComponentState.FINISHED == state
				|| ComponentState.ABORTED == state
				|| ComponentState.FAIL == state
				|| ComponentState.RELOADED == state;
	}

}
