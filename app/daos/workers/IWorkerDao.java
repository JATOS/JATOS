package daos.workers;

import java.util.List;

import models.workers.Worker;

/**
 * Interface for DAO of Worker
 * 
 * @author Kristian Lange
 */
public interface IWorkerDao {

	/**
	 * Removes a Worker including all its StudyResults and all their
	 * ComponentResults.
	 */
	public abstract void removeWorker(Worker worker);

	public abstract Worker findById(Long id);

	public abstract List<Worker> findAll();

}