package services.publix;

import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import models.common.*;
import models.common.StudyResult.StudyState;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.MTWorker;
import models.common.workers.PersonalSingleWorker;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ResultCreator.
 */
public class ResultCreatorTest {

    private ComponentResultDao componentResultDao;
    private StudyResultDao studyResultDao;
    private WorkerDao workerDao;

    private ResultCreator resultCreator;

    @Before
    public void setup() {
        componentResultDao = mock(ComponentResultDao.class);
        studyResultDao = mock(StudyResultDao.class);
        workerDao = mock(WorkerDao.class);
        resultCreator = new ResultCreator(componentResultDao, studyResultDao, workerDao);
    }

    @Test
    public void createStudyResult_setsPRE_forPreviewWorkerAndAllowPreview() {
        // Study and batch allowing preview
        Study study = new Study();
        study.setAllowPreview(true);
        Batch batch = new Batch();
        batch.setStudy(study);
        // Preview worker (PersonalSingle and GeneralSingle are considered preview workers)
        PersonalSingleWorker worker = new PersonalSingleWorker();
        worker.setId(100L);
        StudyLink studyLink = new StudyLink(batch, worker);

        StudyResult sr = resultCreator.createStudyResult(studyLink, worker);

        assertNotNull(sr);
        assertEquals(study, sr.getStudy());
        assertEquals(StudyState.PRE, sr.getStudyState());
        assertTrue(worker.getStudyResultList().contains(sr));
        verify(studyResultDao).create(sr);
        verify(workerDao).update(worker);
        verifyNoMoreInteractions(studyResultDao, workerDao, componentResultDao);
    }

    @Test
    public void createStudyResult_setsSTARTED_whenNotPreviewOrNotAllowed() {
        // Case 1: Not a preview worker
        Study study1 = new Study();
        study1.setAllowPreview(true); // allow preview but worker is not preview
        Batch batch1 = new Batch();
        batch1.setStudy(study1);
        MTWorker mtWorker = new MTWorker();
        mtWorker.setId(101L);
        StudyLink link1 = new StudyLink(batch1, mtWorker);

        StudyResult sr1 = resultCreator.createStudyResult(link1, mtWorker);
        assertEquals(StudyState.STARTED, sr1.getStudyState());

        // Case 2: Preview worker but study doesn't allow preview
        Study study2 = new Study();
        study2.setAllowPreview(false);
        Batch batch2 = new Batch();
        batch2.setStudy(study2);
        GeneralSingleWorker previewWorker = new GeneralSingleWorker();
        previewWorker.setId(102L);
        StudyLink link2 = new StudyLink(batch2, previewWorker);

        StudyResult sr2 = resultCreator.createStudyResult(link2, previewWorker);
        assertEquals(StudyState.STARTED, sr2.getStudyState());

        // DAO interactions happened for both calls
        verify(studyResultDao, times(2)).create(any(StudyResult.class));
        verify(workerDao).update(mtWorker);
        verify(workerDao).update(previewWorker);
        verifyNoMoreInteractions(studyResultDao, workerDao, componentResultDao);
    }

    @Test
    public void createComponentResult_persistsAndLinks() {
        // Prepare study and component
        Study study = new Study();
        Batch batch = new Batch();
        batch.setStudy(study);
        PersonalSingleWorker worker = new PersonalSingleWorker();
        worker.setId(103L);
        StudyLink studyLink = new StudyLink(batch, worker);
        StudyResult studyResult = resultCreator.createStudyResult(studyLink, worker);

        Component component = new Component();
        component.setStudy(study);

        ComponentResult componentResult = resultCreator.createComponentResult(studyResult, component);

        assertNotNull(componentResult);
        assertEquals(component, componentResult.getComponent());
        assertEquals(studyResult, componentResult.getStudyResult());
        assertTrue(studyResult.getComponentResultList().contains(componentResult));

        verify(componentResultDao).create(componentResult);
        verify(studyResultDao, atLeastOnce()).create(any(StudyResult.class));
        verify(studyResultDao).update(studyResult);
        verify(workerDao).update(worker);
        verifyNoMoreInteractions(componentResultDao, studyResultDao, workerDao);
    }
}
