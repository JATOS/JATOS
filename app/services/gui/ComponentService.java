package services.gui;

import java.io.IOException;
import java.util.Map;

import models.ComponentModel;
import models.UserModel;
import persistance.ComponentDao;
import play.Logger;
import services.RequestScopeMessaging;
import utils.IOUtils;
import utils.JsonUtils;

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

	private static final String CLASS_NAME = ComponentService.class
			.getSimpleName();

	private final ComponentDao componentDao;

	@Inject
	ComponentService(ComponentDao componentDao) {
		this.componentDao = componentDao;
	}

	/**
	 * Clones a ComponentModel. Does not clone id, uuid, or date. Does not
	 * persist the clone. Does not clone the HTML file.
	 */
	public ComponentModel cloneComponentModel(ComponentModel component) {
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
	 * Does the same as {@link #cloneComponentModel(ComponentModel)
	 * cloneComponentModel} and additionally clones the HTML file and changes
	 * the title.
	 */
	public ComponentModel cloneComponent(ComponentModel component) {
		ComponentModel clone = cloneComponentModel(component);
		clone.setTitle(cloneTitle(component.getTitle()));
		try {
			String clonedHtmlFileName = IOUtils.cloneComponentHtmlFile(
					component.getStudy().getDirName(),
					component.getHtmlFilePath());
			clone.setHtmlFilePath(clonedHtmlFileName);
		} catch (IOException e) {
			// Just log it - a component is allowed to have no HTML file
			RequestScopeMessaging.warning(MessagesStrings
					.componentCloneHtmlNotCloned(component.getHtmlFilePath()));
			Logger.info(CLASS_NAME + ".cloneComponent: " + e.getMessage());
		}
		return clone;
	}

	/**
	 * Generates an title for the cloned study that doesn't exist so far
	 */
	private String cloneTitle(String origTitle) {
		String cloneTitle = origTitle + " (clone)";
		int i = 2;
		while (!componentDao.findByTitle(cloneTitle).isEmpty()) {
			cloneTitle = origTitle + " (clone " + i + ")";
			i++;
		}
		return cloneTitle;
	}

	/**
	 * Binds component data from a edit/create component request onto a
	 * ComponentModel. Play's default form binder doesn't work here.
	 */
	public ComponentModel bindComponentFromRequest(Map<String, String[]> formMap) {
		ComponentModel component = new ComponentModel();
		component.setTitle(formMap.get(ComponentModel.TITLE)[0]);
		component
				.setHtmlFilePath(formMap.get(ComponentModel.HTML_FILE_PATH)[0]);
		component.setReloadable(Boolean.parseBoolean(formMap
				.get(ComponentModel.RELOADABLE)[0]));
		component.setComments(formMap.get(ComponentModel.COMMENTS)[0]);
		component.setJsonData(JsonUtils.asStringForDB(formMap
				.get(ComponentModel.JSON_DATA)[0]));
		return component;
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
