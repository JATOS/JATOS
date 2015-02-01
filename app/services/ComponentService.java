package services;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.mvc.Controller;
import play.mvc.Http;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.JatosGuiException;

/**
 * Utility class for all JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class ComponentService extends Controller {

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;

	@Inject
	public ComponentService(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
	}

	/**
	 * Checks the component of this study and throws a JatosGuiException in case
	 * of a problem. Distinguishes between normal and Ajax request.
	 */
	public void checkStandardForComponents(Long studyId, Long componentId,
			StudyModel study, UserModel loggedInUser, ComponentModel component)
			throws JatosGuiException {
		studyService.checkStandardForStudy(study, studyId, loggedInUser);
		if (component == null) {
			String errorMsg = ErrorMessages.componentNotExist(componentId);
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		// Check component belongs to the study
		if (!component.getStudy().getId().equals(study.getId())) {
			String errorMsg = ErrorMessages.componentNotBelongToStudy(studyId,
					componentId);
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
	}

}
