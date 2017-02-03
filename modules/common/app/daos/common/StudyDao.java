package daos.common;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.common.Study;
import models.common.User;
import play.db.jpa.JPAApi;

/**
 * DAO of Study entity
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyDao extends AbstractDao {

	@Inject
	StudyDao(JPAApi jpa) {
		super(jpa);
	}

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
		return jpa.em().find(Study.class, id);
	}

	public Study findByUuid(String uuid) {
		String queryStr = "SELECT s FROM Study s WHERE " + "s.uuid=:uuid";
		TypedQuery<Study> query = jpa.em().createQuery(queryStr, Study.class);
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
		String queryStr = "SELECT s FROM Study s WHERE s.title=:title";
		TypedQuery<Study> query = jpa.em().createQuery(queryStr, Study.class);
		return query.setParameter("title", title).getResultList();
	}

	public List<Study> findAll() {
		TypedQuery<Study> query = jpa.em().createQuery("SELECT s FROM Study s",
				Study.class);
		return query.getResultList();
	}

	public List<Study> findAllByUser(User user) {
		TypedQuery<Study> query = jpa.em().createQuery(
				"SELECT s FROM Study s INNER JOIN s.userList u WHERE u = :user",
				Study.class);
		query.setParameter("user", user);
		return query.getResultList();
	}

}
