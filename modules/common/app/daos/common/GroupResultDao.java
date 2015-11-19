package daos.common;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.common.Group;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
import play.db.jpa.JPAApi;

/**
 * DAO for GroupResult
 * 
 * @author Kristian Lange
 */
@Singleton
public class GroupResultDao extends AbstractDao {

	@Inject
	GroupResultDao(JPAApi jpa) {
		super(jpa);
	}
	
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
		return em().find(GroupResult.class, id);
	}

	public List<GroupResult> findAllByGroup(Group group) {
		String queryStr = "SELECT e FROM GroupResult e "
				+ "WHERE e.group=:groupId";
		TypedQuery<GroupResult> query = em().createQuery(queryStr,
				GroupResult.class);
		return query.setParameter("groupId", group).getResultList();
	}

	/**
	 * Searches the DB for the first group result of this group where the
	 * maximum group size is not reached yet and that is in state STARTED.
	 */
	public GroupResult findFirstMaxNotReached(Group group) {
		List<GroupResult> groupResultList = findAllMaxNotReached(group);
		return !groupResultList.isEmpty() ? groupResultList.get(0) : null;
	}

	/**
	 * Searches the DB for all group results of this group where the maximum
	 * group size is not reached yet and that are in state STARTED.
	 */
	public List<GroupResult> findAllMaxNotReached(Group group) {
		String queryStr = "SELECT gr FROM GroupResult gr, Group g "
				+ "WHERE gr.group=:groupId AND g.id=:groupId "
				+ "AND gr.groupState=:groupState "
				+ "AND size(gr.studyResultList) < g.maxActiveMemberSize "
				+ "AND (size(gr.studyResultList) + size(gr.studyResultHistory)) "
				+ "< g.maxTotalMemberSize";
		TypedQuery<GroupResult> query = em().createQuery(queryStr,
				GroupResult.class);
		query.setParameter("groupId", group);
		query.setParameter("groupState", GroupState.STARTED);
		return query.getResultList();
	}

	public List<GroupResult> findAllNotFinished() {
		String queryStr = "SELECT e FROM GroupResult e WHERE e.groupState <> :groupState";
		TypedQuery<GroupResult> query = em().createQuery(queryStr,
				GroupResult.class);
		query.setParameter("groupState", GroupState.FINISHED);
		return query.getResultList();
	}

}
