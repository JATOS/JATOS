package daos.common;

import java.util.List;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.common.Batch;
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

	public List<GroupResult> findAllByBatch(Batch batch) {
		String queryStr = "SELECT e FROM GroupResult e WHERE e.batch=:batchId";
		TypedQuery<GroupResult> query = JPA.em().createQuery(queryStr,
				GroupResult.class);
		return query.setParameter("batchId", batch).getResultList();
	}

	/**
	 * Searches the DB for the first GroupResult of this group where the
	 * maximum group size is not reached yet and that is in state STARTED.
	 */
	public GroupResult findFirstMaxNotReached(Batch group) {
		List<GroupResult> groupResultList = findAllMaxNotReached(group);
		return !groupResultList.isEmpty() ? groupResultList.get(0) : null;
	}

	/**
	 * Searches the DB for all GroupResults of this batch where the maximum
	 * group size is not reached yet and that are in state STARTED.
	 */
	public List<GroupResult> findAllMaxNotReached(Batch batch) {
		String queryStr = "SELECT gr FROM GroupResult gr, Batch b "
				+ "WHERE gr.batch=:batchId AND b.id=:batchId "
				+ "AND gr.groupState=:groupState "
				+ "AND (b.maxActiveMembers is null OR size(gr.studyResultList) < b.maxActiveMembers)"
				+ "AND (size(gr.studyResultList) + size(gr.studyResultHistory)) "
				+ "< b.maxTotalMembers";
		TypedQuery<GroupResult> query = JPA.em().createQuery(queryStr,
				GroupResult.class);
		query.setParameter("batchId", batch);
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
