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

}
