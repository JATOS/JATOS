package services.gui;

import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import exceptions.common.NotFoundException;
import general.common.StudyLogger;
import http.common.Http.Context;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import models.common.workers.WorkerType;
import models.gui.BatchProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import play.test.Helpers;
import testutils.gui.JPAMocker;

import java.util.Collections;
import java.util.UUID;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.workers.WorkerType.JATOS;
import static models.common.workers.WorkerType.PERSONAL_SINGLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchService.
 */
public class BatchServiceTest {

    private BatchDao batchDao;
    private StudyDao studyDao;
    private WorkerDao workerDao;
    private GroupResultDao groupResultDao;
    private StudyLogger studyLogger;

    private BatchService batchService;

    @Before
    public void setup() {
        batchDao = Mockito.mock(BatchDao.class);
        studyDao = Mockito.mock(StudyDao.class);
        workerDao = Mockito.mock(WorkerDao.class);
        groupResultDao = Mockito.mock(GroupResultDao.class);
        StudyLinkDao studyLinkDao = Mockito.mock(StudyLinkDao.class);
        studyLogger = Mockito.mock(StudyLogger.class);
        batchService = new BatchService(batchDao, studyDao, workerDao, studyLogger);

        JPAMocker.mockDaoTransactions(batchDao, studyDao, workerDao, groupResultDao, studyLinkDao);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    @After
    public void tearDown() {
        Context.clear();
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
        study.addBatch(defaultBatch);

        // One member with JatosWorker
        User user = new User("member", "Member", "m@example.org");
        JatosWorker jw = new JatosWorker(user);
        user.setWorker(jw);
        study.addUser(user);
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
        original.addAllowedWorkerType(JATOS);
        original.addAllowedWorkerType(PERSONAL_SINGLE);
        original.setBatchInput("{\"a\":1}");
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
        assertThat(clone.getAllowedWorkerTypes()).containsOnly(WorkerType.JATOS, WorkerType.PERSONAL_SINGLE);
        assertThat(clone.getWorkerList()).contains(worker);
        assertThat(clone.getBatchInput()).isEqualTo("{\"a\":1}");

        // new UUID and default session/version (not copied)
        assertThat(clone.getUuid()).isNotEqualTo("orig-uuid");
        assertThat(clone.getBatchSessionData()).isEqualTo("{}");
        assertThat(clone.getBatchSessionVersion()).isEqualTo(1L);
    }

    @Test
    public void createDefaultBatch_initializesFields() {
        // When
        Batch batch = batchService.createDefaultBatch();

        // Then
        assertThat(batch.getTitle()).isEqualTo(BatchProperties.DEFAULT_TITLE);
        assertThat(batch.getUuid()).isNull();
        assertThat(batch.getAllowedWorkerTypes()).containsOnly(WorkerType.PERSONAL_MULTIPLE, WorkerType.PERSONAL_SINGLE);
        // All members' JatosWorkers added
        assertThat(batch.getWorkerList()).hasSize(0);
        assertThat(batch.getBatchSessionData()).isEqualTo("{}");
        assertThat(batch.getStudy()).isNull();
    }

    @Test
    public void updateBatch_appliesProperties_andPersists() {
        // Given
        Batch batch = new Batch();
        BatchProperties props = new BatchProperties();
        props.setTitle("NewTitle");
        props.setActive(false);
        props.setMaxActiveMembers(7);
        props.setMaxTotalMembers(15);
        props.setMaxTotalWorkers(30);
        props.addAllowedWorkerType(PERSONAL_SINGLE);
        props.setComments("c");
        props.setBatchInput("{x:1}");

        // When
        batchService.updateBatch(batch, props);

        // Then values applied
        assertThat(batch.getTitle()).isEqualTo("NewTitle");
        assertThat(batch.isActive()).isFalse();
        assertThat(batch.getMaxActiveMembers()).isEqualTo(7);
        assertThat(batch.getMaxTotalMembers()).isEqualTo(15);
        assertThat(batch.getMaxTotalWorkers()).isEqualTo(30);
        assertThat(batch.getAllowedWorkerTypes()).containsOnly(WorkerType.PERSONAL_SINGLE);
        assertThat(batch.getComments()).isEqualTo("c");
        assertThat(batch.getBatchInput()).isEqualTo("{x:1}");

        verify(batchDao, times(1)).merge(batch);
    }

    @Test
    public void bind_roundTrip_preservesLimitsAndAllowedWorkers() {
        Batch batch = new Batch();
        batch.setTitle("T");
        batch.setActive(true);
        batch.setMaxActiveMembers(1);
        batch.setMaxTotalMembers(2);
        batch.setMaxTotalWorkers(3);
        batch.addAllowedWorkerType(WorkerType.JATOS);
        batch.addAllowedWorkerType(WorkerType.PERSONAL_SINGLE);
        batch.setComments("c");
        batch.setBatchInput("{y:2}");

        BatchProperties props = batchService.bindToProperties(batch);
        assertThat(props.getAllowedWorkerTypes()).contains(WorkerType.JATOS, WorkerType.PERSONAL_SINGLE);

        Batch fromProps = batchService.bindToBatch(props);
        assertThat(fromProps.getMaxActiveMembers()).isEqualTo(1);
        assertThat(fromProps.getMaxTotalMembers()).isEqualTo(2);
        assertThat(fromProps.getMaxTotalWorkers()).isEqualTo(3);
        assertThat(fromProps.getAllowedWorkerTypes()).contains(WorkerType.JATOS, WorkerType.PERSONAL_SINGLE);
        assertThat(fromProps.getComments()).isEqualTo("c");
        assertThat(fromProps.getBatchInput()).contains("{y:2}");
    }

    @Test
    public void fetchBatch_minusOne_returnsDefault_andMissingThrows() {
        // Default
        Study study = studyWithOneUserAndDefaultBatch();
        when(batchDao.findDefaultBatchByStudy(study)).thenReturn(study.getDefaultBatch());
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
        study.addBatch(batch);

        // Group results to be removed
        when(groupResultDao.findAllByBatch(batch)).thenReturn(Collections.emptyList());

        // Workers in batch
        // 1) JatosWorker with no user (should be removed)
        JatosWorker jw = new JatosWorker();
        jw.setId(10L);
        jw.setUser(null);
        jw.addBatch(batch);
        batch.addWorker(jw);
        // 2) PersonalSingleWorker belonging only to this batch (should be removed)
        PersonalSingleWorker psw = new PersonalSingleWorker();
        psw.setId(11L);
        psw.addBatch(batch);
        batch.addWorker(psw);

        Context.current().args().put(SIGNEDIN_USER, new User());

        // When
        batchService.remove(batch);

        // Then: study updated and batch removed
        verify(studyDao, times(1)).merge(study);
        // workers removed
        verify(workerDao, atLeastOnce()).remove(any(Worker.class));
        // batch itself removed and logging
        verify(batchDao, times(1)).remove(batch);
        verify(studyLogger, times(1)).log(eq(study), any(User.class), eq("Removed batch"), eq(batch));
    }
}
