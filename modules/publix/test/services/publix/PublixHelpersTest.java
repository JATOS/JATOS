package services.publix;

import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.MTWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for PublixHelpers.
 */
public class PublixHelpersTest {

    @Test
    public void studyDone_trueForFinishedAbortedFail_falseOtherwise() {
        // True cases
        assertTrue(PublixHelpers.studyDone(studyResultWithState(StudyState.FINISHED)));
        assertTrue(PublixHelpers.studyDone(studyResultWithState(StudyState.ABORTED)));
        assertTrue(PublixHelpers.studyDone(studyResultWithState(StudyState.FAIL)));

        // False cases
        assertFalse(PublixHelpers.studyDone(studyResultWithState(StudyState.PRE)));
        assertFalse(PublixHelpers.studyDone(studyResultWithState(StudyState.STARTED)));
        assertFalse(PublixHelpers.studyDone(studyResultWithState(StudyState.DATA_RETRIEVED)));
    }

    @Test
    public void componentDone_trueForFinishedAbortedFailReloaded_falseOtherwise() {
        // True cases
        assertTrue(PublixHelpers.componentDone(componentResultWithState(ComponentState.FINISHED)));
        assertTrue(PublixHelpers.componentDone(componentResultWithState(ComponentState.ABORTED)));
        assertTrue(PublixHelpers.componentDone(componentResultWithState(ComponentState.FAIL)));
        assertTrue(PublixHelpers.componentDone(componentResultWithState(ComponentState.RELOADED)));

        // False cases
        assertFalse(PublixHelpers.componentDone(componentResultWithState(ComponentState.STARTED)));
        assertFalse(PublixHelpers.componentDone(componentResultWithState(ComponentState.DATA_RETRIEVED)));
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

    @Test
    public void finishedStudyAlready_trueIfWorkerHasFinishedStudyResultForGivenStudy() {
        Study studyA = new Study();
        studyA.setId(1L);
        Study studyB = new Study();
        studyB.setId(2L);

        StudyResult sr1 = new StudyResult();
        sr1.setStudy(studyA);
        sr1.setStudyState(StudyState.STARTED);

        StudyResult sr2 = new StudyResult();
        sr2.setStudy(studyB);
        sr2.setStudyState(StudyState.FINISHED);

        StudyResult sr3 = new StudyResult();
        sr3.setStudy(studyA);
        sr3.setStudyState(StudyState.FAIL);

        Worker worker = new MTWorker();
        worker.setStudyResultList(Arrays.asList(sr1, sr2, sr3));

        // Should be true for studyA (sr3 is done) and studyB (sr2 is done)
        assertTrue(PublixHelpers.finishedStudyAlready(worker, studyA));
        assertTrue(PublixHelpers.finishedStudyAlready(worker, studyB));

        // Another study with no finished results -> false
        Study studyC = new Study();
        studyC.setId(3L);
        assertFalse(PublixHelpers.finishedStudyAlready(worker, studyC));
    }

    @Test
    public void finishedStudyAlready_falseIfOnlyUnfinishedResults() {
        Study study = new Study();
        StudyResult sr1 = new StudyResult();
        sr1.setStudy(study);
        sr1.setStudyState(StudyState.STARTED);

        StudyResult sr2 = new StudyResult();
        sr2.setStudy(study);
        sr2.setStudyState(StudyState.PRE);

        Worker worker = new GeneralSingleWorker();
        worker.setStudyResultList(Arrays.asList(sr1, sr2));

        assertFalse(PublixHelpers.finishedStudyAlready(worker, study));
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
