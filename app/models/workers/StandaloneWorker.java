package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;

/**
 * Concrete worker from JATOS as a standalone tool (in contrast to a worker who
 * originates from MTurk).
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(StandaloneWorker.WORKER_TYPE)
public class StandaloneWorker extends Worker {

	public static final String WORKER_TYPE = "Standalone";

	/**
	 * Worker's name
	 */
	private String name;
	
	/**
	 * Some additional comments about this worker
	 */
	@Lob
	private String comments;
	
	public StandaloneWorker() {
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public void setComments(String comments) {
		this.comments = comments;
	}

	public String getComments() {
		return this.comments;
	}
	
	@Override
	public String toString() {
		return name + ", " + super.toString();
	}

	@Override
	public String generateConfirmationCode() {
		return null;
	}

}
