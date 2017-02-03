package daos.common;

import javax.inject.Singleton;

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

	protected AbstractDao(JPAApi jpa) {
		this.jpa = jpa;
	}

	protected void persist(Object entity) {
		jpa.em().persist(entity);
	}

	protected void merge(Object entity) {
		jpa.em().merge(entity);
	}

	protected void remove(Object entity) {
		jpa.em().remove(entity);
	}

	protected void refresh(Object entity) {
		jpa.em().refresh(entity);
	}

}
