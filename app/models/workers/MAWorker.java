package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import models.UserModel;

@Entity
@DiscriminatorValue("MA")
public class MAWorker extends Worker {

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
	
	@Override
	public boolean isAllowedToStartStudy(Long studyId) {
		return true;
	}

}
