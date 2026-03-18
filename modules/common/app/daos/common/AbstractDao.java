package daos.common;

import javax.inject.Singleton;

import play.db.jpa.JPAApi;

/**
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
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

	public void flush() {
		jpa.em().flush();
	}

}
