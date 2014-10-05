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
			throw new BadRequestPublixException(ErrorMessages.noUserLoggedIn(),
					errorMediaType);
		}
		UserModel loggedInUser = UserModel.findByEmail(email);
		if (loggedInUser == null) {
			throw new NotFoundPublixException(ErrorMessages.userNotExist(email),
					errorMediaType);
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
			studyResult = super.retrieveWorkersStartedStudyResult(
					worker, study, mediaType);
		} catch (PublixException e) {
			// Do nothing
		}
		
		String mechArgTry = retrieveMechArgTry(mediaType);
		if (studyResult == null) {
			if (mechArgTry.equals(StudyModel.STUDY)) {
				throw new ForbiddenPublixException(
						errorMessages.workerNeverStartedStudy(worker,
								study.getId()), mediaType);
			}
			if (mechArgTry.equals(ComponentModel.COMPONENT)) {
				// Try-out of a single component: Just create a StudyResult for
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

	public void checkMembership(StudyModel study, UserModel loggedInUser)
			throws ForbiddenPublixException {
		checkMembership(study, loggedInUser, MediaType.HTML_UTF_8);
	}

	public void checkMembership(StudyModel study, UserModel loggedInUser,
			MediaType errorMediaType) throws ForbiddenPublixException {
		if (!study.hasMember(loggedInUser)) {
			throw new ForbiddenPublixException(ErrorMessages.notMember(
					loggedInUser.getName(), loggedInUser.getEmail(),
					study.getId(), study.getTitle()), errorMediaType);
		}
	}

	public String retrieveMechArgTry() throws ForbiddenPublixException {
		return retrieveMechArgTry(MediaType.HTML_UTF_8);
	}

	public String retrieveMechArgTry(MediaType mediaType)
			throws ForbiddenPublixException {
		String mechArgTry = Publix.session(MAPublix.MECHARG_TRY);
		if (mechArgTry == null) {
			throw new ForbiddenPublixException(ErrorMessages.noMechArgTry(),
					mediaType);
		}
		return mechArgTry;
	}

}
