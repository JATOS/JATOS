package daos.common;

import java.util.List;

import javax.inject.Singleton;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import models.common.Batch;
import models.common.Study;
import models.common.StudyResult;
import play.db.jpa.JPA;

/**
 * DAO for StudyResult entity
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyResultDao extends AbstractDao {

	public void create(StudyResult studyResult) {
		super.persist(studyResult);
	}

	public void update(StudyResult studyResult) {
		merge(studyResult);
	}

	public void remove(StudyResult studyResult) {
		super.remove(studyResult);
	}

	public void refresh(StudyResult studyResult) {
		super.refresh(studyResult);
	}

	public StudyResult findById(Long id) {
		return JPA.em().find(StudyResult.class, id);
	}

	/**
	 * Returns the number of StudyResults belonging to the given study.
	 */
	public int countByStudy(Study study) {
		String queryStr = "SELECT COUNT(e) FROM StudyResult e WHERE e.study=:studyId";
		Query query = JPA.em().createQuery(queryStr);
		Number result = (Number) query.setParameter("studyId", study)
				.getSingleResult();
		return result.intValue();
	}

	public List<StudyResult> findAllByStudy(Study study) {
		String queryStr = "SELECT e FROM StudyResult e "
				+ "WHERE e.study=:studyId";
		TypedQuery<StudyResult> query = JPA.em().createQuery(queryStr,
				StudyResult.class);
		return query.setParameter("studyId", study).getResultList();
	}

	public List<StudyResult> findAllByBatch(Batch batch) {
		String queryStr = "SELECT e FROM StudyResult e "
				+ "WHERE e.batch=:batchId";
		TypedQuery<StudyResult> query = JPA.em().createQuery(queryStr,
				StudyResult.class);
		return query.setParameter("batchId", batch).getResultList();
	}

}
