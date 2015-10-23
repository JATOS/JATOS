package services;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.Group;
import persistance.GroupDao;

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
	 * Clones a Group
	 */
	public Group clone(Group group) {
		Group clone = new Group();
		clone.setMinMemberSize(group.getMinMemberSize());
		clone.setMaxMemberSize(group.getMaxMemberSize());
		clone.setMaxWorkerSize(group.getMaxWorkerSize());
		clone.setStudy(group.getStudy());
		return clone;
	}

	/**
	 * Update group's fields with the ones from updatedGroup and persist the
	 * group. Doesn't change the group's study.
	 * 
	 */
	public void updateProperties(Group group, Group updatedGroup) {
		group.setMinMemberSize(updatedGroup.getMinMemberSize());
		group.setMaxMemberSize(updatedGroup.getMaxMemberSize());
		group.setMaxWorkerSize(updatedGroup.getMaxWorkerSize());
		groupDao.update(group);
	}

	public Group bindFromRequest(Map<String, String[]> formMap) {
		Group group = new Group();
		group.setMinMemberSize(Integer.parseInt(formMap
				.get(Group.MIN_MEMBER_SIZE)[0]));
		group.setMaxMemberSize(Integer.parseInt(formMap
				.get(Group.MAX_MEMBER_SIZE)[0]));
		group.setMaxWorkerSize(Integer.parseInt(formMap
				.get(Group.MAX_WORKER_SIZE)[0]));
		return group;
	}

}
