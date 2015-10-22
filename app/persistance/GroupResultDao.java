package persistance;

import java.util.List;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.GroupModel;
import models.GroupResult;
import models.GroupResult.GroupState;
import play.db.jpa.JPA;

/**
 * DAO for GroupResult
 * 
 * @author Kristian Lange
 */
@Singleton
public class GroupResultDao extends AbstractDao {

	public void create(GroupResult groupResult) {
		persist(groupResult);
	}

	public void update(GroupResult groupResult) {
		merge(groupResult);
	}

	public void remove(GroupResult groupResult) {
		super.remove(groupResult);
	}

	public void refresh(GroupResult groupResult) {
		super.refresh(groupResult);
	}

	public GroupResult findById(Long id) {
		return JPA.em().find(GroupResult.class, id);
	}

	public List<GroupResult> findAllByGroup(GroupModel group) {
		String queryStr = "SELECT e FROM GroupResult e "
				+ "WHERE e.group=:groupId";
		TypedQuery<GroupResult> query = JPA.em().createQuery(queryStr,
				GroupResult.class);
		return query.setParameter("groupId", group).getResultList();
	}

	/**
	 * Searches the DB for the first group result of this group where the
	 * maximum group size is not reached yet and that is in state STARTED.
	 */
	public GroupResult findFirstMaxNotReached(GroupModel group) {
		List<GroupResult> groupResultList = findAllMaxNotReached(group);
		return !groupResultList.isEmpty() ? groupResultList.get(0) : null;
	}

	/**
	 * Searches the DB for all group results of this group where the maximum
	 * group size is not reached yet and that are in state STARTED.
	 */
	public List<GroupResult> findAllMaxNotReached(GroupModel group) {
		String queryStr = "SELECT e FROM GroupResult e, GroupModel s "
				+ "WHERE e.group=:groupId AND s.id=:groupId "
				+ "AND e.groupState=:groupState "
				+ "AND size(e.studyResultList) < s.maxGroupSize";
		TypedQuery<GroupResult> query = JPA.em().createQuery(queryStr,
				GroupResult.class);
		query.setParameter("groupId", group);
		query.setParameter("groupState", GroupState.STARTED);
		return query.getResultList();
	}

	public List<GroupResult> findAllNotFinished() {
		String queryStr = "SELECT e FROM GroupResult e WHERE e.groupState <> :groupState";
		TypedQuery<GroupResult> query = JPA.em().createQuery(queryStr,
				GroupResult.class);
		query.setParameter("groupState", GroupState.FINISHED);
		return query.getResultList();
	}

}
