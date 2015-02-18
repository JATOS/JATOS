package persistance;

import java.util.List;

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
public class ComponentDao extends AbstractDao<ComponentModel> implements
		IComponentDao {

	private final IComponentResultDao componentResultDao;

	@Inject
	ComponentDao(IComponentResultDao componentResultDao) {
		this.componentResultDao = componentResultDao;
	}

	@Override
	public void create(StudyModel study, ComponentModel component) {
		component.setStudy(study);
		study.addComponent(component);
		persist(component);
		merge(study);
	}

	@Override
	public void update(ComponentModel component) {
		merge(component);
	}

	@Override
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

	@Override
	public void changeActive(ComponentModel component, boolean active) {
		component.setActive(active);
		merge(component);
	}

	@Override
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

	@Override
	public ComponentModel findById(Long id) {
		return JPA.em().find(ComponentModel.class, id);
	}

	@Override
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

	@Override
	public List<ComponentModel> findAll() {
		TypedQuery<ComponentModel> query = JPA.em().createQuery(
				"SELECT e FROM ComponentModel e", ComponentModel.class);
		return query.getResultList();
	}

	@Override
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
