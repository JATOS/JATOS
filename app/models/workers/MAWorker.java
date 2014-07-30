package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import models.MAUser;

@Entity
@DiscriminatorValue("MA")
public class MAWorker extends Worker {

	private MAUser user;

	public MAWorker() {
	}
	
	public MAWorker(MAUser user) {
		this.user = user;
	}

	public void setUser(MAUser user) {
		this.user = user;
	}

	public MAUser getUser() {
		return this.user;
	}

	@Override
	public String toString() {
		return user.getEmail() + ", " + super.toString();
	}

}
