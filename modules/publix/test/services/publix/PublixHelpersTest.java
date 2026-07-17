package services.publix;

import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.MTWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for PublixHelpers.
 */
public class PublixHelpersTest {

    @Test
    public void studyResultDone_trueForFinishedAbortedFail_falseOtherwise() {
        // True cases
        assertTrue(PublixHelpers.studyResultDone(studyResultWithState(StudyState.FINISHED)));
        assertTrue(PublixHelpers.studyResultDone(studyResultWithState(StudyState.ABORTED)));
        assertTrue(PublixHelpers.studyResultDone(studyResultWithState(StudyState.FAIL)));

        // False cases
        assertFalse(PublixHelpers.studyResultDone(studyResultWithState(StudyState.PRE)));
        assertFalse(PublixHelpers.studyResultDone(studyResultWithState(StudyState.STARTED)));
        assertFalse(PublixHelpers.studyResultDone(studyResultWithState(StudyState.DATA_RETRIEVED)));
    }

    @Test
    public void componentResultDone_trueForFinishedAbortedFailReloaded_falseOtherwise() {
        // True cases
        assertTrue(PublixHelpers.componentResultDone(componentResultWithState(ComponentState.FINISHED)));
        assertTrue(PublixHelpers.componentResultDone(componentResultWithState(ComponentState.ABORTED)));
        assertTrue(PublixHelpers.componentResultDone(componentResultWithState(ComponentState.FAIL)));
        assertTrue(PublixHelpers.componentResultDone(componentResultWithState(ComponentState.RELOADED)));

        // False cases
        assertFalse(PublixHelpers.componentResultDone(componentResultWithState(ComponentState.STARTED)));
        assertFalse(PublixHelpers.componentResultDone(componentResultWithState(ComponentState.DATA_RETRIEVED)));
    }

    @Test
    public void isPreviewWorker_trueForPersonalSingleAndGeneralSingle_falseOtherwise() {
        Worker personal = new PersonalSingleWorker();
        Worker general = new GeneralSingleWorker();
        Worker mt = new MTWorker();

        assertTrue(PublixHelpers.isPreviewWorker(personal));
        assertTrue(PublixHelpers.isPreviewWorker(general));
        assertFalse(PublixHelpers.isPreviewWorker(mt));
    }

    private static StudyResult studyResultWithState(StudyState state) {
        StudyResult sr = new StudyResult();
        sr.setStudyState(state);
        return sr;
    }

    private static ComponentResult componentResultWithState(ComponentState state) {
        ComponentResult cr = new ComponentResult();
        cr.setComponentState(state);
        return cr;
    }
}
