package persistance;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;

/**
 * Interface for DAO of ComponentModel
 * 
 * @author Kristian Lange
 */
public interface IComponentDao {

	/**
	 * Persist Component and add it to the given Study.
	 */
	public abstract void create(StudyModel study, ComponentModel component);

	public abstract void update(ComponentModel component);

	/**
	 * Update component's properties with the ones from updatedComponent.
	 */
	public abstract void updateProperties(ComponentModel component,
			ComponentModel updatedComponent);

	/**
	 * Change and persist active property of a Component.
	 */
	public abstract void changeActive(ComponentModel component, boolean active);

	/**
	 * Remove Component: Remove it from the given study, remove all its
	 * ComponentResults, and remove the component itself.
	 */
	public abstract void remove(StudyModel study, ComponentModel component);

	public abstract ComponentModel findById(Long id);

	public abstract ComponentModel findByUuid(String uuid);

	public abstract List<ComponentModel> findAll();

	public abstract void changeOrder(ComponentModel component, int newIndex);

}