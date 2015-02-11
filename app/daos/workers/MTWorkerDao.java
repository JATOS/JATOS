package daos.workers;

import java.util.List;

import javax.persistence.TypedQuery;

import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import models.workers.Worker;
import play.db.jpa.JPA;

import com.google.inject.Singleton;

/**
 * DAO for MTWorker model
 * 
 * @author Kristian Lange
 */
@Singleton
public class MTWorkerDao extends WorkerDao implements IMTWorkerDao {

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.workers.IMTWorkerDao#createMTWorker(java.lang.String, boolean)
	 */
	@Override
	public MTWorker createMTWorker(String mtWorkerId, boolean mTurkSandbox) {
		MTWorker worker;
		if (mTurkSandbox) {
			worker = new MTSandboxWorker(mtWorkerId);
		} else {
			worker = new MTWorker(mtWorkerId);
		}
		persist(worker);
		return worker;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.workers.IMTWorkerDao#findByMTWorkerId(java.lang.String)
	 */
	@Override
	public MTWorker findByMTWorkerId(String mtWorkerId) {
		String queryStr = "SELECT e FROM Worker e WHERE "
				+ "upper(e.mtWorkerId)=:mtWorkerId";
		TypedQuery<Worker> query = JPA.em().createQuery(queryStr, Worker.class);
		List<Worker> workerList = query.setParameter("mtWorkerId", mtWorkerId)
				.getResultList();
		MTWorker worker = workerList.isEmpty() ? null : (MTWorker) workerList
				.get(0);
		return worker;
	}

}
