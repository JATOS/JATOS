package utils.common;

import java.io.IOException;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.StudyDao;
import models.common.Component;
import models.common.Study;

@Singleton
public class StudyCloner {

	private final StudyDao studyDao;
	private final ComponentCloner componentCloner;
	private final IOUtils ioUtils;

	@Inject
	StudyCloner(StudyDao studyDao, ComponentCloner componentCloner,
			IOUtils ioUtils) {
		this.studyDao = studyDao;
		this.componentCloner = componentCloner;
		this.ioUtils = ioUtils;
	}

	/**
	 * Clones the given Study. Does not clone id, uuid, or date. Generates a new
	 * UUID for the clone. Copies the corresponding study assets. Does not
	 * persist the clone.
	 */
	public Study clone(Study study) throws IOException {
		Study clone = new Study();
		// Generate new UUID for clone
		clone.setUuid(UUID.randomUUID().toString());
		clone.setTitle(cloneTitle(study.getTitle()));
		clone.setDescription(study.getDescription());
		clone.setDirName(study.getDirName());
		clone.setComments(study.getComments());
		clone.setJsonData(study.getJsonData());

		clone.setLocked(false);
		study.getAllowedWorkerTypeList().forEach(clone::addAllowedWorkerType);

		// Clone each component
		for (Component component : study.getComponentList()) {
			Component componentClone = componentCloner.clone(component);
			componentClone.setStudy(clone);
			clone.addComponent(componentClone);
		}

		// Clone assets directory
		String destDirName = ioUtils
				.cloneStudyAssetsDirectory(study.getDirName());
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
