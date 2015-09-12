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
	 * Searches the DB for all groups with this studyId that have the
	 * state INCOMPLETE and returns the first occurrence.
	 */
	public GroupModel findFirstIncomplete(StudyModel study) {
		String queryStr = "SELECT e FROM GroupModel e "
				+ "WHERE e.study=:studyId AND e.groupState=:groupState";
		TypedQuery<GroupModel> query = JPA.em().createQuery(queryStr,
				GroupModel.class);
		query.setParameter("studyId", study);
		query.setParameter("groupState", GroupState.INCOMPLETE);
		query.setMaxResults(1);
		List<GroupModel> groupList = query.getResultList();
		return (!groupList.isEmpty()) ? groupList.get(0) : null;
	}

}
