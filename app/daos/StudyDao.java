package daos;

import java.util.Iterator;
import java.util.List;

import javax.persistence.TypedQuery;

import com.google.inject.Singleton;

import models.StudyModel;
import play.db.jpa.JPA;

/**
 * DAO of StudyModel.
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyDao extends AbstractDao<StudyModel> {

	public StudyModel findById(Long id) {
		return JPA.em().find(StudyModel.class, id);
	}

	public StudyModel findByUuid(String uuid) {
		String queryStr = "SELECT e FROM StudyModel e WHERE " + "e.uuid=:uuid";
		TypedQuery<StudyModel> query = JPA.em().createQuery(queryStr,
				StudyModel.class);
		List<StudyModel> studyList = query.setParameter("uuid", uuid)
				.getResultList();
		StudyModel study = studyList.isEmpty() ? null : (StudyModel) studyList
				.get(0);
		return study;
	}

	public List<StudyModel> findAll() {
		TypedQuery<StudyModel> query = JPA.em().createQuery(
				"SELECT e FROM StudyModel e", StudyModel.class);
		return query.getResultList();
	}

	public List<StudyModel> findAllByUser(String memberEmail) {
		TypedQuery<StudyModel> query = JPA.em().createQuery(
				"SELECT DISTINCT g FROM UserModel u LEFT JOIN u.studyList g "
						+ "WHERE u.email = :member", StudyModel.class);
		query.setParameter("member", memberEmail);
		List<StudyModel> studyList = query.getResultList();
		// Sometimes the DB returns an element that's just null (bug?). Iterate
		// through the list and remove all null elements.
		Iterator<StudyModel> it = studyList.iterator();
		while (it.hasNext()) {
			StudyModel study = it.next();
			if (study == null) {
				it.remove();
			}
		}
		return studyList;
	}

}
