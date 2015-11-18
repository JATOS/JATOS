package daos.common;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.common.User;
import models.common.workers.JatosWorker;
import play.db.jpa.JPAApi;

/**
 * DAO for User entity
 * 
 * @author Kristian Lange
 */
@Singleton
public class UserDao extends AbstractDao {

	@Inject
	UserDao(JPAApi jpa) {
		super(jpa);
	}

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

	public boolean authenticate(String email, String passwordHash) {
		String queryStr = "SELECT e FROM User e WHERE "
				+ "e.email=:email and e.passwordHash=:passwordHash";
		boolean doesNotExist = em.createQuery(queryStr, User.class)
				.setMaxResults(1).setParameter("email", email)
				.setParameter("passwordHash", passwordHash).getResultList()
				.isEmpty();
		return !doesNotExist;
	}

	public User findByEmail(String email) {
		return em.find(User.class, email);
	}

	public List<User> findAll() {
		TypedQuery<User> query = em().createQuery("SELECT e FROM User e",
				User.class);
		return query.getResultList();
	}

}
