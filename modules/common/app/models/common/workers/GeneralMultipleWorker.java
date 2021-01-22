package models.common.workers;

import play.data.validation.ValidationError;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.util.List;

/**
 * DB entity of the concrete Worker if a study is run as an General Multiple Worker. A General Multiple Worker is a
 * worker for an external run, the worker can run the study many times. The worker is created during start of the study.
 *
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(GeneralMultipleWorker.WORKER_TYPE)
public class GeneralMultipleWorker extends Worker {

    public static final String WORKER_TYPE = "GeneralMultiple";
    public static final String UI_WORKER_TYPE = "General Multiple";

    public GeneralMultipleWorker() {
    }

    @Override
    public String getWorkerType() {
        return WORKER_TYPE;
    }

    @Override
    public String getUIWorkerType() {
        return UI_WORKER_TYPE;
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
