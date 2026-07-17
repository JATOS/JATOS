package services.gui;

import daos.common.BatchDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import exceptions.common.BadRequestException;
import models.common.Batch;
import models.common.StudyLink;
import models.common.workers.Worker;
import models.common.workers.WorkerType;
import models.gui.StudyCodeProperties;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import testutils.gui.JPAMocker;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StudyLinkService.
 */
public class StudyLinkServiceTest {

    private BatchDao batchDao;
    private WorkerDao workerDao;
    private StudyLinkDao studyLinkDao;
    private WorkerService workerService;

    private StudyLinkService studyLinkService;

    @Before
    public void setUp() {
        batchDao = Mockito.mock(BatchDao.class);
        workerDao = Mockito.mock(WorkerDao.class);
        studyLinkDao = Mockito.mock(StudyLinkDao.class);
        workerService = Mockito.mock(WorkerService.class);

        studyLinkService = new StudyLinkService(batchDao, workerDao, studyLinkDao, workerService);

        JPAMocker.mockDaoTransactions(batchDao, workerDao, studyLinkDao);
    }

    @Test
    public void getStudyCodes_personalSingle_returnsPersistedStudyCode() {
        Batch batch = new Batch();
        batch.setId(100L);

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType(WorkerType.PERSONAL_SINGLE);
        props.setAmount(1);
        props.setComment("participant comment");

        List<String> studyCodes = studyLinkService.getStudyCodes(batch, props);

        assertThat(studyCodes).hasSize(1);
        assertThat(studyCodes.get(0)).isNotEmpty();

        ArgumentCaptor<Worker> workerCaptor = ArgumentCaptor.forClass(Worker.class);
        verify(workerService).validateWorker(workerCaptor.capture());
        assertEquals("participant comment", workerCaptor.getValue().getComment());

        verify(workerDao).persist(workerCaptor.getValue());
        verify(batchDao).addWorkerToBatch(eq(batch.getId()), eq(workerCaptor.getValue().getId()));
        verify(studyLinkDao).persist(any(StudyLink.class));
    }

    @Test
    public void getStudyCodes_personalMultiple_returnsRequestedNumberOfStudyCodes() {
        Batch batch = new Batch();
        batch.setId(101L);

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType(WorkerType.PERSONAL_MULTIPLE);
        props.setAmount(3);
        props.setComment("comment");

        List<String> studyCodes = studyLinkService.getStudyCodes(batch, props);

        assertThat(studyCodes).hasSize(3);
        studyCodes.forEach(studyCode -> {
            assertThat(studyCode).isNotNull();
            assertThat(studyCode).isNotEmpty();
        });

        verify(workerService, times(3)).validateWorker(any(Worker.class));
        verify(workerDao, times(3)).persist(any(Worker.class));
        verify(batchDao, times(3)).addWorkerToBatch(eq(batch.getId()), any());
        verify(studyLinkDao, times(3)).persist(any(StudyLink.class));
    }

    @Test
    public void getStudyCodes_personal_returnsOneCodeForZeroAmount() {
        Batch batch = new Batch();
        batch.setId(102L);

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType(WorkerType.PERSONAL_SINGLE);
        props.setAmount(0);

        List<String> studyCodes = studyLinkService.getStudyCodes(batch, props);

        assertThat(studyCodes).hasSize(1);
        assertThat(studyCodes.get(0)).isNotEmpty();

        verify(workerDao).persist(any(Worker.class));
        verify(studyLinkDao).persist(any(StudyLink.class));
    }

    @Test
    public void getStudyCodes_general_returnsExistingStudyCode() {
        Batch batch = new Batch();
        batch.setId(200L);

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType(WorkerType.GENERAL_SINGLE);

        StudyLink existingStudyLink = new StudyLink(batch, WorkerType.GENERAL_SINGLE);
        when(studyLinkDao.findFirstByBatchAndWorkerType(batch, WorkerType.GENERAL_SINGLE))
                .thenReturn(Optional.of(existingStudyLink));

        List<String> studyCodes = studyLinkService.getStudyCodes(batch, props);

        assertThat(studyCodes).hasSize(1);
        assertEquals(existingStudyLink.getStudyCode(), studyCodes.get(0));

        verify(studyLinkDao).findFirstByBatchAndWorkerType(batch, WorkerType.GENERAL_SINGLE);
        verify(studyLinkDao, never()).persist(any(StudyLink.class));
        verifyNoInteractions(workerDao, batchDao, workerService);
    }

    @Test
    public void getStudyCodes_general_createsAndReturnsStudyCodeIfMissing() {
        Batch batch = new Batch();
        batch.setId(201L);

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType(WorkerType.GENERAL_MULTIPLE);

        when(studyLinkDao.findFirstByBatchAndWorkerType(batch, WorkerType.GENERAL_MULTIPLE))
                .thenReturn(Optional.empty());
        when(studyLinkDao.persist(any(StudyLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<String> studyCodes = studyLinkService.getStudyCodes(batch, props);

        assertThat(studyCodes).hasSize(1);
        assertThat(studyCodes.get(0)).isNotEmpty();

        ArgumentCaptor<StudyLink> studyLinkCaptor = ArgumentCaptor.forClass(StudyLink.class);
        verify(studyLinkDao).persist(studyLinkCaptor.capture());
        assertEquals(studyLinkCaptor.getValue().getStudyCode(), studyCodes.get(0));

        verifyNoInteractions(workerDao, batchDao, workerService);
    }

    @Test(expected = BadRequestException.class)
    public void getStudyCodes_unknownType_throwsBadRequestException() {
        Batch batch = new Batch();

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType(null);

        studyLinkService.getStudyCodes(batch, props);
    }
}
