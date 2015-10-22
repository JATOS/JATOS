package persistance;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.GroupModel;
import play.db.jpa.JPA;

/**
 * DAO of StudyModel.
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

	public void create(GroupModel group) {
		persist(group);
	}

	public void update(GroupModel group) {
		merge(group);
	}

	/**
	 * Remove group and its group results
	 */
	public void remove(GroupModel group) {
		// Remove group's GroupResults
		groupResultDao.findAllByGroup(group).forEach(groupResultDao::remove);
		super.remove(group);
	}

	public GroupModel findById(Long id) {
		return JPA.em().find(GroupModel.class, id);
	}

}
