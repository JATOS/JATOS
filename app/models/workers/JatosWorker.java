package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import models.UserModel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Concrete worker who originates from JATOS itself.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(JatosWorker.WORKER_TYPE)
public class JatosWorker extends Worker {
	
	public static final String WORKER_TYPE = "Jatos";

	@JsonIgnore
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_email")
	private UserModel user;

	public JatosWorker() {
	}
	
	public JatosWorker(UserModel user) {
		this.user = user;
	}

	public void setUser(UserModel user) {
		this.user = user;
	}

	public UserModel getUser() {
		return this.user;
	}
	
	@JsonProperty("userEmail")
	public String getUserEmail() {
		return this.user.getEmail();
	}

	@Override
	public String toString() {
		return user.getEmail() + ", " + super.toString();
	}
	
	@Override
	public String generateConfirmationCode() {
		return null;
	}
	
}
