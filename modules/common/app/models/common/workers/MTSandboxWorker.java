package models.common.workers;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import static models.common.workers.WorkerType.MT_SANDBOX;

/**
 * DB entity of the concrete worker who originates from the MTurk Sandbox.
 */
@Entity
@DiscriminatorValue(MTSandboxWorker.WORKER_TYPE)
public class MTSandboxWorker extends MTWorker {

	static final String WORKER_TYPE = "MTSandbox";
    static final String SHORT_WORKER_TYPE = "mts";
	static final String UI_WORKER_TYPE = "MTurk Sandbox";

    @SuppressWarnings("unused")
	public MTSandboxWorker() {
	}

	@JsonCreator
	public MTSandboxWorker(String mtWorkerId) {
		super(mtWorkerId);
	}

	@Override
	public WorkerType getWorkerType() {
		return MT_SANDBOX;
	}
	
}
