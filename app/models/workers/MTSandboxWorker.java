package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Concrete worker who originates from the MTurk Sandbox.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(MTSandboxWorker.WORKER_TYPE)
public class MTSandboxWorker extends MTWorker {

	public static final String WORKER_TYPE = "MTSandbox";

	public MTSandboxWorker() {
	}

	public MTSandboxWorker(String mtWorkerId) {
		super(mtWorkerId);
	}

	public static MTWorker findByMTWorkerId(String mtWorkerId) {
		return findByMTWorkerId(mtWorkerId, WORKER_TYPE);
	}

}
