package services.gui;

import javax.inject.Inject;

import models.common.Component;
import daos.common.ComponentDao;

public class ComponentCloner {

	private final ComponentDao componentDao;

	@Inject
	ComponentCloner(ComponentDao componentDao) {
		this.componentDao = componentDao;
	}

	/**
	 * Clones a Component entity. Does not clone id, uuid, or date. Does not
	 * persist the clone. Does not clone the HTML file.
	 */
	public Component clone(Component componentToBeCloned) {
		Component clone = new Component();
		clone.setStudy(componentToBeCloned.getStudy());
		clone.setTitle(componentToBeCloned.getTitle());
		clone.setHtmlFilePath(componentToBeCloned.getHtmlFilePath());
		clone.setReloadable(componentToBeCloned.isReloadable());
		clone.setActive(componentToBeCloned.isActive());
		clone.setJsonData(componentToBeCloned.getJsonData());
		clone.setComments(componentToBeCloned.getComments());
		return clone;
	}

	/**
	 * Generates an title for the cloned study that doesn't exist so far
	 */
	public String cloneTitle(String origTitle) {
		String cloneTitle = origTitle + " (clone)";
		int i = 2;
		while (!componentDao.findByTitle(cloneTitle).isEmpty()) {
			cloneTitle = origTitle + " (clone " + i + ")";
			i++;
		}
		return cloneTitle;
	}

}

