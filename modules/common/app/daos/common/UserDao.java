package daos.common;

import java.util.List;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.common.User;
import play.db.jpa.JPA;

/**
 * DAO for User entity
 * 
 * @author Kristian Lange
 */
@Singleton
public class UserDao extends AbstractDao {

	/**
	 * Persist user und creates it's JatosWorker.
	 */
	public void create(User user) {
		persist(user);
	}

	public void update(User user) {
		merge(user);
	}

	public boolean authenticate(String email, String passwordHash) {
		String queryStr = "SELECT e FROM User e WHERE "
				+ "e.email=:email and e.passwordHash=:passwordHash";
		boolean doesNotExist = JPA.em().createQuery(queryStr, User.class)
				.setMaxResults(1).setParameter("email", email)
				.setParameter("passwordHash", passwordHash).getResultList()
				.isEmpty();
		return !doesNotExist;
	}

	public User findByEmail(String email) {
		return JPA.em().find(User.class, email);
	}

	public List<User> findAll() {
		TypedQuery<User> query = JPA.em().createQuery("SELECT e FROM User e",
				User.class);
		return query.getResultList();
	}

}
