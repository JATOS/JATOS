package controllers.publix.jatos;

import models.StudyModel;
import models.UserModel;
import models.workers.JatosWorker;
import models.workers.Worker;
import persistance.ComponentDao;
import persistance.ComponentResultDao;
import persistance.StudyDao;
import persistance.StudyResultDao;
import persistance.UserDao;
import persistance.workers.WorkerDao;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.gui.Users;
import controllers.publix.Publix;
import controllers.publix.PublixUtils;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;

/**
 * Special PublixUtils for JatosPublix (studies or components started via JATOS'
 * UI).
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
			throws PublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof JatosWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (JatosWorker) worker;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(JatosWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(JatosWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		UserModel user = worker.getUser();
		// User has to be a member of this study
		if (!study.hasMember(user)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
		// User has to be logged in
		String email = Publix.session(Users.SESSION_EMAIL);
		if (!user.getEmail().equals(email)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
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
