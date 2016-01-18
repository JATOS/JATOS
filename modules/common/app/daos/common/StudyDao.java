package daos.common;

import java.util.Iterator;
import java.util.List;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.common.Study;
import play.db.jpa.JPA;

/**
 * DAO of Study entity
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyDao extends AbstractDao {

	public void create(Study study) {
		persist(study);
	}

	public void remove(Study study) {
		super.remove(study);
	}

	public void update(Study study) {
		merge(study);
	}

	public Study findById(Long id) {
		return JPA.em().find(Study.class, id);
	}

	public Study findByUuid(String uuid) {
		String queryStr = "SELECT e FROM Study e WHERE " + "e.uuid=:uuid";
		TypedQuery<Study> query = JPA.em().createQuery(queryStr, Study.class);
		query.setParameter("uuid", uuid);
		// There can be only one study with this UUID
		query.setMaxResults(1);
		List<Study> studyList = query.getResultList();
		return studyList.isEmpty() ? null : studyList.get(0);
	}

	/**
	 * Finds all studies with the given title and returns them in a list. If
	 * there is none it returns an empty list.
	 */
	public List<Study> findByTitle(String title) {
		String queryStr = "SELECT e FROM Study e WHERE e.title=:title";
		TypedQuery<Study> query = JPA.em().createQuery(queryStr, Study.class);
		return query.setParameter("title", title).getResultList();
	}

	public List<Study> findAll() {
		TypedQuery<Study> query = JPA.em().createQuery("SELECT e FROM Study e",
				Study.class);
		return query.getResultList();
	}

	public List<Study> findAllByUser(String userEmail) {
		TypedQuery<Study> query = JPA.em().createQuery(
				"SELECT DISTINCT g FROM User u LEFT JOIN u.studyList g "
						+ "WHERE u.email = :user",
				Study.class);
		query.setParameter("user", userEmail);
		List<Study> studyList = query.getResultList();
		// Sometimes the DB returns an element that's just null (bug?). Iterate
		// through the list and remove all null elements.
		Iterator<Study> it = studyList.iterator();
		while (it.hasNext()) {
			Study study = it.next();
			if (study == null) {
				it.remove();
			}
		}
		return studyList;
	}

}
