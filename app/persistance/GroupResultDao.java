package persistance;

import java.util.List;

import javax.persistence.TypedQuery;

import models.GroupResult;
import models.GroupResult.GroupState;
import models.StudyModel;
import play.db.jpa.JPA;

import com.google.inject.Singleton;

/**
 * DAO for GroupResult model
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
	 * Searches the DB for all GroupResults with this studyId that have the
	 * state INCOMPLETE and returns the first occurrence.
	 */
	public GroupResult findFirstIncomplete(StudyModel study) {
		String queryStr = "SELECT e FROM GroupResult e "
				+ "WHERE e.study=:studyId AND e.groupState=:groupState";
		TypedQuery<GroupResult> query = JPA.em().createQuery(queryStr,
				GroupResult.class);
		query.setParameter("studyId", study);
		query.setParameter("groupState", GroupState.INCOMPLETE);
		query.setMaxResults(1);
		List<GroupResult> groupResultList = query.getResultList();
		return (!groupResultList.isEmpty()) ? groupResultList.get(0) : null;
	}

}
