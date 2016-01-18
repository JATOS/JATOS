package daos.common.worker;

import java.util.List;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import daos.common.AbstractDao;
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
	
	public void update(Worker worker) {
		merge(worker);
	}

	public void remove(Worker worker) {
		super.remove(worker);
	}

	public Worker findById(Long id) {
		return JPA.em().find(Worker.class, id);
	}

	public List<Worker> findAll() {
		TypedQuery<Worker> query = JPA.em()
				.createQuery("SELECT e FROM Worker e", Worker.class);
		return query.getResultList();
	}

}
