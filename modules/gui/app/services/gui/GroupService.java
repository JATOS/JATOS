package services.gui;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.GroupDao;
import models.common.Group;
import models.common.workers.JatosWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.gui.GroupProperties;

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
		group.getAllowedWorkerTypes().forEach(clone::addAllowedWorkerType);
		return clone;
	}

	/**
	 * Add default allowed workers
	 */
	public void initGroup(Group group) {
		group.addAllowedWorkerType(JatosWorker.WORKER_TYPE);
		group.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		group.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
	}

	public void updateGroup(Group group, Group updatedGroup) {
		group.setMinActiveMemberSize(updatedGroup.getMinActiveMemberSize());
		group.setMaxActiveMemberSize(updatedGroup.getMaxActiveMemberSize());
		group.setMaxTotalMemberSize(updatedGroup.getMaxTotalMemberSize());
		group.getAllowedWorkerTypes().clear();
		updatedGroup.getAllowedWorkerTypes()
				.forEach(group::addAllowedWorkerType);
		groupDao.update(group);
	}

	public GroupProperties bindToGroupProperties(Group group) {
		GroupProperties props = new GroupProperties();
		group.getAllowedWorkerTypes().forEach(props::addAllowedWorkerType);
		props.setId(group.getId());
		props.setMaxActiveMemberSize(group.getMaxActiveMemberSize());
		props.setMaxActiveMemberLimited(group.getMaxActiveMemberSize() != null);
		props.setMaxTotalMemberSize(group.getMaxTotalMemberSize());
		props.setMaxTotalMemberLimited(group.getMaxTotalMemberSize() != null);
		props.setMinActiveMemberSize(group.getMinActiveMemberSize());
		return props;
	}

	public Group bindToGroup(GroupProperties props) {
		Group group = new Group();
		props.getAllowedWorkerTypes().forEach(group::addAllowedWorkerType);
		if (props.isMaxActiveMemberLimited()) {
			group.setMaxActiveMemberSize(props.getMaxActiveMemberSize());
		} else {
			group.setMaxActiveMemberSize(null);
		}
		if (props.isMaxTotalMemberLimited()) {
			group.setMaxTotalMemberSize(props.getMaxTotalMemberSize());
		} else {
			group.setMaxTotalMemberSize(null);
		}
		group.setMinActiveMemberSize(props.getMinActiveMemberSize());
		return group;
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

}
