package persistance;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import models.ComponentModel;
import models.ComponentResult;
import models.StudyModel;
import models.StudyResult;
import play.db.jpa.JPA;

/**
 * DAO for ComponentModel
 * 
 * @author Kristian Lange
 */
@Singleton
public class ComponentDao extends AbstractDao {

	private final ComponentResultDao componentResultDao;

	@Inject
	ComponentDao(ComponentResultDao componentResultDao) {
		this.componentResultDao = componentResultDao;
	}

	/**
	 * Persist Component and add it to the given Study.
	 */
	public void create(StudyModel study, ComponentModel component) {
		if (component.getUuid() == null) {
			component.setUuid(UUID.randomUUID().toString());
		}
		component.setStudy(study);
		study.addComponent(component);
		persist(component);
		merge(study);
	}

	/**
	 * Persist Component.
	 */
	public void create(ComponentModel component) {
		if (component.getUuid() == null) {
			component.setUuid(UUID.randomUUID().toString());
		}
		persist(component);
	}

	public void update(ComponentModel component) {
		merge(component);
	}

	/**
	 * Change and persist active property of a Component.
	 */
	public void changeActive(ComponentModel component, boolean active) {
		component.setActive(active);
		merge(component);
	}

	/**
	 * Remove Component: Remove it from the given study, remove all its
	 * ComponentResults, and remove the component itself.
	 */
	public void remove(StudyModel study, ComponentModel component) {
		// Remove component from study
		study.removeComponent(component);
		merge(study);
		// Remove component's ComponentResults
		for (ComponentResult componentResult : componentResultDao
				.findAllByComponent(component)) {
			StudyResult studyResult = componentResult.getStudyResult();
			studyResult.removeComponentResult(componentResult);
			merge(studyResult);
			remove(componentResult);
		}
		super.remove(component);
	}

	public ComponentModel findById(Long id) {
		return JPA.em().find(ComponentModel.class, id);
	}

	/**
	 * Searches for components with this UUID within the study with the given
	 * ID.
	 */
	public ComponentModel findByUuid(String uuid, StudyModel study) {
		String queryStr = "SELECT e FROM ComponentModel e WHERE "
				+ "e.uuid=:uuid and e.study=:study";
		TypedQuery<ComponentModel> query = JPA.em().createQuery(queryStr,
				ComponentModel.class);
		query.setParameter("uuid", uuid);
		query.setParameter("study", study);
		// There can be only one component with this UUID
		query.setMaxResults(1);
		List<ComponentModel> studyList = query.getResultList();
		return studyList.isEmpty() ? null : studyList.get(0);
	}

	/**
	 * Finds all components with the given title and returns them in a list. If
	 * there is none it returns an empty list.
	 */
	public List<ComponentModel> findByTitle(String title) {
		String queryStr = "SELECT e FROM ComponentModel e WHERE "
				+ "e.title=:title";
		TypedQuery<ComponentModel> query = JPA.em().createQuery(queryStr,
				ComponentModel.class);
		return query.setParameter("title", title).getResultList();
	}

	/**
	 * Change the position of the given Component within its study. The position
	 * is like a index of a list but starts at 1 instead of 0.
	 */
	public void changePosition(ComponentModel component, int newPosition) {
		String queryStr = "UPDATE ComponentModel SET componentList_order = "
				+ ":newIndex WHERE id = :id";
		Query query = JPA.em().createQuery(queryStr);
		// Index is position - 1
		query.setParameter("newIndex", newPosition - 1);
		query.setParameter("id", component.getId());
		query.executeUpdate();
	}

}
