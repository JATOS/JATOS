package services;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.GroupModel;
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
	 * Clones a GroupModel.
	 */
	public GroupModel clone(GroupModel group) {
		GroupModel clone = new GroupModel();
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
	public void updateProperties(GroupModel group, GroupModel updatedGroup) {
		group.setMinMemberSize(updatedGroup.getMinMemberSize());
		group.setMaxMemberSize(updatedGroup.getMaxMemberSize());
		group.setMaxWorkerSize(updatedGroup.getMaxWorkerSize());
		groupDao.update(group);
	}

	public GroupModel bindFromRequest(Map<String, String[]> formMap) {
		GroupModel group = new GroupModel();
		group.setMinMemberSize(Integer.parseInt(formMap
				.get(GroupModel.MIN_MEMBER_SIZE)[0]));
		group.setMaxMemberSize(Integer.parseInt(formMap
				.get(GroupModel.MAX_MEMBER_SIZE)[0]));
		group.setMaxWorkerSize(Integer.parseInt(formMap
				.get(GroupModel.MAX_WORKER_SIZE)[0]));
		return group;
	}

}
