package services.gui;

import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import models.common.Batch;
import models.common.workers.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import play.data.validation.ValidationError;

import java.util.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkerService.
 */
public class WorkerServiceTest {

    private StudyResultDao studyResultDao;
    private WorkerService workerService;

    @Before
    public void setUp() {
        studyResultDao = mock(StudyResultDao.class);
        workerService = new WorkerService(studyResultDao);
    }

    @Test
    public void validateWorker_ok_noException() throws Exception {
        Worker worker = mock(Worker.class);
        when(worker.validate()).thenReturn(null);

        workerService.validateWorker(worker);

        verify(worker, times(1)).validate();
    }

    @Test(expected = BadRequestException.class)
    public void validateWorker_withErrors_throwsWithFirstMessage() throws Exception {
        Worker worker = mock(Worker.class);
        List<ValidationError> errors = new ArrayList<>();
        errors.add(new ValidationError("field", "boom"));
        errors.add(new ValidationError("field2", "ignored"));
        when(worker.validate()).thenReturn(errors);

        try {
            workerService.validateWorker(worker);
        } finally {
            verify(worker, times(1)).validate();
        }
    }

    @Test
    public void retrieveStudyResultCountsPerWorker_returnsCountsForAllTypes() {
        Batch batch = new Batch();
        batch.setId(1L);

        // Prepare dao counts for each worker type
        Map<String, Integer> expected = new HashMap<>();
        expected.put(JatosWorker.WORKER_TYPE, 1);
        expected.put(PersonalSingleWorker.WORKER_TYPE, 2);
        expected.put(PersonalMultipleWorker.WORKER_TYPE, 3);
        expected.put(GeneralSingleWorker.WORKER_TYPE, 4);
        expected.put(GeneralMultipleWorker.WORKER_TYPE, 5);
        expected.put(MTWorker.WORKER_TYPE, 6); // includes MTSandbox in DAO

        // stubbing
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            when(studyResultDao.countByBatchAndWorkerType(batch, e.getKey())).thenReturn(e.getValue());
        }

        Map<String, Integer> result = workerService.retrieveStudyResultCountsPerWorker(batch);

        assertThat(result).isEqualTo(expected);
        for (String workerType : expected.keySet()) {
            verify(studyResultDao).countByBatchAndWorkerType(batch, workerType);
        }
    }

    @Test
    public void extractWorkerType_mappings_allAliases() throws Exception {
        assertThat(workerService.extractWorkerType("jatos")).isEqualTo(JatosWorker.WORKER_TYPE);
        assertThat(workerService.extractWorkerType("ja")).isEqualTo(JatosWorker.WORKER_TYPE);

        assertThat(workerService.extractWorkerType("personalsingle")).isEqualTo(PersonalSingleWorker.WORKER_TYPE);
        assertThat(workerService.extractWorkerType("ps")).isEqualTo(PersonalSingleWorker.WORKER_TYPE);

        assertThat(workerService.extractWorkerType("personalmultiple")).isEqualTo(PersonalMultipleWorker.WORKER_TYPE);
        assertThat(workerService.extractWorkerType("pm")).isEqualTo(PersonalMultipleWorker.WORKER_TYPE);

        assertThat(workerService.extractWorkerType("generalsingle")).isEqualTo(GeneralSingleWorker.WORKER_TYPE);
        assertThat(workerService.extractWorkerType("gs")).isEqualTo(GeneralSingleWorker.WORKER_TYPE);

        assertThat(workerService.extractWorkerType("generalmultiple")).isEqualTo(GeneralMultipleWorker.WORKER_TYPE);
        assertThat(workerService.extractWorkerType("gm")).isEqualTo(GeneralMultipleWorker.WORKER_TYPE);

        assertThat(workerService.extractWorkerType("mturk")).isEqualTo(MTWorker.WORKER_TYPE);
        assertThat(workerService.extractWorkerType("mt")).isEqualTo(MTWorker.WORKER_TYPE);

        assertThat(workerService.extractWorkerType("mturksandbox")).isEqualTo(MTSandboxWorker.WORKER_TYPE);
        assertThat(workerService.extractWorkerType("mts")).isEqualTo(MTSandboxWorker.WORKER_TYPE);
    }

    @Test
    public void extractWorkerType_null_returnsNull() throws Exception {
        assertThat(workerService.extractWorkerType(null)).isNull();
    }

    @Test(expected = BadRequestException.class)
    public void extractWorkerType_unknown_throws() throws Exception {
        workerService.extractWorkerType("unknown");
    }
}
