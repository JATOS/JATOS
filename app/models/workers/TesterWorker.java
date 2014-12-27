package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Worker for testing purpose.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(TesterWorker.WORKER_TYPE)
public class TesterWorker extends Worker {

	public static final String WORKER_TYPE = "Tester";

	/**
	 * Worker's name or other identification
	 */
	private String name;
	
	public TesterWorker() {
	}
	
	@JsonCreator
	public TesterWorker(String name) {
		this.name = name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	@Override
	public String generateConfirmationCode() {
		return null;
	}

}
