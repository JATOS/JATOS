package daos;

import java.util.List;

import javax.persistence.TypedQuery;

import models.UserModel;
import models.workers.JatosWorker;
import play.db.jpa.JPA;

import com.google.inject.Singleton;

/**
 * DAO for UserModel
 * 
 * @author Kristian Lange
 */
@Singleton
public class UserDao extends AbstractDao<UserModel> implements IUserDao {

	@Override
	public void create(UserModel user) {
		JatosWorker worker = new JatosWorker(user);
		persist(worker);
		user.setWorker(worker);
		persist(user);
		merge(worker);
	}

	@Override
	public void update(UserModel user) {
		merge(user);
	}

	@Override
	public void updateName(UserModel user, String name) {
		user.setName(name);
		merge(user);
	}

	@Override
	public UserModel authenticate(String email, String passwordHash) {
		String queryStr = "SELECT e FROM UserModel e WHERE "
				+ "e.email=:email and e.passwordHash=:passwordHash";
		TypedQuery<UserModel> query = JPA.em().createQuery(queryStr,
				UserModel.class);
		List<UserModel> userList = query.setParameter("email", email)
				.setParameter("passwordHash", passwordHash).getResultList();
		return userList.isEmpty() ? null : userList.get(0);
	}

	@Override
	public UserModel findByEmail(String email) {
		return JPA.em().find(UserModel.class, email);
	}

	@Override
	public List<UserModel> findAll() {
		TypedQuery<UserModel> query = JPA.em().createQuery(
				"SELECT e FROM UserModel e", UserModel.class);
		return query.getResultList();
	}

}
