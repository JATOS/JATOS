package daos.common;

import models.common.Batch;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * DAO of Batch entity
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class BatchDao extends AbstractDao {

	@Inject
	BatchDao(JPAApi jpa) {
		super(jpa);
	}

	public void create(Batch batch) {
		persist(batch);
	}

	public void update(Batch batch) {
		merge(batch);
	}

	public void remove(Batch batch) {
		super.remove(batch);
	}

	public Batch findById(Long id) {
		return jpa.em().find(Batch.class, id);
	}

	/**
	 * Counts all workers in the given batch that are not JatosWorkers
	 * https://stackoverflow.com/a/34432660/1278769
	 */
	public int countWorkersWithoutJatosWorker(Batch batch) {
		Number result = (Number) jpa.em()
				.createQuery("SELECT COUNT(*) AS cnt FROM Worker worker "
						+ "WHERE TYPE(worker)!=JatosWorker AND worker.id IN("
						+ "SELECT workerList.id FROM Batch batch INNER JOIN batch.workerList workerList WHERE batch.id=:batchId"
						+ ")")
				.setParameter("batchId", batch.getId())
				.getSingleResult();
		return result != null ? result.intValue() : 0;
	}

}
