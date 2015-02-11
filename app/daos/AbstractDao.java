package daos;

import play.db.jpa.JPA;

import com.google.inject.Singleton;

/**
 * Abstract DAO
 * 
 * @author Kristian Lange
 */
@Singleton
public abstract class AbstractDao {

	public static void persist(Object entity) {
		JPA.em().persist(entity);
	}

	public static void merge(Object entity) {
		JPA.em().merge(entity);
	}

	public static void remove(Object entity) {
		JPA.em().remove(entity);
	}

	public static void refresh(Object entity) {
		JPA.em().refresh(entity);
	}

}
