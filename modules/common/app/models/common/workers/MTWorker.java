package models.common.workers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import play.data.validation.ValidationError;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.util.List;
import java.util.UUID;

import static models.common.workers.WorkerType.MT;

/**
 * DB entity of the concrete worker who originates from the MTurk.
 */
@Entity
@DiscriminatorValue(MTWorker.WORKER_TYPE)
public class MTWorker extends Worker {

	static final String WORKER_TYPE = "MT";
    static final String SHORT_WORKER_TYPE = "mt";
	static final String UI_WORKER_TYPE = "MTurk";

	/**
	 * Worker ID from MTurk
	 */
	@JsonProperty("mtWorkerId")
	private String mtWorkerId;

	public MTWorker() {
	}

	@JsonCreator
	public MTWorker(String mtWorkerId) {
		this.mtWorkerId = mtWorkerId;
	}

	@Override
	public WorkerType getWorkerType() {
		return MT;
	}

	public String getMTWorkerId() {
		return this.mtWorkerId;
	}

	@Override
	public String generateConfirmationCode() {
		return UUID.randomUUID().toString();
	}

	@Override
	public List<ValidationError> validate() {
		return null;
	}

}
