package services.gui;

import models.ComponentModel;
import models.UserModel;
import persistance.ComponentDao;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.BadRequestException;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class ComponentService {

	private final ComponentDao componentDao;

	@Inject
	ComponentService(ComponentDao componentDao) {
		this.componentDao = componentDao;
	}

	/**
	 * Clones a ComponentModel. Does not clone id, uuid, or date. Does not
	 * persist the clone.
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
	 * Updates some but not all fields of a ComponentModel and persists it.
	 */
	public void updateComponentAfterEdit(ComponentModel component,
			ComponentModel updatedComponent) {
		component.setTitle(updatedComponent.getTitle());
		component.setReloadable(updatedComponent.isReloadable());
		component.setHtmlFilePath(updatedComponent.getHtmlFilePath());
		component.setComments(updatedComponent.getComments());
		component.setJsonData(updatedComponent.getJsonData());
		componentDao.update(component);
	}

	/**
	 * Checks the component of this study and throws an Exception in case of a
	 * problem.
	 */
	public void checkStandardForComponents(Long studyId, Long componentId,
			UserModel loggedInUser, ComponentModel component)
			throws BadRequestException {
		if (component == null) {
			throw new BadRequestException(
					MessagesStrings.componentNotExist(componentId));
		}
		if (component.getStudy() == null) {
			throw new BadRequestException(
					MessagesStrings.componentHasNoStudy(componentId));
		}
		// Check component belongs to the study
		if (!component.getStudy().getId().equals(studyId)) {
			throw new BadRequestException(
					MessagesStrings.componentNotBelongToStudy(studyId,
							componentId));
		}
	}

}
