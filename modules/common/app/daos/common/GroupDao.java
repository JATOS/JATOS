package daos.common;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Group;
import play.db.jpa.JPA;

/**
 * DAO of Group entity
 * 
 * @author Kristian Lange
 */
@Singleton
public class GroupDao extends AbstractDao {

	private final GroupResultDao groupResultDao;

	@Inject
	GroupDao(GroupResultDao groupResultDao) {
		this.groupResultDao = groupResultDao;
	}

	public void create(Group group) {
		persist(group);
	}

	public void update(Group group) {
		merge(group);
	}

	/**
	 * Remove group and its group results
	 */
	public void remove(Group group) {
		// Remove group's GroupResults
		groupResultDao.findAllByGroup(group).forEach(groupResultDao::remove);
		super.remove(group);
	}

	public Group findById(Long id) {
		return JPA.em().find(Group.class, id);
	}

}
