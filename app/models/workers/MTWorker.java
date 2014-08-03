package models.workers;

import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.TypedQuery;

import play.db.jpa.JPA;

@Entity
@DiscriminatorValue("MT")
public class MTWorker extends Worker {

	private String mtWorkerId;

	public MTWorker() {
	}

	public MTWorker(String workerId) {
		this.mtWorkerId = workerId;
	}

	public void setMTWorkerId(String mtWorkerId) {
		this.mtWorkerId = mtWorkerId;
	}

	public String getMTWorkerId() {
		return this.mtWorkerId;
	}

	@Override
	public String toString() {
		return mtWorkerId + ", " + super.toString();
	}

	@Override
	public boolean isAllowedToStartStudy(Long studyId) {
		return !didStudy(studyId);
	}

	public static MTWorker findByMTWorkerId(String mtWorkerId) {
		String queryStr = "SELECT e FROM Worker e WHERE "
				+ "e.mtWorkerId=:mtWorkerId";
		TypedQuery<MTWorker> query = JPA.em().createQuery(queryStr,
				MTWorker.class);
		List<MTWorker> workerList = query
				.setParameter("mtWorkerId", mtWorkerId).getResultList();
		return workerList.isEmpty() ? null : workerList.get(0);
	}

}
