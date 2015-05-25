package models.workers;

import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import play.data.validation.ValidationError;

/**
 * Worker if a study is run as an general single worker. A general single worker
 * is a worker for an external run, the worker can run the study only once. The
 * worker is created during start of the study.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(GeneralSingleWorker.WORKER_TYPE)
public class GeneralSingleWorker extends Worker {

	public static final String WORKER_TYPE = "GeneralSingle";
	public static final String UI_WORKER_TYPE = "General Single";

	public GeneralSingleWorker() {
	}

	public String getWorkerType() {
		return WORKER_TYPE;
	}

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
