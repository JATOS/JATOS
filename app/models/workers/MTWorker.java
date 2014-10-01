package models.workers;

import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.TypedQuery;

import play.db.jpa.JPA;
import services.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

/**
 * Concrete worker who originates from the MTurk.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(MTWorker.WORKER_TYPE)
public class MTWorker extends Worker {

	public static final String WORKER_TYPE = "MT";

	@JsonView(JsonUtils.JsonForMA.class)
	private String mtWorkerId;
	
	public MTWorker() {
	}

	public MTWorker(String mtWorkerId) {
		this.mtWorkerId = mtWorkerId;
	}

	public void setMTWorkerId(String mtWorkerId) {
		this.mtWorkerId = mtWorkerId;
	}

	@JsonIgnore
	public String getMTWorkerId() {
		return this.mtWorkerId;
	}
	
	@Override
	public String toString() {
		return mtWorkerId + ", " + super.toString();
	}
	
	public static MTWorker findByMTWorkerId(String mtWorkerId) {
		String queryStr = "SELECT e FROM Worker e WHERE "
				+ "e.mtWorkerId=:mtWorkerId";
		TypedQuery<Worker> query = JPA.em().createQuery(queryStr, Worker.class);
		List<Worker> workerList = query.setParameter("mtWorkerId", mtWorkerId)
				.getResultList();
		MTWorker worker = workerList.isEmpty() ? null : (MTWorker) workerList
				.get(0);
		return worker;
	}

	public static MTWorker findByMTWorkerIdAndWorkerType(String mtWorkerId) {
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
