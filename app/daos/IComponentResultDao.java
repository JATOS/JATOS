package daos;

import java.util.List;

import models.ComponentModel;
import models.ComponentResult;
import models.StudyResult;

/**
 * Interface for DAO of ComponentResult model
 * 
 * @author Kristian Lange
 */
public interface IComponentResultDao {

	/**
	 * Creates ComponentResult for the given Component and adds it to the given
	 * StudyResult.
	 */
	public abstract ComponentResult createComponentResult(
			StudyResult studyResult, ComponentModel component);

	/**
	 * Remove ComponentResult form its StudyResult and then remove itself.
	 */
	public abstract void removeComponentResult(ComponentResult componentResult);

	public abstract ComponentResult findById(Long id);

	public abstract int countByComponent(ComponentModel component);

	public abstract List<ComponentResult> findAllByComponent(
			ComponentModel component);

}