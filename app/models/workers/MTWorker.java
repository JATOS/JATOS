package models.workers;

import java.util.List;
import java.util.UUID;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.TypedQuery;

import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Concrete worker who originates from the MTurk.
 * 
 * @author Kristian Lange
 */
@Entity
@DiscriminatorValue(MTWorker.WORKER_TYPE)
public class MTWorker extends Worker {

	public static final String WORKER_TYPE = "MT";
	public static final String UI_WORKER_TYPE = "MTurk";

	/**
	 * Worker ID from MTurk
	 */
	@JsonProperty("mtWorkerId")
	private String mtWorkerId;

	public MTWorker() {
	}

	@JsonCreator
	public MTWorker(String mtWorkerId) {
		this.mtWorkerId = mtWorkerId;
	}

	public void setMTWorkerId(String mtWorkerId) {
		this.mtWorkerId = mtWorkerId;
	}

	public String getMTWorkerId() {
		return this.mtWorkerId;
	}
	
	public String getUIWorkerType() {
		return UI_WORKER_TYPE;
	}

	@Override
	public String toString() {
		return mtWorkerId + ", " + super.toString();
	}

	@Override
	public String generateConfirmationCode() {
		return UUID.randomUUID().toString();
	}

	/**
	 * Retrieves the worker with the given MTurk worker ID in a case insensitive
	 * way.
	 */
	public static MTWorker findByMTWorkerId(String mtWorkerId) {
		String queryStr = "SELECT e FROM Worker e WHERE "
				+ "upper(e.mtWorkerId)=:mtWorkerId";
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

	/**
	 * Retrieves the worker with the given MTurk worker ID and type in a case
	 * insensitive way.
	 */
	protected static MTWorker findByMTWorkerId(String mtWorkerId,
			String workerType) {
		String queryStr = "SELECT e FROM Worker e WHERE "
				+ "upper(e.mtWorkerId)=:mtWorkerId and e.workerType=:workerType";
		TypedQuery<Worker> query = JPA.em().createQuery(queryStr, Worker.class);
		List<Worker> workerList = query.setParameter("mtWorkerId", mtWorkerId)
				.setParameter(Worker.DISCRIMINATOR, workerType).getResultList();
		MTWorker worker = workerList.isEmpty() ? null : (MTWorker) workerList
				.get(0);
		return worker;
	}

}
