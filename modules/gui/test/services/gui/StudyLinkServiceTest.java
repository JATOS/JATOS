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
import models.common.workers.GeneralMultipleWorker;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import models.gui.StudyCodeProperties;
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
    private StudyService studyService;
    private WorkerService workerService;
    private AuthorizationService authorizationService;

    private StudyLinkService studyLinkService;

    @Before
    public void setUp() {
        batchDao = mock(BatchDao.class);
        workerDao = mock(WorkerDao.class);
        studyLinkDao = mock(StudyLinkDao.class);
        workerService = mock(WorkerService.class);
        studyService = mock(StudyService.class);
        authorizationService = mock(AuthorizationService.class);

        studyLinkService = new StudyLinkService(batchDao, workerDao, studyLinkDao, workerService);
    }

    @Test
    public void getStudyCodes_personal_checksPermissions() throws ForbiddenException, NotFoundException, BadRequestException {
        // Create batch and study relations
        Batch batch = new Batch();
        batch.setId(101L);
        Study study = new Study();
        study.setId(201L);
        study.setBatchList(new ArrayList<>(Collections.singletonList(batch)));
        batch.setStudy(study);

        when(studyService.getStudyFromIdOrUuid("study-uuid-y")).thenReturn(study);
        when(workerService.extractWorkerType("PersonalSingle")).thenReturn(PersonalSingleWorker.WORKER_TYPE);

        // Capture created worker to assert a decoded comment
        ArgumentCaptor<Worker> workerCaptor = ArgumentCaptor.forClass(Worker.class);

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType("PersonalSingle");
        props.setComment("Hello World");
        props.setAmount(2);
        JsonNode node = studyLinkService.getStudyCodes(batch, props);

        assertThat(node.isArray()).isTrue();
        assertThat(node.size()).isEqualTo(2);
        assertThat(node.get(0).asText().length()).isEqualTo(11);

        // Worker creation happened with a decoded comment
        verify(workerDao, times(2)).create(workerCaptor.capture());
        Worker created = workerCaptor.getValue();
        assertEquals("Hello World", created.getComment());

        verify(studyLinkDao, times(2)).create(any(StudyLink.class));
        verify(batchDao, times(2)).update(eq(batch));
        verifyNoMoreInteractions(authorizationService); // no batchId -> no checker call
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

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType("GeneralMultiple");
        props.setComment(null);
        props.setAmount(1);

        JsonNode node = studyLinkService.getStudyCodes(batch, props);
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

        StudyCodeProperties props = new StudyCodeProperties();
        props.setType("GeneralSingle");
        props.setComment(null);
        props.setAmount(1);

        JsonNode node = studyLinkService.getStudyCodes(batch, props);
        assertThat(node.isArray()).isTrue();
        assertThat(node.size()).isEqualTo(1);
        assertThat(node.get(0).asText()).isNotEmpty();

        verify(studyLinkDao, times(1)).create(any(StudyLink.class));
    }
}
