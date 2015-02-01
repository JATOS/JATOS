package daos;

import com.google.inject.Singleton;

import play.db.jpa.JPA;

/**
 * Abstract DAO
 * 
 * @author Kristian Lange
 */
@Singleton
public abstract class AbstractDao<E> {

	public void persist(E entity) {
		JPA.em().persist(entity);
	}

	public void merge(E entity) {
		JPA.em().merge(entity);
	}

	public void remove(E entity) {
		JPA.em().remove(entity);
	}

	public void refresh(E entity) {
		JPA.em().refresh(entity);
	}

}
