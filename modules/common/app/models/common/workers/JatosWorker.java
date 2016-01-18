package models.common.workers;

import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import models.common.User;
import play.data.validation.ValidationError;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model and DB entity of the concrete Worker who originates from JATOS itself.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(JatosWorker.WORKER_TYPE)
public class JatosWorker extends Worker {

	public static final String WORKER_TYPE = "Jatos";
	public static final String UI_WORKER_TYPE = "Jatos";

	/**
	 * Corresponding User. This relationship is bidirectional.
	 */
	@JsonIgnore
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_email")
	private User user;

	public JatosWorker() {
	}

	public JatosWorker(User user) {
		this.user = user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public User getUser() {
		return this.user;
	}

	@JsonProperty("userEmail")
	public String getUserEmail() {
		return this.user.getEmail();
	}

	public String getWorkerType() {
		return WORKER_TYPE;
	}

	public String getUIWorkerType() {
		return UI_WORKER_TYPE;
	}

	@Override
	public String generateConfirmationCode() {
		return null;
	}

	@Override
	public List<ValidationError> validate() {
		return null;
	}

}
