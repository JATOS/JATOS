package controllers.publix;

import models.StudyModel;
import models.UserModel;
import models.workers.JatosWorker;
import services.ErrorMessages;
import services.MAErrorMessages;
import controllers.Users;
import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.NotFoundPublixException;

/**
 * Special PublixUtils for JatosPublix (studies or components started via JATOS'
 * UI).
 * 
 * @author Kristian Lange
 */
public class JatosPublixUtils extends PublixUtils<JatosWorker> {

	private MAErrorMessages errorMessages;

	public JatosPublixUtils(MAErrorMessages errorMessages) {
		super(errorMessages);
		this.errorMessages = errorMessages;
	}

	@Override
	public JatosWorker retrieveWorker() throws BadRequestPublixException,
			NotFoundPublixException {
		String email = Publix.session(Users.COOKIE_EMAIL);
		if (email == null) {
			throw new BadRequestPublixException(ErrorMessages.NO_USER_LOGGED_IN);
		}
		UserModel loggedInUser = UserModel.findByEmail(email);
		if (loggedInUser == null) {
			throw new NotFoundPublixException(ErrorMessages.userNotExist(email));
		}
		return loggedInUser.getWorker();
	}

	@Override
	public void checkWorkerAllowedToDoStudy(JatosWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		UserModel loggedInUser = worker.getUser();
		if (!study.hasMember(loggedInUser)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
	}

}
