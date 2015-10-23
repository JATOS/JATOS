package persistance;

import java.util.List;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.User;
import models.workers.JatosWorker;
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
		JatosWorker worker = new JatosWorker(user);
		persist(worker);
		user.setWorker(worker);
		persist(user);
		merge(worker);
	}

	public void update(User user) {
		merge(user);
	}

	/**
	 * Changes only the name of the given user.
	 */
	public void updateName(User user, String name) {
		user.setName(name);
		merge(user);
	}

	public User authenticate(String email, String passwordHash) {
		String queryStr = "SELECT e FROM User e WHERE "
				+ "e.email=:email and e.passwordHash=:passwordHash";
		TypedQuery<User> query = JPA.em().createQuery(queryStr,
				User.class);
		// There can be only one user with this email
		query.setMaxResults(1);
		query.setParameter("email", email);
		query.setParameter("passwordHash", passwordHash);
		List<User> userList = query.getResultList();
		return userList.isEmpty() ? null : userList.get(0);
	}

	public User findByEmail(String email) {
		return JPA.em().find(User.class, email);
	}

	public List<User> findAll() {
		TypedQuery<User> query = JPA.em().createQuery(
				"SELECT e FROM User e", User.class);
		return query.getResultList();
	}

}
