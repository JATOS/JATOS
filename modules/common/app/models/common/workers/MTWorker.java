package models.common.workers;

import java.util.List;
import java.util.UUID;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import play.data.validation.ValidationError;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model and DB entity of the concrete worker who originates from the MTurk.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(MTWorker.WORKER_TYPE)
public class MTWorker extends Worker {

	public static final String WORKER_TYPE = "MT";
	public static final String UI_WORKER_TYPE = "MTurk";

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

	public void setMTWorkerId(String mtWorkerId) {
		this.mtWorkerId = mtWorkerId;
	}

	public String getMTWorkerId() {
		return this.mtWorkerId;
	}

	public String getWorkerType() {
		return WORKER_TYPE;
	}

	public String getUIWorkerType() {
		return UI_WORKER_TYPE;
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
