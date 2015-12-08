package daos.common;

import java.util.List;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.common.Group;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
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

	public List<GroupResult> findAllByGroup(Group group) {
		String queryStr = "SELECT e FROM GroupResult e "
				+ "WHERE e.group=:groupId";
		TypedQuery<GroupResult> query = JPA.em().createQuery(queryStr,
				GroupResult.class);
		return query.setParameter("groupId", group).getResultList();
	}

	/**
	 * Searches the DB for the first GroupResult of this group where the
	 * maximum group size is not reached yet and that is in state STARTED.
	 */
	public GroupResult findFirstMaxNotReached(Group group) {
		List<GroupResult> groupResultList = findAllMaxNotReached(group);
		return !groupResultList.isEmpty() ? groupResultList.get(0) : null;
	}

	/**
	 * Searches the DB for all GroupResults of this group where the maximum
	 * group size is not reached yet and that are in state STARTED.
	 */
	public List<GroupResult> findAllMaxNotReached(Group group) {
		String queryStr = "SELECT gr FROM GroupResult gr, Group g "
				+ "WHERE gr.group=:groupId AND g.id=:groupId "
				+ "AND gr.groupState=:groupState "
				+ "AND (g.maxActiveMemberSize is null OR size(gr.studyResultList) < g.maxActiveMemberSize)"
				+ "AND (size(gr.studyResultList) + size(gr.studyResultHistory)) "
				+ "< g.maxTotalMemberSize";
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
