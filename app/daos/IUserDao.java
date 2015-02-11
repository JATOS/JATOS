package daos;

import java.util.List;

import models.UserModel;

/**
 * Interface for DAO of User model
 * 
 * @author Kristian Lange
 */
public interface IUserDao {

	/**
	 * Persist user und creates it's JatosWorker.
	 */
	public abstract void addUser(UserModel user);

	/**
	 * Changes name of user.
	 */
	public abstract void updateUser(UserModel user, String name);

	public abstract UserModel authenticate(String email, String passwordHash);

	public abstract UserModel findByEmail(String email);

	public abstract List<UserModel> findAll();

}