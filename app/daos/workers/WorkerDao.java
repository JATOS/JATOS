package daos.workers;

import java.util.List;

import javax.persistence.TypedQuery;

import play.db.jpa.JPA;
import models.workers.Worker;

import com.google.inject.Singleton;

import daos.AbstractDao;

/**
 * DAO for abstract Worker model
 * 
 * @author Kristian Lange
 */
@Singleton
public class WorkerDao extends AbstractDao<Worker> {

	public Worker findById(Long id) {
		return JPA.em().find(Worker.class, id);
	}

	public List<Worker> findAll() {
		TypedQuery<Worker> query = JPA.em().createQuery(
				"SELECT e FROM Worker e", Worker.class);
		return query.getResultList();
	}
	
}
