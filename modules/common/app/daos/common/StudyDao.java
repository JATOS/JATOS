package daos.common;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.TypedQuery;

import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.User;
import play.db.jpa.JPA;

/**
 * DAO of Study entity
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyDao extends AbstractDao {

	private final StudyResultDao studyResultDao;
	private final ComponentResultDao componentResultDao;
	private final ComponentDao componentDao;

	@Inject
	StudyDao(StudyResultDao studyResultDao,
			ComponentResultDao componentResultDao, ComponentDao componentDao) {
		this.studyResultDao = studyResultDao;
		this.componentResultDao = componentResultDao;
		this.componentDao = componentDao;
	}

	/**
	 * Persist study and it's components and add user.
	 */
	public void create(Study study, User user) {
		if (study.getUuid() == null) {
			study.setUuid(UUID.randomUUID().toString());
		}
		study.getComponentList().forEach(componentDao::create);
		persist(study);
		addUser(study, user);
	}

	/**
	 * Add user to study.
	 */
	public void addUser(Study study, User user) {
		study.addUser(user);
		merge(study);
	}

	public void update(Study study) {
		merge(study);
	}

	/**
	 * Remove study and its components
	 */
	public void remove(Study study) {
		// Remove all study's components and their ComponentResults
		for (Component component : study.getComponentList()) {
			List<ComponentResult> componentResultList = componentResultDao
					.findAllByComponent(component);
			componentResultList.forEach(componentResultDao::remove);
			remove(component);
		}
		// Remove study's StudyResults
		studyResultDao.findAllByStudy(study).forEach(studyResultDao::remove);
		super.remove(study);
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
