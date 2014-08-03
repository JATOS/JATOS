package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("MTSandbox")
public class MTSandboxWorker extends MTWorker {

	public MTSandboxWorker(String workerId) {
		super(workerId);
	}
	
	@Override
	public boolean isAllowedToStartStudy(Long studyId) {
		return true;
	}

}
