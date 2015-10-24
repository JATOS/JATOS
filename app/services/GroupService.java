package services;

import javax.inject.Singleton;

import models.Group;
import models.Study;
import models.StudyProperties;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class GroupService {

	/**
	 * Clones a Group and persists
	 */
	public Group clone(Group group, Study studyClone) {
		Group clone = new Group();
		clone.setMinMemberSize(group.getMinMemberSize());
		clone.setMaxMemberSize(group.getMaxMemberSize());
		clone.setMaxWorkerSize(group.getMaxWorkerSize());
		clone.setStudy(studyClone);
		// Not necessary to persist since study creates it already
		// groupDao.create(clone);
		return clone;
	}

	/**
	 * Create and persist a new Group with the given parameters.
	 */
	public Group createGroup(StudyProperties studyProperties, Study study) {
		Group group = new Group();
		group.setMinMemberSize(studyProperties.getMinMemberSize());
		group.setMaxMemberSize(studyProperties.getMaxMemberSize());
		group.setMaxWorkerSize(studyProperties.getMaxWorkerSize());
		group.setStudy(study);
		// Not necessary to persist since study creates it already
		// groupDao.create(group);
		return group;
	}

	/**
	 * Update group's fields with the ones from updatedGroup and persist the
	 * group. Doesn't change the group's study.
	 * 
	 */
	public void bindToGroup(Group group, StudyProperties studyProperties) {
		group.setMinMemberSize(studyProperties.getMinMemberSize());
		group.setMaxMemberSize(studyProperties.getMaxMemberSize());
		group.setMaxWorkerSize(studyProperties.getMaxWorkerSize());
		// Not necessary to persist since study merges it already
		// groupDao.update(group);
	}

	public void bindToProperties(StudyProperties studyProperties, Group group) {
		studyProperties.setGroupId(group.getId());
		studyProperties.setMinMemberSize(group.getMinMemberSize());
		studyProperties.setMaxMemberSize(group.getMaxMemberSize());
		studyProperties.setMaxWorkerSize(group.getMaxWorkerSize());
	}
	
	public void updateGroup(Group group, Group updatedGroup) {
		if (group == null) {
			group = new Group();
		}
		group.setMinMemberSize(updatedGroup.getMinMemberSize());
		group.setMaxMemberSize(updatedGroup.getMaxMemberSize());
		group.setMaxWorkerSize(updatedGroup.getMaxWorkerSize());
		// Not necessary to persist since study merges it already
		// groupDao.update(group);
	}

}
