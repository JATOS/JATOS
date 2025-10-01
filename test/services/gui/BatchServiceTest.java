package services.gui;

import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.NotFoundException;
import general.common.StudyLogger;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import models.gui.BatchProperties;
import models.gui.BatchSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchService.
 */
public class BatchServiceTest {

    private ResultRemover resultRemover;
    private BatchDao batchDao;
    private StudyDao studyDao;
    private WorkerDao workerDao;
    private GroupResultDao groupResultDao;
    private StudyLinkDao studyLinkDao;
    private StudyLogger studyLogger;

    private BatchService batchService;

    @Before
    public void setup() {
        resultRemover = Mockito.mock(ResultRemover.class);
        batchDao = Mockito.mock(BatchDao.class);
        studyDao = Mockito.mock(StudyDao.class);
        workerDao = Mockito.mock(WorkerDao.class);
        groupResultDao = Mockito.mock(GroupResultDao.class);
        studyLinkDao = Mockito.mock(StudyLinkDao.class);
        studyLogger = Mockito.mock(StudyLogger.class);
        batchService = new BatchService(resultRemover, batchDao, studyDao, workerDao, groupResultDao, studyLinkDao, studyLogger);
    }

    private Study studyWithOneUserAndDefaultBatch() {
        Study study = new Study();
        study.setId(1L);
        study.setTitle("Study");

        // Default batch at index 0
        Batch defaultBatch = new Batch();
        defaultBatch.setId(11L);
        defaultBatch.setUuid(UUID.randomUUID().toString());
        defaultBatch.setStudy(study);
        study.setBatchList(new ArrayList<>(Collections.singletonList(defaultBatch)));

        // One member with JatosWorker
        User user = new User("member", "Member", "m@example.org");
        JatosWorker jw = new JatosWorker(user);
        user.setWorker(jw);
        study.setUserList(new HashSet<>(Collections.singletonList(user)));
        return study;
    }

    @Test
    public void clone_copiesFields_andResetsSessionAndVersion() {
        // Given
        Batch original = new Batch();
        original.setUuid("orig-uuid");
        original.setTitle("Batch A");
        original.setActive(true);
        original.setMaxActiveMembers(3);
        original.setMaxTotalMembers(10);
        original.setMaxTotalWorkers(20);
        original.addAllowedWorkerType(JatosWorker.WORKER_TYPE);
        original.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
        original.setJsonData("{\"a\":1}");
        original.setBatchSessionData("{\"foo\":\"bar\"}");
        original.setBatchSessionVersion(5L);
        // add a worker to ensure worker list is copied
        Worker worker = new PersonalSingleWorker();
        original.addWorker(worker);

        // When
        Batch clone = batchService.clone(original);

        // Then - same general properties
        assertThat(clone.getTitle()).isEqualTo("Batch A");
        assertThat(clone.isActive()).isTrue();
        assertThat(clone.getMaxActiveMembers()).isEqualTo(3);
        assertThat(clone.getMaxTotalMembers()).isEqualTo(10);
        assertThat(clone.getMaxTotalWorkers()).isEqualTo(20);
        assertThat(clone.getAllowedWorkerTypes()).contains(JatosWorker.WORKER_TYPE, PersonalSingleWorker.WORKER_TYPE);
        assertThat(clone.getWorkerList()).contains(worker);
        assertThat(clone.getJsonData()).isEqualTo("{\"a\":1}");

        // new UUID and default session/version (not copied)
        assertThat(clone.getUuid()).isNotEqualTo("orig-uuid");
        assertThat(clone.getBatchSessionData()).isEqualTo("{}");
        assertThat(clone.getBatchSessionVersion()).isEqualTo(1L);
    }

    @Test
    public void createDefaultBatch_initializesFields_andAddsStudyUsers() {
        // Given
        Study study = studyWithOneUserAndDefaultBatch();

        // When
        Batch batch = batchService.createDefaultBatch(study);

        // Then
        assertThat(batch.getTitle()).isEqualTo(BatchProperties.DEFAULT_TITLE);
        assertThat(batch.getUuid()).isNotNull();
        assertThat(batch.getAllowedWorkerTypes()).contains(JatosWorker.WORKER_TYPE, PersonalSingleWorker.WORKER_TYPE);
        // All members' JatosWorkers added
        assertThat(batch.getWorkerList()).hasSize(1);
        assertThat(batch.getBatchSessionData()).isEqualTo("{}");
        assertThat(batch.getStudy()).isEqualTo(study);
    }

    @Test
    public void updateBatch_appliesProperties_andPersists() {
        // Given
        Batch batch = new Batch();
        BatchProperties props = new BatchProperties();
        props.setTitle("NewTitle");
        props.setActive(false);
        props.setMaxActiveMemberLimited(true);
        props.setMaxActiveMembers(7);
        props.setMaxTotalMemberLimited(true);
        props.setMaxTotalMembers(15);
        props.setMaxTotalWorkerLimited(true);
        props.setMaxTotalWorkers(30);
        props.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
        props.setComments("c");
        props.setJsonData("{x:1}");

        // When
        batchService.updateBatch(batch, props);

        // Then values applied
        assertThat(batch.getTitle()).isEqualTo("NewTitle");
        assertThat(batch.isActive()).isFalse();
        assertThat(batch.getMaxActiveMembers()).isEqualTo(7);
        assertThat(batch.getMaxTotalMembers()).isEqualTo(15);
        assertThat(batch.getMaxTotalWorkers()).isEqualTo(30);
        assertThat(batch.getAllowedWorkerTypes()).containsOnly(PersonalSingleWorker.WORKER_TYPE);
        assertThat(batch.getComments()).isEqualTo("c");
        assertThat(batch.getJsonData()).isEqualTo("{x:1}");

        verify(batchDao, times(1)).update(batch);
    }

    @Test
    public void bind_roundTrip_preservesLimitsAndAllowedWorkers() {
        Batch batch = new Batch();
        batch.setTitle("T");
        batch.setActive(true);
        batch.setMaxActiveMembers(1);
        batch.setMaxTotalMembers(2);
        batch.setMaxTotalWorkers(3);
        batch.addAllowedWorkerType(JatosWorker.WORKER_TYPE);
        batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
        batch.setComments("c");
        batch.setJsonData("{y:2}");

        BatchProperties props = batchService.bindToProperties(batch);
        assertThat(props.isMaxActiveMemberLimited()).isTrue();
        assertThat(props.isMaxTotalMemberLimited()).isTrue();
        assertThat(props.isMaxTotalWorkerLimited()).isTrue();
        assertThat(props.getAllowedWorkerTypes()).contains(JatosWorker.WORKER_TYPE, PersonalSingleWorker.WORKER_TYPE);

        Batch fromProps = batchService.bindToBatch(props);
        assertThat(fromProps.getMaxActiveMembers()).isEqualTo(1);
        assertThat(fromProps.getMaxTotalMembers()).isEqualTo(2);
        assertThat(fromProps.getMaxTotalWorkers()).isEqualTo(3);
        assertThat(fromProps.getAllowedWorkerTypes()).contains(JatosWorker.WORKER_TYPE, PersonalSingleWorker.WORKER_TYPE);
        assertThat(fromProps.getComments()).isEqualTo("c");
        assertThat(fromProps.getJsonData()).contains("{y:2}");
    }

    @Test
    public void bindToBatchSession_mapsFields() {
        Batch batch = new Batch();
        batch.setBatchSessionVersion(9L);
        batch.setBatchSessionData("{data}");

        BatchSession session = batchService.bindToBatchSession(batch);
        assertThat(session.getVersion()).isEqualTo(9L);
        assertThat(session.getData()).isEqualTo("{data}");
    }

    @Test
    public void updateBatchSession_nullOrVersionMismatch_returnsFalse() {
        // null batch
        when(batchDao.findById(1L)).thenReturn(null);
        BatchSession session = new BatchSession();
        session.setVersion(1L);
        session.setData("{}");
        assertThat(batchService.updateBatchSession(1L, session)).isFalse();

        // version mismatch
        Batch existing = new Batch();
        existing.setBatchSessionVersion(2L);
        when(batchDao.findById(2L)).thenReturn(existing);
        session.setVersion(1L);
        assertThat(batchService.updateBatchSession(2L, session)).isFalse();
    }

    @Test
    public void updateBatchSession_success_incrementsVersion_andNormalizesEmptyData() {
        Batch existing = new Batch();
        existing.setBatchSessionVersion(3L);
        existing.setBatchSessionData("{old}");
        when(batchDao.findById(5L)).thenReturn(existing);

        BatchSession session = new BatchSession();
        session.setVersion(3L);
        session.setData(""); // should become {}

        boolean res = batchService.updateBatchSession(5L, session);
        assertThat(res).isTrue();
        assertThat(existing.getBatchSessionVersion()).isEqualTo(4L);
        assertThat(existing.getBatchSessionData()).isEqualTo("{}");
        verify(batchDao).update(existing);
    }

    @Test
    public void fetchBatch_minusOne_returnsDefault_andMissingThrows() throws Exception {
        // Default
        Study study = studyWithOneUserAndDefaultBatch();
        Batch def = batchService.fetchBatch(-1L, study);
        assertThat(def).isEqualTo(study.getDefaultBatch());

        // by id
        Batch b = new Batch();
        when(batchDao.findById(99L)).thenReturn(b);
        assertThat(batchService.fetchBatch(99L, study)).isEqualTo(b);

        // missing
        when(batchDao.findById(100L)).thenReturn(null);
        try {
            batchService.fetchBatch(100L, study);
        } catch (NotFoundException e) {
            assertThat(e.getMessage()).contains("does not exist");
            return;
        }
        throw new AssertionError("Expected NotFoundException");
    }

    @Test
    public void remove_deletesResultsLinksGroups_andUpdatesOrRemovesWorkers() {
        // Study and batch
        Study study = new Study();
        study.setId(1L);
        Batch batch = new Batch();
        batch.setId(2L);
        batch.setStudy(study);
        study.setBatchList(new ArrayList<>(Collections.singletonList(batch)));

        // Group results to be removed
        when(groupResultDao.findAllByBatch(batch)).thenReturn(Collections.emptyList());

        // Workers in batch
        // 1) JatosWorker with no user (should be removed)
        JatosWorker jw = new JatosWorker();
        jw.setId(10L);
        jw.setUser(null);
        jw.setBatchList(new HashSet<>(Collections.singleton(batch)));
        batch.addWorker(jw);
        // 2) PersonalSingleWorker belonging only to this batch (should be removed)
        PersonalSingleWorker psw = new PersonalSingleWorker();
        psw.setId(11L);
        psw.setBatchList(new HashSet<>(Collections.singleton(batch)));
        batch.addWorker(psw);

        // When
        batchService.remove(batch, new User("u","n","e@e"));

        // Then: study updated and batch removed
        verify(studyDao, times(1)).update(study);
        // results and links removed
        verify(resultRemover, times(1)).removeAllStudyResults(eq(batch), any(User.class));
        verify(studyLinkDao, times(1)).removeAllByBatch(batch);
        verify(groupResultDao, times(1)).findAllByBatch(batch);
        // workers removed
        verify(workerDao, atLeastOnce()).remove(any(Worker.class));
        // batch itself removed and logging
        verify(batchDao, times(1)).remove(batch);
        verify(studyLogger, times(1)).log(eq(study), any(User.class), eq("Removed batch"), eq(batch));
    }
}
