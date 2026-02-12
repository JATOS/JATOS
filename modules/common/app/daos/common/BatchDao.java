package daos.common;

import models.common.Batch;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

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

	public Optional<Batch> findByUuid(String uuid) {
		String queryStr = "SELECT s FROM Batch s WHERE " + "s.uuid=:uuid";
		List<Batch> batchList = jpa.em().createQuery(queryStr, Batch.class)
				.setParameter("uuid", uuid)
				.setMaxResults(1)
				.getResultList();
		return !batchList.isEmpty() ? Optional.of(batchList.get(0)) : Optional.empty();
	}

}
