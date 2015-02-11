package daos;

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
public class ComponentDao extends AbstractDao implements IComponentDao {

	private final IComponentResultDao componentResultDao;

	@Inject
	ComponentDao(IComponentResultDao componentResultDao) {
		this.componentResultDao = componentResultDao;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IComponentDao#addComponent(models.StudyModel,
	 * models.ComponentModel)
	 */
	@Override
	public void addComponent(StudyModel study, ComponentModel component) {
		component.setStudy(study);
		study.addComponent(component);
		persist(component);
		merge(study);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IComponentDao#updateComponentsProperties(models.ComponentModel,
	 * models.ComponentModel)
	 */
	@Override
	public void updateComponentsProperties(ComponentModel component,
			ComponentModel updatedComponent) {
		component.setTitle(updatedComponent.getTitle());
		component.setReloadable(updatedComponent.isReloadable());
		component.setHtmlFilePath(updatedComponent.getHtmlFilePath());
		component.setComments(updatedComponent.getComments());
		component.setJsonData(updatedComponent.getJsonData());
		component.setActive(updatedComponent.isActive());
		merge(component);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IComponentDao#changeActive(models.ComponentModel, boolean)
	 */
	@Override
	public void changeActive(ComponentModel component, boolean active) {
		component.setActive(active);
		merge(component);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IComponentDao#removeComponent(models.StudyModel,
	 * models.ComponentModel)
	 */
	@Override
	public void removeComponent(StudyModel study, ComponentModel component) {
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
		remove(component);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IComponentDao#findById(java.lang.Long)
	 */
	@Override
	public ComponentModel findById(Long id) {
		return JPA.em().find(ComponentModel.class, id);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IComponentDao#findByUuid(java.lang.String)
	 */
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IComponentDao#findAll()
	 */
	@Override
	public List<ComponentModel> findAll() {
		TypedQuery<ComponentModel> query = JPA.em().createQuery(
				"SELECT e FROM ComponentModel e", ComponentModel.class);
		return query.getResultList();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IComponentDao#changeComponentOrder(models.ComponentModel, int)
	 */
	@Override
	public void changeComponentOrder(ComponentModel component, int newIndex) {
		String queryStr = "UPDATE ComponentModel SET componentList_order = "
				+ ":newIndex WHERE id = :id";
		Query query = JPA.em().createQuery(queryStr);
		query.setParameter("newIndex", newIndex);
		query.setParameter("id", component.getId());
		query.executeUpdate();
	}

}
