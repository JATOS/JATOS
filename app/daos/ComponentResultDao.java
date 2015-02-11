package daos;

import java.util.List;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import models.ComponentModel;
import models.ComponentResult;
import models.StudyResult;
import play.db.jpa.JPA;

import com.google.inject.Singleton;

/**
 * DAO for ComponentResult model
 * 
 * @author Kristian Lange
 */
@Singleton
public class ComponentResultDao extends AbstractDao implements
		IComponentResultDao {

	/**
	 * Creates ComponentResult for the given Component and adds it to the given
	 * StudyResult.
	 */
	public ComponentResult createComponentResult(StudyResult studyResult,
			ComponentModel component) {
		ComponentResult componentResult = new ComponentResult(component);
		componentResult.setStudyResult(studyResult);
		persist(componentResult);
		studyResult.addComponentResult(componentResult);
		merge(studyResult);
		merge(componentResult);
		return componentResult;
	}

	/**
	 * Remove ComponentResult form its StudyResult and then remove itself.
	 */
	public void removeComponentResult(ComponentResult componentResult) {
		StudyResult studyResult = componentResult.getStudyResult();
		studyResult.removeComponentResult(componentResult);
		merge(studyResult);
		remove(componentResult);
	}

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
