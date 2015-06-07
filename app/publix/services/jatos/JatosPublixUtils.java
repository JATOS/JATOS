package publix.services.jatos;

import models.UserModel;
import models.workers.JatosWorker;
import models.workers.Worker;
import persistance.ComponentDao;
import persistance.ComponentResultDao;
import persistance.StudyDao;
import persistance.StudyResultDao;
import persistance.UserDao;
import persistance.workers.WorkerDao;
import publix.controllers.Publix;
import publix.controllers.jatos.JatosPublix;
import publix.exceptions.ForbiddenPublixException;
import publix.services.PublixUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.Users;

/**
 * JatosPublix' implementation of PublixUtils (studies or components started via
 * JATOS' UI).
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosPublixUtils extends PublixUtils<JatosWorker> {

	private JatosErrorMessages errorMessages;
	private UserDao userDao;

	@Inject
	JatosPublixUtils(JatosErrorMessages errorMessages, UserDao userDao,
			StudyDao studyDao, StudyResultDao studyResultDao,
			ComponentDao componentDao, ComponentResultDao componentResultDao,
			WorkerDao workerDao) {
		super(errorMessages, studyDao, studyResultDao, componentDao,
				componentResultDao, workerDao);
		this.errorMessages = errorMessages;
		this.userDao = userDao;
	}

	@Override
	public JatosWorker retrieveTypedWorker(String workerIdStr)
			throws ForbiddenPublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof JatosWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (JatosWorker) worker;
	}

	/**
	 * Retrieves the currently logged-in user or throws an
	 * ForbiddenPublixException if none is logged-in.
	 */
	public UserModel retrieveLoggedInUser() throws ForbiddenPublixException {
		String email = Publix.session(Users.SESSION_EMAIL);
		if (email == null) {
			throw new ForbiddenPublixException(
					JatosErrorMessages.NO_USER_LOGGED_IN);
		}
		UserModel loggedInUser = userDao.findByEmail(email);
		if (loggedInUser == null) {
			throw new ForbiddenPublixException(
					errorMessages.userNotExist(email));
		}
		return loggedInUser;
	}

	/**
	 * Retrieves the kind of jatos run, whole study or single component, this
	 * is. This information was stored in the session in the prior action.
	 */
	public String retrieveJatosShowFromSession()
			throws ForbiddenPublixException {
		String jatosShow = Publix.session(JatosPublix.JATOS_RUN);
		if (jatosShow == null) {
			throw new ForbiddenPublixException(
					JatosErrorMessages.STUDY_OR_COMPONENT_NEVER_STARTED_FROM_JATOS);
		}
		return jatosShow;
	}

}
