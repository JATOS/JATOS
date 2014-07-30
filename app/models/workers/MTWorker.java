package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("MT")
public class MTWorker extends Worker {

	private String workerId;

	public MTWorker() {
	}
	
	public MTWorker(String workerId) {
		this.workerId = workerId;
	}

	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}

	public String getWorkerId() {
		return this.workerId;
	}

	@Override
	public String toString() {
		return workerId + ", " + super.toString();
	}

}
