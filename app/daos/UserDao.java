package daos;

import java.util.List;

import javax.persistence.TypedQuery;

import com.google.inject.Singleton;

import models.UserModel;
import play.db.jpa.JPA;

/**
 * DAO for UserModel
 * 
 * @author Kristian Lange
 */
@Singleton
public class UserDao extends AbstractDao<UserModel> {

	public UserModel authenticate(String email, String passwordHash) {
		String queryStr = "SELECT e FROM UserModel e WHERE "
				+ "e.email=:email and e.passwordHash=:passwordHash";
		TypedQuery<UserModel> query = JPA.em().createQuery(queryStr,
				UserModel.class);
		List<UserModel> userList = query.setParameter("email", email)
				.setParameter("passwordHash", passwordHash).getResultList();
		return userList.isEmpty() ? null : userList.get(0);
	}

	public UserModel findByEmail(String email) {
		return JPA.em().find(UserModel.class, email);
	}

	public List<UserModel> findAll() {
		TypedQuery<UserModel> query = JPA.em().createQuery(
				"SELECT e FROM UserModel e", UserModel.class);
		return query.getResultList();
	}

}
