package daos;

import java.util.List;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import models.ComponentResult;
import models.StudyModel;
import models.StudyResult;
import models.workers.Worker;
import play.db.jpa.JPA;

import com.google.inject.Singleton;

/**
 * DAO for StudyResult model
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyResultDao extends AbstractDao implements IStudyResultDao {

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IStudyResultDao#create(models.StudyModel,
	 * models.workers.Worker)
	 */
	@Override
	public StudyResult create(StudyModel study, Worker worker) {
		StudyResult studyResult = new StudyResult(study);
		persist(studyResult);
		worker.addStudyResult(studyResult);
		merge(worker);
		return studyResult;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IStudyResultDao#removeStudyResult(models.StudyResult)
	 */
	@Override
	public void removeStudyResult(StudyResult studyResult) {
		// Remove all component results of this study result
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			remove(componentResult);
		}

		// Remove study result from worker
		Worker worker = studyResult.getWorker();
		worker.removeStudyResult(studyResult);
		merge(worker);

		// Remove studyResult
		remove(studyResult);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IStudyResultDao#removeAllStudyResults(models.StudyModel)
	 */
	@Override
	public void removeAllStudyResults(StudyModel study) {
		List<StudyResult> studyResultList = findAllByStudy(study);
		for (StudyResult studyResult : studyResultList) {
			removeStudyResult(studyResult);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IStudyResultDao#findById(java.lang.Long)
	 */
	@Override
	public StudyResult findById(Long id) {
		return JPA.em().find(StudyResult.class, id);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IStudyResultDao#findAll()
	 */
	@Override
	public List<StudyResult> findAll() {
		String queryStr = "SELECT e FROM StudyResult e";
		TypedQuery<StudyResult> query = JPA.em().createQuery(queryStr,
				StudyResult.class);
		return query.getResultList();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IStudyResultDao#countByStudy(models.StudyModel)
	 */
	@Override
	public int countByStudy(StudyModel study) {
		String queryStr = "SELECT COUNT(e) FROM StudyResult e WHERE e.study=:studyId";
		Query query = JPA.em().createQuery(queryStr);
		Number result = (Number) query.setParameter("studyId", study)
				.getSingleResult();
		return result.intValue();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see daos.IStudyResultDao#findAllByStudy(models.StudyModel)
	 */
	@Override
	public List<StudyResult> findAllByStudy(StudyModel study) {
		String queryStr = "SELECT e FROM StudyResult e "
				+ "WHERE e.study=:studyId";
		TypedQuery<StudyResult> query = JPA.em().createQuery(queryStr,
				StudyResult.class);
		return query.setParameter("studyId", study).getResultList();
	}

}
