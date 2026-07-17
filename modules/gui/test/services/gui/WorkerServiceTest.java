package services.gui;

import exceptions.common.BadRequestException;
import models.common.workers.Worker;
import org.junit.Test;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkerService.
 */
public class WorkerServiceTest {

    private final WorkerService workerService = new WorkerService();


    @Test
    public void validateWorker_ok_noException() {
        Worker worker = mock(Worker.class);
        when(worker.validate()).thenReturn(null);

        workerService.validateWorker(worker);

        verify(worker, times(1)).validate();
    }

    @Test(expected = BadRequestException.class)
    public void validateWorker_withErrors_throwsWithFirstMessage() {
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

}
