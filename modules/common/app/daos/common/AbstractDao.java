package daos.common;

import javax.inject.Singleton;
import javax.persistence.EntityManager;

import play.db.jpa.JPAApi;

/**
 * Abstract DAO: Of the JPA calls only refresh() is public - persist(), merge()
 * and remove() are protected. The latter ones usually involve changes in
 * different entities and should be handled by the DAO of that type.
 * 
 * @author Kristian Lange
 */
@Singleton
public abstract class AbstractDao {

	protected final JPAApi jpa;
	protected final EntityManager em;

	protected AbstractDao(JPAApi jpa) {
		this.jpa = jpa;
		this.em = jpa.em("default");
	}

	public EntityManager em() {
		return em;
	}

	protected void persist(Object entity) {
		em().persist(entity);
	}

	protected void merge(Object entity) {
		em().merge(entity);
	}

	protected void remove(Object entity) {
		em().remove(entity);
	}

	protected void refresh(Object entity) {
		em().refresh(entity);
	}

}
