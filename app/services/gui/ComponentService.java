package services.gui;

import models.ComponentModel;
import models.UserModel;
import play.mvc.Controller;
import play.mvc.Http;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.gui.JatosGuiException;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class ComponentService extends Controller {

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;

	@Inject
	ComponentService(JatosGuiExceptionThrower jatosGuiExceptionThrower) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
	}

	/**
	 * Clones a ComponentModel. Does not clone id, uuid, or date.
	 */
	public ComponentModel clone(ComponentModel component) {
		ComponentModel clone = new ComponentModel();
		clone.setStudy(component.getStudy());
		clone.setTitle(component.getTitle());
		clone.setHtmlFilePath(component.getHtmlFilePath());
		clone.setReloadable(component.isReloadable());
		clone.setActive(component.isActive());
		clone.setJsonData(component.getJsonData());
		clone.setComments(component.getComments());
		return clone;
	}

	/**
	 * Checks the component of this study and throws a JatosGuiException in case
	 * of a problem. Distinguishes between normal and Ajax request.
	 */
	public void checkStandardForComponents(Long studyId, Long componentId,
			UserModel loggedInUser, ComponentModel component)
			throws JatosGuiException {
		if (component == null) {
			String errorMsg = MessagesStrings.componentNotExist(componentId);
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		// Check component belongs to the study
		if (!component.getStudy().getId().equals(studyId)) {
			String errorMsg = MessagesStrings.componentNotBelongToStudy(
					studyId, componentId);
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
	}

}
