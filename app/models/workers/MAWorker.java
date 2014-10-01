package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import models.UserModel;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Concrete worker who originates from the MechArg itself.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(MAWorker.WORKER_TYPE)
public class MAWorker extends Worker {
	
	public static final String WORKER_TYPE = "MA";

	@JsonIgnore
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_email")
	private UserModel user;

	public MAWorker() {
	}
	
	public MAWorker(UserModel user) {
		this.user = user;
	}

	public void setUser(UserModel user) {
		this.user = user;
	}

	public UserModel getUser() {
		return this.user;
	}

	@Override
	public String toString() {
		return user.getEmail() + ", " + super.toString();
	}
	
}
