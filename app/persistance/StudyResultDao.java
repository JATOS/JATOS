package persistance;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import models.GroupModel;
import models.StudyModel;
import models.StudyResult;
import models.workers.Worker;
import play.db.jpa.JPA;

/**
 * DAO for StudyResult model
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyResultDao extends AbstractDao {

	private final GroupDao groupDao;

	@Inject
	StudyResultDao(GroupDao groupDao) {
		this.groupDao = groupDao;
	}

	/**
	 * Creates StudyResult and adds it to the given Worker.
	 */
	public StudyResult create(StudyModel study, Worker worker) {
		StudyResult studyResult = new StudyResult(study);
		persist(studyResult);
		worker.addStudyResult(studyResult);
		merge(worker);
		return studyResult;
	}

	public void update(StudyResult studyResult) {
		merge(studyResult);
	}

	/**
	 * Remove all ComponentResults of the given StudyResult, remove StudyResult
	 * from the given worker, and remove StudyResult itself.
	 */
	public void remove(StudyResult studyResult) {
		// Remove all component results of this study result
		studyResult.getComponentResultList().forEach(this::remove);

		// Remove study result from worker
		Worker worker = studyResult.getWorker();
		worker.removeStudyResult(studyResult);
		merge(worker);

		// Remove studyResult from group
		GroupModel group = studyResult.getGroup();
		if (group != null) {
			group.removeStudyResult(studyResult);
			if (group.getStudyResultList().isEmpty()) {
				// Remove group if it has no StudyResults
				groupDao.remove(group);
			} else {
				merge(group);
			}
		}

		// Remove studyResult
		super.remove(studyResult);
	}

	public void refresh(StudyResult studyResult) {
		super.refresh(studyResult);
	}

	/**
	 * Removes ALL StudyResults including their ComponentResult of the specified
	 * study.
	 */
	public void removeAllOfStudy(StudyModel study) {
		List<StudyResult> studyResultList = findAllByStudy(study);
		studyResultList.forEach(this::remove);
	}

	public StudyResult findById(Long id) {
		return JPA.em().find(StudyResult.class, id);
	}

	/**
	 * Returns the number of StudyResults belonging to the given study.
	 */
	public int countByStudy(StudyModel study) {
		String queryStr = "SELECT COUNT(e) FROM StudyResult e WHERE e.study=:studyId";
		Query query = JPA.em().createQuery(queryStr);
		Number result = (Number) query.setParameter("studyId", study)
				.getSingleResult();
		return result.intValue();
	}

	public List<StudyResult> findAllByStudy(StudyModel study) {
		String queryStr = "SELECT e FROM StudyResult e "
				+ "WHERE e.study=:studyId";
		TypedQuery<StudyResult> query = JPA.em().createQuery(queryStr,
				StudyResult.class);
		return query.setParameter("studyId", study).getResultList();
	}

}
