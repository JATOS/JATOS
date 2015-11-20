package daos.common;

import javax.inject.Singleton;

import play.db.jpa.JPA;

/**
 * Abstract DAO: Of the JPA calls only refresh() is public - persist(), merge()
 * and remove() are protected. The latter ones usually involve changes in
 * different entities and should be handled by the DAO of that type.
 * 
 * @author Kristian Lange
 */
@Singleton
public abstract class AbstractDao {

	protected void persist(Object entity) {
		JPA.em().persist(entity);
	}

	protected void merge(Object entity) {
		JPA.em().merge(entity);
	}

	protected void remove(Object entity) {
		JPA.em().remove(entity);
	}

	protected void refresh(Object entity) {
		JPA.em().refresh(entity);
	}

}
