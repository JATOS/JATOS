package daos;

import java.util.List;

import models.StudyModel;
import models.StudyResult;
import models.workers.Worker;

/**
 * Interface for DAO of StudyResult model
 * 
 * @author Kristian Lange
 */
public interface IStudyResultDao {

	/**
	 * Creates StudyResult and adds it to the given Worker.
	 */
	public abstract StudyResult create(StudyModel study, Worker worker);

	public abstract void update(StudyResult studyResult);

	/**
	 * Remove all ComponentResults of the given StudyResult, remove StudyResult
	 * from the given worker, and remove StudyResult itself.
	 */
	public abstract void remove(StudyResult studyResult);

	/**
	 * Removes ALL StudyResults including their ComponentResult of the specified
	 * study.
	 */
	public abstract void removeAllOfStudy(StudyModel study);

	public abstract StudyResult findById(Long id);

	public abstract List<StudyResult> findAll();

	public abstract int countByStudy(StudyModel study);

	public abstract List<StudyResult> findAllByStudy(StudyModel study);

}