package persistance;

import java.util.List;

import javax.persistence.TypedQuery;

import models.GroupModel;
import models.GroupModel.GroupState;
import models.StudyModel;
import play.db.jpa.JPA;

import com.google.inject.Singleton;

/**
 * DAO for GroupModel
 * 
 * @author Kristian Lange
 */
@Singleton
public class GroupDao extends AbstractDao {

	public void create(GroupModel group) {
		persist(group);
	}

	public void update(GroupModel group) {
		merge(group);
	}

	public void remove(GroupModel group) {
		super.remove(group);
	}

	public void refresh(GroupModel group) {
		super.refresh(group);
	}

	public GroupModel findById(Long id) {
		return JPA.em().find(GroupModel.class, id);
	}
	
	/**
	 * Searches the DB for the first group with this studyId where the maximum
	 * group size is not reached yet and that is in state STARTED.
	 */
	public GroupModel findFirstMaxNotReached(StudyModel study) {
		List<GroupModel> groupList = findAllMaxNotReached(study);
		return !groupList.isEmpty() ? groupList.get(0) : null;
	}

	/**
	 * Searches the DB for all groups with this studyId where the maximum
	 * group size is not reached yet and that are in state STARTED.
	 */
	public List<GroupModel> findAllMaxNotReached(StudyModel study) {
		String queryStr = "SELECT e FROM GroupModel e, StudyModel s "
				+ "WHERE e.study=:studyId AND s.id=:studyId "
				+ "AND e.groupState=:groupState "
				+ "AND size(e.studyResultList) < s.maxGroupSize";
		TypedQuery<GroupModel> query = JPA.em().createQuery(queryStr,
				GroupModel.class);
		query.setParameter("studyId", study);
		query.setParameter("groupState", GroupState.STARTED);
		return query.getResultList();
	}
	
	public List<GroupModel> findAllNotFinished() {
		String queryStr = "SELECT e FROM GroupModel e WHERE e.groupState <> :groupState";
		TypedQuery<GroupModel> query = JPA.em().createQuery(queryStr,
				GroupModel.class);
		query.setParameter("groupState", GroupState.FINISHED);
		return query.getResultList();
	}

}
