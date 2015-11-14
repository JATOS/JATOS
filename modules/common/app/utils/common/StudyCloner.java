package utils.common;

import java.io.IOException;

import javax.inject.Inject;

import models.common.Component;
import models.common.Study;
import models.common.User;
import daos.common.StudyDao;

public class StudyCloner {

	private final StudyDao studyDao;
	private final ComponentCloner componentCloner;

	@Inject
	StudyCloner(StudyDao studyDao, ComponentCloner componentCloner) {
		this.studyDao = studyDao;
		this.componentCloner = componentCloner;
	}

	/**
	 * Clones the given Study and persists it. Copies the corresponding study
	 * assets.
	 */
	public Study clone(Study study, User loggedInUser) throws IOException {
		Study clone = new Study();
		clone.setTitle(cloneTitle(study.getTitle()));
		clone.setDescription(study.getDescription());
		clone.setDirName(study.getDirName());
		clone.setComments(study.getComments());
		clone.setJsonData(study.getJsonData());

		clone.setLocked(false);
		study.getAllowedWorkerTypeList().forEach(clone::addAllowedWorkerType);

		// Clone each component
		for (Component component : study.getComponentList()) {
			Component componentClone = componentCloner
					.clone(component);
			componentClone.setStudy(clone);
			clone.addComponent(componentClone);
		}

		// Clone assets directory
		String destDirName = IOUtils.cloneStudyAssetsDirectory(study
				.getDirName());
		clone.setDirName(destDirName);

		return clone;
	}

	/**
	 * Generates an title for the cloned study by adding '(clone)' and numbers
	 * that doesn't exist so far.
	 */
	private String cloneTitle(String origTitle) {
		String cloneTitle = origTitle + " (clone)";
		int i = 2;
		while (!studyDao.findByTitle(cloneTitle).isEmpty()) {
			cloneTitle = origTitle + " (clone " + i + ")";
			i++;
		}
		return cloneTitle;
	}

}
