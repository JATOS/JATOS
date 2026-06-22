package services.publix;

import daos.common.BatchDao;
import daos.common.worker.WorkerDao;
import models.common.Batch;
import models.common.workers.GeneralMultipleWorker;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import models.common.workers.Worker;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkerCreator.
 */
public class WorkerCreatorTest {

    private WorkerDao workerDao;
    private BatchDao batchDao;

    private WorkerCreator workerCreator;

    @Before
    public void setup() {
        workerDao = mock(WorkerDao.class);
        batchDao = mock(BatchDao.class);
        workerCreator = new WorkerCreator(workerDao, batchDao);

        doAnswer(invocation -> {
            Worker worker = invocation.getArgument(0);
            worker.setId(100L);
            return null;
        }).when(workerDao).create(any(Worker.class));
    }

    @Test
    public void createAndPersistMTWorker_createsSandboxWorker_whenSandboxTrue() {
        Batch batch = new Batch();
        batch.setId(1L);
        String mtId = "A1B2C3";

        MTWorker created = workerCreator.createAndPersistMTWorker(mtId, true, batch);

        assertNotNull(created);
        assertTrue(created instanceof MTSandboxWorker);
        assertEquals(mtId, created.getMTWorkerId());
        assertFalse(batch.getWorkerList().contains(created));
        verify(workerDao).persist(created);
        verify(batchDao).addWorkerToBatch(batch.getId(), created.getId());
        verifyNoMoreInteractions(workerDao, batchDao);
    }

    @Test
    public void createAndPersistMTWorker_createsMTWorker_whenSandboxFalse() {
        Batch batch = new Batch();
        batch.setId(1L);
        String mtId = "Z9Y8X7";

        MTWorker created = workerCreator.createAndPersistMTWorker(mtId, false, batch);

        assertNotNull(created);
        assertFalse(created instanceof MTSandboxWorker);
        assertEquals(mtId, created.getMTWorkerId());
        assertFalse(batch.getWorkerList().contains(created));
        verify(workerDao).persist(created);
        verify(batchDao).addWorkerToBatch(batch.getId(), created.getId());
        verifyNoMoreInteractions(workerDao, batchDao);
    }

    @Test
    public void createAndPersistGeneralSingleWorker_persistsAndLinks() {
        Batch batch = new Batch();
        batch.setId(1L);

        GeneralSingleWorker created = workerCreator.createAndPersistGeneralSingleWorker(batch);

        assertNotNull(created);
        assertFalse(batch.getWorkerList().contains(created));
        verify(workerDao).persist(created);
        verify(batchDao).addWorkerToBatch(batch.getId(), created.getId());
        verifyNoMoreInteractions(workerDao, batchDao);
    }

    @Test
    public void createAndPersistGeneralMultipleWorker_persistsAndLinks() {
        Batch batch = new Batch();
        batch.setId(1L);

        GeneralMultipleWorker created = workerCreator.createAndPersistGeneralMultipleWorker(batch);

        assertNotNull(created);
        assertFalse(batch.getWorkerList().contains(created));
        verify(workerDao).persist(created);
        verify(batchDao).addWorkerToBatch(batch.getId(), created.getId());
        verifyNoMoreInteractions(workerDao, batchDao);
    }
}
