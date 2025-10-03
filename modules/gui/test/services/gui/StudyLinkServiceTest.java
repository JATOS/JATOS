package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.BatchDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import models.common.Batch;
import models.common.Study;
import models.common.StudyLink;
import models.common.workers.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StudyLinkService.
 *
 * @author Kristian Lange
 */
public class StudyLinkServiceTest {

    private BatchDao batchDao;
    private WorkerDao workerDao;
    private StudyLinkDao studyLinkDao;
    private WorkerService workerService;
    private StudyService studyService;
    private Checker checker;

    private StudyLinkService studyLinkService;

    @Before
    public void setUp() {
        batchDao = mock(BatchDao.class);
        workerDao = mock(WorkerDao.class);
        studyLinkDao = mock(StudyLinkDao.class);
        workerService = mock(WorkerService.class);
        studyService = mock(StudyService.class);
        checker = mock(Checker.class);

        studyLinkService = new StudyLinkService(batchDao, workerDao, studyLinkDao, workerService, studyService, checker);
    }

    @Test
    public void getStudyCodes_personal_defaultBatch_andCommentDecoded() throws Exception {
        // Study with default batch
        Batch defaultBatch = new Batch();
        defaultBatch.setId(100L);
        Study study = new Study();
        study.setId(200L);
        study.setBatchList(new ArrayList<>(Collections.singletonList(defaultBatch)));

        when(studyService.getStudyFromIdOrUuid("study-uuid-x")).thenReturn(study);
        when(workerService.extractWorkerType("PersonalMultiple")).thenReturn(PersonalMultipleWorker.WORKER_TYPE);

        // Capture created worker to assert decoded comment
        ArgumentCaptor<Worker> workerCaptor = ArgumentCaptor.forClass(Worker.class);

        String encodedComment = "Hello%20World%2Bplus"; // -> "Hello World+plus"
        JsonNode node = studyLinkService.getStudyCodes("study-uuid-x", scala.Option$.MODULE$.empty(),
                "PersonalMultiple", encodedComment, null);

        // Returned JSON should have one code (amount null -> 1)
        assertThat(node.isArray()).isTrue();
        assertThat(node.size()).isEqualTo(1);
        assertThat(node.get(0).asText()).isNotEmpty();

        // Worker creation happened with decoded comment
        verify(workerDao).create(workerCaptor.capture());
        Worker created = workerCaptor.getValue();
        assertEquals("Hello World+plus", created.getComment());

        verify(studyLinkDao, times(1)).create(any(StudyLink.class));
        verify(batchDao, times(1)).update(eq(defaultBatch));
        verifyNoMoreInteractions(checker); // no batchId -> no checker call
    }

    @Test
    public void getStudyCodes_personal_withBatchId_checksPermissions() throws ForbiddenException, NotFoundException, BadRequestException {
        // Create batch and study relations
        Batch batch = new Batch();
        batch.setId(101L);
        Study study = new Study();
        study.setId(201L);
        study.setBatchList(new ArrayList<>(Collections.singletonList(batch)));
        batch.setStudy(study);

        when(studyService.getStudyFromIdOrUuid("study-uuid-y")).thenReturn(study);
        when(batchDao.findById(101L)).thenReturn(batch);
        when(workerService.extractWorkerType("PersonalSingle")).thenReturn(PersonalSingleWorker.WORKER_TYPE);

        scala.Option<Long> batchId = new scala.Some<>(101L);
        JsonNode node = studyLinkService.getStudyCodes("study-uuid-y", batchId, "PersonalSingle", null, 2);

        assertThat(node.isArray()).isTrue();
        assertThat(node.size()).isEqualTo(2);
        assertThat(node.get(0).asText().length()).isEqualTo(11);

        // Checker called with batch and its study and the id
        verify(checker, times(1)).checkStandardForBatch(eq(batch), eq(study), eq(101L));
    }

    @Test
    public void getStudyCodes_general_existingLinkReturned() throws Exception {
        Batch batch = new Batch();
        batch.setId(300L);
        Study study = new Study();
        study.setId(400L);
        study.setBatchList(new ArrayList<>(Collections.singletonList(batch)));

        when(studyService.getStudyFromIdOrUuid("study-uuid-z")).thenReturn(study);
        when(workerService.extractWorkerType("GeneralMultiple")).thenReturn(GeneralMultipleWorker.WORKER_TYPE);

        StudyLink existing = new StudyLink(batch, GeneralMultipleWorker.WORKER_TYPE);
        when(studyLinkDao.findFirstByBatchAndWorkerType(batch, GeneralMultipleWorker.WORKER_TYPE))
                .thenReturn(Optional.of(existing));

        JsonNode node = studyLinkService.getStudyCodes("study-uuid-z", scala.Option$.MODULE$.empty(),
                "GeneralMultiple", null, null);
        assertThat(node.isArray()).isTrue();
        assertThat(node.size()).isEqualTo(1);
        assertThat(node.get(0).asText()).isEqualTo(existing.getStudyCode());

        verify(studyLinkDao, never()).create(any(StudyLink.class));
    }

    @Test
    public void getStudyCodes_general_createsIfMissing() throws Exception {
        Batch batch = new Batch();
        batch.setId(301L);
        Study study = new Study();
        study.setId(401L);
        study.setBatchList(new ArrayList<>(Collections.singletonList(batch)));

        when(studyService.getStudyFromIdOrUuid("study-uuid-a")).thenReturn(study);
        when(workerService.extractWorkerType("GeneralSingle")).thenReturn(GeneralSingleWorker.WORKER_TYPE);

        when(studyLinkDao.findFirstByBatchAndWorkerType(batch, GeneralSingleWorker.WORKER_TYPE))
                .thenReturn(Optional.empty());

        // Ensure create returns the same instance passed in so we can assert its code
        when(studyLinkDao.create(any(StudyLink.class))).thenAnswer(inv -> inv.getArgument(0));

        JsonNode node = studyLinkService.getStudyCodes("study-uuid-a", scala.Option$.MODULE$.empty(),
                "GeneralSingle", null, null);
        assertThat(node.isArray()).isTrue();
        assertThat(node.size()).isEqualTo(1);
        assertThat(node.get(0).asText()).isNotEmpty();

        verify(studyLinkDao, times(1)).create(any(StudyLink.class));
    }
}
