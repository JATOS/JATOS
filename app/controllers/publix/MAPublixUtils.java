package controllers.publix;

import models.StudyModel;
import models.UserModel;
import models.workers.MAWorker;
import services.ErrorMessages;
import services.MAErrorMessages;
import controllers.Users;
import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.NotFoundPublixException;

/**
 * Special PublixUtils for MAPublix (studies or components started via JATOS'
 * UI).
 * 
 * @author Kristian Lange
 */
public class MAPublixUtils extends PublixUtils<MAWorker> {

	private MAErrorMessages errorMessages;

	public MAPublixUtils(MAErrorMessages errorMessages) {
		super(errorMessages);
		this.errorMessages = errorMessages;
	}

	@Override
	public MAWorker retrieveWorker() throws BadRequestPublixException,
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
	public void checkWorkerAllowedToDoStudy(MAWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		UserModel loggedInUser = worker.getUser();
		if (!study.hasMember(loggedInUser)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
	}

}
