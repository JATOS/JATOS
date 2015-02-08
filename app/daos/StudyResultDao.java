package daos;

import java.util.List;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import models.StudyModel;
import models.StudyResult;
import play.db.jpa.JPA;

import com.google.inject.Singleton;

/**
 * DAO for StudyResult model
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyResultDao extends AbstractDao<StudyResult> {

	public StudyResult findById(Long id) {
		return JPA.em().find(StudyResult.class, id);
	}

	public List<StudyResult> findAll() {
		String queryStr = "SELECT e FROM StudyResult e";
		TypedQuery<StudyResult> query = JPA.em().createQuery(queryStr,
				StudyResult.class);
		return query.getResultList();
	}

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
