package daos.common;

import javax.inject.Singleton;

import models.common.Batch;
import play.db.jpa.JPA;

/**
 * DAO of Batch entity
 * 
 * @author Kristian Lange
 */
@Singleton
public class BatchDao extends AbstractDao {

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
		return JPA.em().find(Batch.class, id);
	}

}
