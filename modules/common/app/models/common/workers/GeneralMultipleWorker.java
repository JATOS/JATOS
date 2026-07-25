package models.common.workers;

import play.data.validation.ValidationError;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.List;

import static models.common.workers.WorkerType.GENERAL_MULTIPLE;

/**
 * DB entity of the concrete Worker if a study is run as an General Multiple Worker. A General Multiple Worker is a
 * worker for an external run, the worker can run the study many times. The worker is created during start of the study.
 */
@Entity
@DiscriminatorValue(GeneralMultipleWorker.WORKER_TYPE)
public class GeneralMultipleWorker extends Worker {

    static final String WORKER_TYPE = "GeneralMultiple";
    static final String SHORT_WORKER_TYPE = "gm";
    static final String UI_WORKER_TYPE = "General Multiple";

    public GeneralMultipleWorker() {
    }

    @Override
    public WorkerType getWorkerType() {
        return GENERAL_MULTIPLE;
    }

    @Override
    public String generateConfirmationCode() {
        return null;
    }

    @Override
    public List<ValidationError> validate() {
        return null;
    }

}
