package services.gui;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Group;
import daos.common.GroupDao;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class GroupService {

	private final GroupDao groupDao;

	@Inject
	GroupService(GroupDao groupDao) {
		this.groupDao = groupDao;
	}

	/**
	 * Clones a Group and persists
	 */
	public Group clone(Group group) {
		Group clone = new Group();
		clone.setMinActiveMemberSize(group.getMinActiveMemberSize());
		clone.setMaxActiveMemberSize(group.getMaxActiveMemberSize());
		clone.setMaxTotalMemberSize(group.getMaxTotalMemberSize());
		groupDao.create(clone);
		return clone;
	}
	
	public Group createDefaultGroup() {
		Group defaultGroup = new Group();
		defaultGroup.setTitle("Default");
		defaultGroup.setMessaging(false);
		defaultGroup.setMinActiveMemberSize(1);
		defaultGroup.setMaxActiveMemberSize(null);
		defaultGroup.setMaxTotalMemberSize(null);
		groupDao.create(defaultGroup);
		return defaultGroup;
	}

	// /**
	// * Update group's fields with the ones from updatedGroup and persist the
	// * group. Doesn't change the group's study.
	// *
	// */
	// public void bindToGroup(Group group, StudyProperties studyProperties) {
	// group.setMinActiveMemberSize(studyProperties.getMinActiveMemberSize());
	// group.setMaxActiveMemberSize(studyProperties.getMaxActiveMemberSize());
	// group.setMaxTotalMemberSize(studyProperties.getMaxTotalMemberSize());
	// // Not necessary to persist since study merges it already
	// // groupDao.update(group);
	// }
	//
	// public void bindToProperties(StudyProperties studyProperties, Group
	// group) {
	// studyProperties.setGroupId(group.getId());
	// studyProperties.setMinActiveMemberSize(group.getMinActiveMemberSize());
	// studyProperties.setMaxActiveMemberSize(group.getMaxActiveMemberSize());
	// studyProperties.setMaxTotalMemberSize(group.getMaxTotalMemberSize());
	// }

	// public void updateGroup(Group group, Group updatedGroup) {
	// if (group == null) {
	// group = new Group();
	// }
	// group.setMinActiveMemberSize(updatedGroup.getMinActiveMemberSize());
	// group.setMaxActiveMemberSize(updatedGroup.getMaxActiveMemberSize());
	// group.setMaxTotalMemberSize(updatedGroup.getMaxTotalMemberSize());
	// // Not necessary to persist since study merges it already
	// // groupDao.update(group);
	// }

}
