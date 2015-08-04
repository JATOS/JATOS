package persistance;

import java.util.List;

import javax.persistence.TypedQuery;

import models.GroupResult;
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

	public List<GroupResult> findAll() {
		String queryStr = "SELECT e FROM GroupResult e";
		TypedQuery<GroupResult> query = JPA.em().createQuery(queryStr,
				GroupResult.class);
		return query.getResultList();
	}

}
