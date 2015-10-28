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
		clone.setMinActiveMemberSize(group.getMinActiveMemberSize());
		clone.setMaxActiveMemberSize(group.getMaxActiveMemberSize());
		clone.setMaxTotalMemberSize(group.getMaxTotalMemberSize());
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
		group.setMinActiveMemberSize(studyProperties.getMinActiveMemberSize());
		group.setMaxActiveMemberSize(studyProperties.getMaxActiveMemberSize());
		group.setMaxTotalMemberSize(studyProperties.getMaxTotalMemberSize());
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
		group.setMinActiveMemberSize(studyProperties.getMinActiveMemberSize());
		group.setMaxActiveMemberSize(studyProperties.getMaxActiveMemberSize());
		group.setMaxTotalMemberSize(studyProperties.getMaxTotalMemberSize());
		// Not necessary to persist since study merges it already
		// groupDao.update(group);
	}

	public void bindToProperties(StudyProperties studyProperties, Group group) {
		studyProperties.setGroupId(group.getId());
		studyProperties.setMinActiveMemberSize(group.getMinActiveMemberSize());
		studyProperties.setMaxActiveMemberSize(group.getMaxActiveMemberSize());
		studyProperties.setMaxTotalMemberSize(group.getMaxTotalMemberSize());
	}
	
	public void updateGroup(Group group, Group updatedGroup) {
		if (group == null) {
			group = new Group();
		}
		group.setMinActiveMemberSize(updatedGroup.getMinActiveMemberSize());
		group.setMaxActiveMemberSize(updatedGroup.getMaxActiveMemberSize());
		group.setMaxTotalMemberSize(updatedGroup.getMaxTotalMemberSize());
		// Not necessary to persist since study merges it already
		// groupDao.update(group);
	}

}
