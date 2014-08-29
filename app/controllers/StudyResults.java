package controllers;

import models.StudyModel;
import models.UserModel;
import models.results.StudyResult;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import services.ErrorMessages;
import services.Persistance;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class StudyResults extends Controller {

	private static final String CLASS_NAME = StudyResults.class.getSimpleName();

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result remove(Long studyResultId) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: studyResultId " + studyResultId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			throw new ResultException(redirect(routes.Authentication.login()));
		}

		StudyResult studyResult = StudyResult.findById(studyResultId);
		if (studyResult == null) {
			return badRequest(ErrorMessages.studyResultNotExist(studyResultId));
		}
		// Check that logged-in user is member of the study
		StudyModel study = studyResult.getStudy();
		if (!study.hasMember(loggedInUser)) {
			return badRequest(ErrorMessages.notMember(loggedInUser.getName(),
					loggedInUser.getEmail(), study.getId(), study.getTitle()));
		}
		
		Persistance.removeStudyResult(studyResult);
		return ok();
	}

}
