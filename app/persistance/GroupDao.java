package persistance;

import java.util.List;

import javax.persistence.TypedQuery;

import models.GroupModel;
import models.StudyModel;
import play.db.jpa.JPA;

import com.google.inject.Singleton;

/**
 * DAO of GroupModel.
 * 
 * @author Kristian Lange
 */
@Singleton
public class GroupDao extends AbstractDao {

	public void create(StudyModel study, int maxWorker) {
		GroupModel group = new GroupModel(study, maxWorker);
		persist(group);
	}

	public void update(GroupModel group) {
		merge(group);
	}

	public void remove(GroupModel group) {
		super.remove(group);
	}

	public GroupModel findById(Long id) {
		return JPA.em().find(GroupModel.class, id);
	}

	public List<GroupModel> findAll() {
		TypedQuery<GroupModel> query = JPA.em().createQuery(
				"SELECT e FROM GroupModel e", GroupModel.class);
		return query.getResultList();
	}

}
