package persistance;

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
public class StudyResultDao extends AbstractDao<StudyResult> implements
		IStudyResultDao {

	@Override
	public StudyResult create(StudyModel study, Worker worker) {
		StudyResult studyResult = new StudyResult(study);
		persist(studyResult);
		worker.addStudyResult(studyResult);
		merge(worker);
		return studyResult;
	}

	@Override
	public void update(StudyResult studyResult) {
		merge(studyResult);
	}

	@Override
	public void remove(StudyResult studyResult) {
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
		super.remove(studyResult);
	}

	@Override
	public void removeAllOfStudy(StudyModel study) {
		List<StudyResult> studyResultList = findAllByStudy(study);
		for (StudyResult studyResult : studyResultList) {
			remove(studyResult);
		}
	}

	@Override
	public StudyResult findById(Long id) {
		return JPA.em().find(StudyResult.class, id);
	}

	@Override
	public List<StudyResult> findAll() {
		String queryStr = "SELECT e FROM StudyResult e";
		TypedQuery<StudyResult> query = JPA.em().createQuery(queryStr,
				StudyResult.class);
		return query.getResultList();
	}

	@Override
	public int countByStudy(StudyModel study) {
		String queryStr = "SELECT COUNT(e) FROM StudyResult e WHERE e.study=:studyId";
		Query query = JPA.em().createQuery(queryStr);
		Number result = (Number) query.setParameter("studyId", study)
				.getSingleResult();
		return result.intValue();
	}

	@Override
	public List<StudyResult> findAllByStudy(StudyModel study) {
		String queryStr = "SELECT e FROM StudyResult e "
				+ "WHERE e.study=:studyId";
		TypedQuery<StudyResult> query = JPA.em().createQuery(queryStr,
				StudyResult.class);
		return query.setParameter("studyId", study).getResultList();
	}

}
