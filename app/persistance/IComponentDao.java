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

	/**
	 * Searches for components with this UUID within the study with the given
	 * ID.
	 */
	public abstract ComponentModel findByUuid(String uuid, StudyModel study);

	public abstract List<ComponentModel> findAll();

	/**
	 * Change the position of the given Component within its study. The position
	 * is like a index of a list but starts at 1 instead of 0.
	 */
	public abstract void changePosition(ComponentModel component,
			int newPosition);

}