package daos;

import java.util.List;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import com.google.inject.Singleton;

import models.ComponentModel;
import play.db.jpa.JPA;

/**
 * DAO for ComponentModel
 * 
 * @author Kristian Lange
 */
@Singleton
public class ComponentDao extends AbstractDao<ComponentModel> {

	public ComponentModel findById(Long id) {
		return JPA.em().find(ComponentModel.class, id);
	}

	public ComponentModel findByUuid(String uuid) {
		String queryStr = "SELECT e FROM ComponentModel e WHERE "
				+ "e.uuid=:uuid";
		TypedQuery<ComponentModel> query = JPA.em().createQuery(queryStr,
				ComponentModel.class);
		List<ComponentModel> studyList = query.setParameter("uuid", uuid)
				.getResultList();
		ComponentModel study = studyList.isEmpty() ? null
				: (ComponentModel) studyList.get(0);
		return study;
	}

	public List<ComponentModel> findAll() {
		TypedQuery<ComponentModel> query = JPA.em().createQuery(
				"SELECT e FROM ComponentModel e", ComponentModel.class);
		return query.getResultList();
	}

	public void changeComponentOrder(ComponentModel component, int newIndex) {
		String queryStr = "UPDATE ComponentModel SET componentList_order = "
				+ ":newIndex WHERE id = :id";
		Query query = JPA.em().createQuery(queryStr);
		query.setParameter("newIndex", newIndex);
		query.setParameter("id", component.getId());
		query.executeUpdate();
	}

}
