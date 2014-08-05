package models.workers;

import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.TypedQuery;

import play.db.jpa.JPA;

@Entity
@DiscriminatorValue(MTWorker.WORKER_TYPE)
public class MTWorker extends Worker {

	public static final String WORKER_TYPE = "MT";

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
		return findByMTWorkerId(mtWorkerId, WORKER_TYPE);
	}

	protected static MTWorker findByMTWorkerId(String mtWorkerId,
			String workerType) {
		String queryStr = "SELECT e FROM Worker e WHERE "
				+ "e.mtWorkerId=:mtWorkerId and e.workerType=:workerType";
		TypedQuery<Worker> query = JPA.em().createQuery(queryStr, Worker.class);
		List<Worker> workerList = query.setParameter("mtWorkerId", mtWorkerId)
				.setParameter(Worker.DESCRIMINATOR, workerType).getResultList();
		MTWorker worker = workerList.isEmpty() ? null : (MTWorker) workerList
				.get(0);
		return worker;
	}

}
