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
public class UserDao extends AbstractDao implements IUserDao {

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IUserDao#addUser(models.UserModel)
	 */
	@Override
	public void addUser(UserModel user) {
		JatosWorker worker = new JatosWorker(user);
		persist(worker);
		user.setWorker(worker);
		persist(user);
		merge(worker);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IUserDao#updateUser(models.UserModel, java.lang.String)
	 */
	@Override
	public void updateUser(UserModel user, String name) {
		user.setName(name);
		merge(user);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IUserDao#authenticate(java.lang.String, java.lang.String)
	 */
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IUserDao#findByEmail(java.lang.String)
	 */
	@Override
	public UserModel findByEmail(String email) {
		return JPA.em().find(UserModel.class, email);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IUserDao#findAll()
	 */
	@Override
	public List<UserModel> findAll() {
		TypedQuery<UserModel> query = JPA.em().createQuery(
				"SELECT e FROM UserModel e", UserModel.class);
		return query.getResultList();
	}

}
