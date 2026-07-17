package services.gui;

import exceptions.common.BadRequestException;
import models.common.workers.Worker;
import play.data.validation.ValidationError;

import javax.inject.Singleton;
import java.util.List;

/**
 * Service class for everything related to Workers.
 */
@Singleton
public class WorkerService {

    public void validateWorker(Worker worker) {
        List<ValidationError> errorList = worker.validate();
        if (errorList != null && !errorList.isEmpty()) {
            String errorMsg = errorList.get(0).message();
            throw new BadRequestException(errorMsg);
        }
    }

}
