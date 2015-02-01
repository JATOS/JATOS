package daos;

import java.util.List;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import com.google.inject.Singleton;

import models.ComponentModel;
import models.results.ComponentResult;
import play.db.jpa.JPA;

/**
 * DAO for UserModel
 * 
 * @author Kristian Lange
 */
@Singleton
public class ComponentResultDao extends AbstractDao<ComponentResult> {

	public ComponentResult findById(Long id) {
		return JPA.em().find(ComponentResult.class, id);
	}

	public int countByComponent(ComponentModel component) {
		String queryStr = "SELECT COUNT(e) FROM ComponentResult e WHERE e.component=:componentId";
		Query query = JPA.em().createQuery(queryStr);
		Number result = (Number) query.setParameter("componentId", component)
				.getSingleResult();
		return result.intValue();
	}

	public List<ComponentResult> findAllByComponent(ComponentModel component) {
		String queryStr = "SELECT e FROM ComponentResult e "
				+ "WHERE e.component=:componentId";
		TypedQuery<ComponentResult> query = JPA.em().createQuery(queryStr,
				ComponentResult.class);
		return query.setParameter("componentId", component).getResultList();
	}

}
