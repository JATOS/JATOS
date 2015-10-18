package persistance;

import java.util.List;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.GroupResult;
import models.GroupResult.GroupState;
import models.StudyModel;
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

	/**
	 * Searches the DB for the first group result with this studyId where the
	 * maximum group size is not reached yet and that is in state STARTED.
	 */
	public GroupResult findFirstMaxNotReached(StudyModel study) {
		List<GroupResult> groupResultList = findAllMaxNotReached(study);
		return !groupResultList.isEmpty() ? groupResultList.get(0) : null;
	}

	/**
	 * Searches the DB for all group results with this studyId where the
	 * maximum group size is not reached yet and that are in state STARTED.
	 */
	public List<GroupResult> findAllMaxNotReached(StudyModel study) {
		String queryStr = "SELECT e FROM GroupResult e, StudyModel s "
				+ "WHERE e.study=:studyId AND s.id=:studyId "
				+ "AND e.groupState=:groupState "
				+ "AND size(e.studyResultList) < s.maxGroupSize";
		TypedQuery<GroupResult> query = JPA.em().createQuery(queryStr,
				GroupResult.class);
		query.setParameter("studyId", study);
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
