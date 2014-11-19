package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * MTWorker for testing purpose.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(MTTesterWorker.WORKER_TYPE)
public class MTTesterWorker extends MTWorker {

	public static final String WORKER_TYPE = "MTTester";

	public MTTesterWorker() {
	}

	@JsonCreator
	public MTTesterWorker(String mtWorkerId) {
		super(mtWorkerId);
	}

	public static MTWorker findByMTWorkerId(String mtWorkerId) {
		return findByMTWorkerId(mtWorkerId, WORKER_TYPE);
	}

}
