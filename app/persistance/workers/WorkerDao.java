package persistance.workers;

import java.util.List;

import javax.persistence.TypedQuery;

import models.ComponentResult;
import models.StudyResult;
import models.workers.JatosWorker;
import models.workers.Worker;
import persistance.AbstractDao;
import play.db.jpa.JPA;

import com.google.inject.Singleton;

/**
 * DAO for abstract Worker model
 * 
 * @author Kristian Lange
 */
@Singleton
public class WorkerDao extends AbstractDao<Worker> {

	public void create(Worker worker) {
		persist(worker);
	}

	/**
	 * Removes a Worker including all its StudyResults and all their
	 * ComponentResults.
	 */
	public void remove(Worker worker) {
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
		super.remove(worker);
	}

	public Worker findById(Long id) {
		return JPA.em().find(Worker.class, id);
	}

	public List<Worker> findAll() {
		TypedQuery<Worker> query = JPA.em().createQuery(
				"SELECT e FROM Worker e", Worker.class);
		return query.getResultList();
	}

}
