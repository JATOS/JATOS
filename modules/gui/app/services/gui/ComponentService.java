package services.gui;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;

import models.common.Component;
import play.Logger;
import play.data.validation.ValidationError;
import utils.common.ComponentCloner;
import utils.common.IOUtils;
import utils.common.JsonUtils;
import daos.common.ComponentDao;
import exceptions.gui.BadRequestException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;

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
	private final ComponentCloner componentCloner;

	@Inject
	ComponentService(ComponentDao componentDao, ComponentCloner componentCloner) {
		this.componentDao = componentDao;
		this.componentCloner = componentCloner;
	}

	/**
	 * Update component's properties with the ones from updatedComponent.
	 */
	public void updateProperties(Component component, Component updatedComponent) {
		component.setTitle(updatedComponent.getTitle());
		component.setReloadable(updatedComponent.isReloadable());
		component.setHtmlFilePath(updatedComponent.getHtmlFilePath());
		component.setComments(updatedComponent.getComments());
		component.setJsonData(updatedComponent.getJsonData());
		component.setActive(updatedComponent.isActive());
		componentDao.update(component);
	}

	/**
	 * Update component's properties with the ones from updatedComponent, but
	 * not htmlFilePath and not active.
	 */
	public void updateComponentAfterEdit(Component component,
			Component updatedComponent) {
		component.setTitle(updatedComponent.getTitle());
		component.setReloadable(updatedComponent.isReloadable());
		component.setComments(updatedComponent.getComments());
		component.setJsonData(updatedComponent.getJsonData());
		componentDao.update(component);
	}
	
	/**
	 * Does the same as {@link #clone(Component) cloneComponent}
	 * and additionally clones the HTML file and changes the title.
	 */
	public Component cloneWholeComponent(Component component) {
		Component clone = componentCloner.clone(component);
		clone.setTitle(componentCloner.cloneTitle(component.getTitle()));
		try {
			String clonedHtmlFileName = IOUtils.cloneComponentHtmlFile(
					component.getStudy().getDirName(),
					component.getHtmlFilePath());
			clone.setHtmlFilePath(clonedHtmlFileName);
		} catch (IOException e) {
			// Just log it and give a warning - a component is allowed to have
			// no HTML file
			RequestScopeMessaging.warning(MessagesStrings
					.componentCloneHtmlNotCloned(component.getHtmlFilePath()));
			Logger.info(CLASS_NAME + ".cloneComponent: " + e.getMessage());
		}
		return clone;
	}

	/**
	 * Binds component data from a edit/create component request onto a
	 * Component. Play's default form binder doesn't work here.
	 */
	public Component bindComponentFromRequest(Map<String, String[]> formMap) {
		Component component = new Component();
		component.setTitle(formMap.get(Component.TITLE)[0]);
		component.setHtmlFilePath(formMap.get(Component.HTML_FILE_PATH)[0]);
		component.setReloadable(Boolean.parseBoolean(formMap
				.get(Component.RELOADABLE)[0]));
		component.setComments(formMap.get(Component.COMMENTS)[0]);
		component.setJsonData(JsonUtils.asStringForDB(formMap
				.get(Component.JSON_DATA)[0]));
		return component;
	}

	/**
	 * Renames the path to the HTML file in the file system and persists the
	 * component's property.
	 */
	public void renameHtmlFilePath(Component component, String newHtmlFilePath)
			throws IOException {

		// If the new HTML file name is empty persist an empty string
		if (newHtmlFilePath == null || newHtmlFilePath.trim().isEmpty()) {
			component.setHtmlFilePath("");
			componentDao.update(component);
			return;
		}

		// What if current HTML file doesn't exist
		File currentFile = null;
		if (!component.getHtmlFilePath().trim().isEmpty()) {
			currentFile = IOUtils.getFileInStudyAssetsDir(component.getStudy()
					.getDirName(), component.getHtmlFilePath());
		}
		if (currentFile == null || !currentFile.exists()) {
			component.setHtmlFilePath(newHtmlFilePath);
			componentDao.update(component);
			return;
		}

		// Rename HTML file
		IOUtils.renameHtmlFile(component.getHtmlFilePath(), newHtmlFilePath,
				component.getStudy().getDirName());
		component.setHtmlFilePath(newHtmlFilePath);
		componentDao.update(component);
	}

	/**
	 * Checks the component of this study and throws an Exception in case of a
	 * problem.
	 */
	public void checkStandardForComponents(Long studyId, Long componentId,
			Component component) throws BadRequestException {
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

	/**
	 * Validates the component by using the Component's model validation method.
	 * Throws ValidationException in case of an error.
	 */
	public void validate(Component component) throws ValidationException {
		if (component.validate() != null) {
			Logger.warn(CLASS_NAME
					+ ".validate: "
					+ component.validate().stream()
							.map(ValidationError::message)
							.collect(Collectors.joining(", ")));
			throw new ValidationException(MessagesStrings.COMPONENT_INVALID);
		}
	}

}
