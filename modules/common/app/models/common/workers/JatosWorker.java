package models.common.workers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import models.common.User;
import play.data.validation.ValidationError;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToOne;
import java.util.List;

/**
 * DB entity of the concrete Worker who originates from JATOS itself.
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
	@OneToOne(mappedBy = "worker", fetch = FetchType.LAZY)
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

	@Override
	public String getWorkerType() {
		return WORKER_TYPE;
	}

	@Override
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
