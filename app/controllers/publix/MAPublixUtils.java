package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import models.results.ComponentResult.ComponentState;
import models.results.StudyResult;
import models.workers.MAWorker;
import services.ErrorMessages;
import services.MAErrorMessages;
import services.PersistanceUtils;

import com.google.common.net.MediaType;

import controllers.Users;
import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.NotFoundPublixException;
import exceptions.PublixException;

/**
 * Special PublixUtils for MAPublix (studies or components started via MechArg's
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
		return retrieveWorker(MediaType.HTML_UTF_8);
	}

	@Override
	public MAWorker retrieveWorker(MediaType errorMediaType)
			throws BadRequestPublixException, NotFoundPublixException {
		String email = Publix.session(Users.COOKIE_EMAIL);
		if (email == null) {
			throw new BadRequestPublixException(
					ErrorMessages.NO_USER_LOGGED_IN, errorMediaType);
		}
		UserModel loggedInUser = UserModel.findByEmail(email);
		if (loggedInUser == null) {
			throw new NotFoundPublixException(
					ErrorMessages.userNotExist(email), errorMediaType);
		}
		return loggedInUser.getWorker();
	}

	@Override
	public StudyResult retrieveWorkersStartedStudyResult(MAWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		return retrieveWorkersStartedStudyResult(worker, study,
				MediaType.HTML_UTF_8);
	}

	@Override
	public StudyResult retrieveWorkersStartedStudyResult(MAWorker worker,
			StudyModel study, MediaType mediaType)
			throws ForbiddenPublixException {
		StudyResult studyResult = null;
		try {
			studyResult = super.retrieveWorkersStartedStudyResult(worker,
					study, mediaType);
		} catch (PublixException e) {
			// Do nothing
		}

		String mechArgShow = retrieveMechArgShow(mediaType);
		if (studyResult == null) {
			if (mechArgShow.equals(StudyModel.STUDY)) {
				throw new ForbiddenPublixException(
						errorMessages.workerNeverStartedStudy(worker,
								study.getId()), mediaType);
			}
			if (mechArgShow.equals(ComponentModel.COMPONENT)) {
				// Show of a single component: Just create a StudyResult for
				// this. The StudyResult will have only one ComponentResult.
				studyResult = PersistanceUtils.createStudyResult(study, worker);
			}
		}
		return studyResult;
	}

	@Override
	public ComponentResult retrieveStartedComponentResult(
			ComponentModel component, StudyResult studyResult,
			ComponentState maxAllowedComponentState)
			throws ForbiddenPublixException {
		return retrieveStartedComponentResult(component, studyResult,
				maxAllowedComponentState, MediaType.HTML_UTF_8);
	}

	@Override
	public ComponentResult retrieveStartedComponentResult(
			ComponentModel component, StudyResult studyResult,
			ComponentState maxAllowedComponentState, MediaType mediaType)
			throws ForbiddenPublixException {
		ComponentResult componentResult = null;
		try {
			componentResult = super.retrieveStartedComponentResult(component,
					studyResult, maxAllowedComponentState, mediaType);
		} catch (ForbiddenPublixException e) {
			exceptionalFinishStudy(studyResult);
			throw e;
		}
		return componentResult;
	}

}
