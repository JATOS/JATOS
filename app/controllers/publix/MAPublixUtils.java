package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import models.results.StudyResult;
import models.results.StudyResult.StudyState;
import models.workers.MAWorker;

import com.google.common.net.MediaType;

import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;

/**
 * Special PublixUtils for MAPublix (studies or components started via MechArg's
 * UI).
 * 
 * @author madsen
 */
public class MAPublixUtils extends PublixUtils<MAWorker> {

	private MAErrorMessages errorMessages;

	public MAPublixUtils(MAErrorMessages errorMessages, Persistance persistance) {
		super(errorMessages, persistance);
		this.errorMessages = errorMessages;
	}

	@Override
	public MAWorker retrieveWorker() throws Exception {
		return retrieveWorker(MediaType.HTML_UTF_8);
	}

	@Override
	public MAWorker retrieveWorker(MediaType errorMediaType) throws Exception {
		String email = Publix.getLoggedInUserEmail();
		if (email == null) {
			throw new BadRequestPublixException(errorMessages.noUserLoggedIn(),
					errorMediaType);
		}
		UserModel loggedInUser = UserModel.findByEmail(email);
		if (loggedInUser == null) {
			throw new BadRequestPublixException(errorMessages.userNotExists(),
					errorMediaType);
		}
		return loggedInUser.getWorker();
	}

	@Override
	public StudyResult retrieveWorkersStartedStudyResult(MAWorker worker,
			StudyModel study) {
		return retrieveWorkersStartedStudyResult(worker, study,
				MediaType.HTML_UTF_8);
	}

	@Override
	public StudyResult retrieveWorkersStartedStudyResult(MAWorker worker,
			StudyModel study, MediaType mediaType) {
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().getId() == study.getId()
					&& studyResult.getStudyState() == StudyState.STARTED) {
				// Since there is only one study result of the same study
				// allowed to be in STARTED, return the first one
				return studyResult;
			}
		}
		return null;
	}

	@Override
	public ComponentResult retrieveStartedComponentResult(
			ComponentModel component, StudyResult studyResult)
			throws ForbiddenPublixException {
		return retrieveStartedComponentResult(component, studyResult,
				MediaType.HTML_UTF_8);
	}

	@Override
	public ComponentResult retrieveStartedComponentResult(
			ComponentModel component, StudyResult studyResult,
			MediaType mediaType) throws ForbiddenPublixException {
		ComponentResult componentResult = null;
		try {
			componentResult = super.retrieveStartedComponentResult(component,
					studyResult, mediaType);
		} catch (ForbiddenPublixException e) {
			exceptionalFinishStudy(studyResult);
			throw e;
		}
		return componentResult;
	}

}
