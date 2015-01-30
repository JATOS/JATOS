package models;

import play.db.jpa.JPA;

/**
 * Abstract DAO
 * 
 * @author Kristian Lange
 */
public abstract class AbstractDao<E> {

	public void persist(E entity) {
		JPA.em().persist(entity);
	}

	public void merge(UserModel user) {
		JPA.em().merge(user);
	}

	public void remove(UserModel user) {
		JPA.em().remove(user);
	}

}
