package daos;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import play.db.jpa.JPA;

/**
 * DAO for Component entity
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
	public void create(Study study, Component component) {
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
	public void create(Component component) {
		if (component.getUuid() == null) {
			component.setUuid(UUID.randomUUID().toString());
		}
		persist(component);
	}

	public void update(Component component) {
		merge(component);
	}

	/**
	 * Change and persist active property of a Component.
	 */
	public void changeActive(Component component, boolean active) {
		component.setActive(active);
		merge(component);
	}

	/**
	 * Remove Component: Remove it from the given study, remove all its
	 * ComponentResults, and remove the component itself.
	 */
	public void remove(Study study, Component component) {
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

	public Component findById(Long id) {
		return JPA.em().find(Component.class, id);
	}

	/**
	 * Searches for components with this UUID within the study with the given
	 * ID.
	 */
	public Component findByUuid(String uuid, Study study) {
		String queryStr = "SELECT e FROM Component e WHERE "
				+ "e.uuid=:uuid and e.study=:study";
		TypedQuery<Component> query = JPA.em().createQuery(queryStr,
				Component.class);
		query.setParameter("uuid", uuid);
		query.setParameter("study", study);
		// There can be only one component with this UUID
		query.setMaxResults(1);
		List<Component> studyList = query.getResultList();
		return studyList.isEmpty() ? null : studyList.get(0);
	}

	/**
	 * Finds all components with the given title and returns them in a list. If
	 * there is none it returns an empty list.
	 */
	public List<Component> findByTitle(String title) {
		String queryStr = "SELECT e FROM Component e WHERE "
				+ "e.title=:title";
		TypedQuery<Component> query = JPA.em().createQuery(queryStr,
				Component.class);
		return query.setParameter("title", title).getResultList();
	}

	/**
	 * Change the position of the given Component within its study. The position
	 * is like a index of a list but starts at 1 instead of 0.
	 */
	public void changePosition(Component component, int newPosition) {
		String queryStr = "UPDATE Component SET componentList_order = "
				+ ":newIndex WHERE id = :id";
		Query query = JPA.em().createQuery(queryStr);
		// Index is position - 1
		query.setParameter("newIndex", newPosition - 1);
		query.setParameter("id", component.getId());
		query.executeUpdate();
	}

}
