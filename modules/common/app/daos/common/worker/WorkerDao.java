package daos.common.worker;

import java.util.List;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import daos.common.AbstractDao;
import models.common.StudyResult;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import play.db.jpa.JPA;

/**
 * DAO for abstract Worker entity
 * 
 * @author Kristian Lange
 */
@Singleton
public class WorkerDao extends AbstractDao {

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
			studyResult.getComponentResultList().forEach(this::remove);
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
