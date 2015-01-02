package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Worker if a study is run as an open standalone worker
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(OpenStandaloneWorker.WORKER_TYPE)
public class OpenStandaloneWorker extends Worker {

	public static final String WORKER_TYPE = "OpenStandalone";
	public static final String UI_WORKER_TYPE = "Open standalone";

	public OpenStandaloneWorker() {
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

}
