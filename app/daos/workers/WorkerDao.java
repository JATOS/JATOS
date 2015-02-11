package daos.workers;

import java.util.List;

import javax.persistence.TypedQuery;

import models.ComponentResult;
import models.StudyResult;
import models.workers.JatosWorker;
import models.workers.Worker;
import play.db.jpa.JPA;

import com.google.inject.Singleton;

import daos.AbstractDao;

/**
 * DAO for abstract Worker model
 * 
 * @author Kristian Lange
 */
@Singleton
public class WorkerDao extends AbstractDao implements IWorkerDao {

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.workers.IWorkerDao#removeWorker(models.workers.Worker)
	 */
	@Override
	public void removeWorker(Worker worker) {
		// Don't remove JATOS' own workers
		if (worker instanceof JatosWorker) {
			return;
		}

		// Remove all studyResults and their componentResults
		for (StudyResult studyResult : worker.getStudyResultList()) {
			for (ComponentResult componentResult : studyResult
					.getComponentResultList()) {
				remove(componentResult);
			}
			remove(studyResult);
		}

		// Remove worker
		remove(worker);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.workers.IWorkerDao#findById(java.lang.Long)
	 */
	@Override
	public Worker findById(Long id) {
		return JPA.em().find(Worker.class, id);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.workers.IWorkerDao#findAll()
	 */
	@Override
	public List<Worker> findAll() {
		TypedQuery<Worker> query = JPA.em().createQuery(
				"SELECT e FROM Worker e", Worker.class);
		return query.getResultList();
	}

}
