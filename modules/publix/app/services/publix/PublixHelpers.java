package services.publix;

import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;

/**
 * @author Kristian Lange
 */
public class PublixHelpers {

    /**
     * Checks if the worker finished this study already at least once. 'Finished' includes failed and aborted.
     */
    public static boolean finishedStudyAlready(Worker worker, Study study) {
        for (StudyResult studyResult : worker.getStudyResultList()) {
            if (studyResult.getStudy().equals(study) && studyDone(studyResult)) {
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

    public static boolean isPreviewWorker(Worker worker) {
        return worker instanceof PersonalSingleWorker
                || worker instanceof GeneralSingleWorker;
    }

}
