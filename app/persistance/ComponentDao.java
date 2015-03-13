package persistance;

import java.util.List;
import java.util.UUID;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import models.ComponentModel;
import models.ComponentResult;
import models.StudyModel;
import models.StudyResult;
import play.db.jpa.JPA;

import com.google.inject.Inject;
import com.google.inject.Singleton;

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
		component.setUuid(UUID.randomUUID().toString());
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
	 * Update component's properties with the ones from updatedComponent.
	 */
	public void updateProperties(ComponentModel component,
			ComponentModel updatedComponent) {
		component.setTitle(updatedComponent.getTitle());
		component.setReloadable(updatedComponent.isReloadable());
		component.setHtmlFilePath(updatedComponent.getHtmlFilePath());
		component.setComments(updatedComponent.getComments());
		component.setJsonData(updatedComponent.getJsonData());
		component.setActive(updatedComponent.isActive());
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
		List<ComponentModel> studyList = query.setParameter("uuid", uuid)
				.setParameter("study", study).getResultList();
		ComponentModel component = studyList.isEmpty() ? null
				: (ComponentModel) studyList.get(0);
		return component;
	}

	public List<ComponentModel> findAll() {
		TypedQuery<ComponentModel> query = JPA.em().createQuery(
				"SELECT e FROM ComponentModel e", ComponentModel.class);
		return query.getResultList();
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
