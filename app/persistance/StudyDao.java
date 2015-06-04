package persistance;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.persistence.TypedQuery;

import models.ComponentModel;
import models.ComponentResult;
import models.StudyModel;
import models.StudyResult;
import models.UserModel;
import play.db.jpa.JPA;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * DAO of StudyModel.
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
	 * Persist study and it's components and add member.
	 */
	public void create(StudyModel study, UserModel user) {
		if (study.getUuid() == null) {
			study.setUuid(UUID.randomUUID().toString());
		}
		for (ComponentModel component : study.getComponentList()) {
			componentDao.create(component);
		}
		persist(study);
		addMember(study, user);
	}

	/**
	 * Add member to study.
	 */
	public void addMember(StudyModel study, UserModel member) {
		study.addMember(member);
		merge(study);
	}

	public void update(StudyModel study) {
		merge(study);
	}

	/**
	 * Update properties of study with properties of updatedStudy.
	 */
	public void updateProperties(StudyModel study, StudyModel updatedStudy) {
		study.setTitle(updatedStudy.getTitle());
		study.setDescription(updatedStudy.getDescription());
		study.setDirName(updatedStudy.getDirName());
		study.setJsonData(updatedStudy.getJsonData());
		study.setAllowedWorkerList(updatedStudy.getAllowedWorkerList());
		merge(study);
	}

	/**
	 * Update properties of study with properties of updatedStudy (excluding
	 * study's dir name).
	 */
	public void updatePropertiesWODirName(StudyModel study,
			StudyModel updatedStudy) {
		study.setTitle(updatedStudy.getTitle());
		study.setDescription(updatedStudy.getDescription());
		study.setJsonData(updatedStudy.getJsonData());
		study.setAllowedWorkerList(updatedStudy.getAllowedWorkerList());
		merge(study);
	}

	/**
	 * Remove study and its components
	 */
	public void remove(StudyModel study) {
		// Remove all study's components and their ComponentResults
		for (ComponentModel component : study.getComponentList()) {
			List<ComponentResult> componentResultList = componentResultDao
					.findAllByComponent(component);
			for (ComponentResult componentResult : componentResultList) {
				componentResultDao.remove(componentResult);
			}
			remove(component);
		}
		// Remove study's StudyResults
		for (StudyResult studyResult : studyResultDao.findAllByStudy(study)) {
			studyResultDao.remove(studyResult);
		}
		super.remove(study);
	}

	public StudyModel findById(Long id) {
		return JPA.em().find(StudyModel.class, id);
	}

	public StudyModel findByUuid(String uuid) {
		String queryStr = "SELECT e FROM StudyModel e WHERE " + "e.uuid=:uuid";
		TypedQuery<StudyModel> query = JPA.em().createQuery(queryStr,
				StudyModel.class);
		List<StudyModel> studyList = query.setParameter("uuid", uuid)
				.getResultList();
		// There can be only one study with this UUID
		StudyModel study = studyList.isEmpty() ? null : (StudyModel) studyList
				.get(0);
		return study;
	}

	/**
	 * Finds all studies with the given title and returns them in a list. If
	 * there is none it returns null.
	 */
	public List<StudyModel> findByTitle(String title) {
		String queryStr = "SELECT e FROM StudyModel e WHERE "
				+ "e.title=:title";
		TypedQuery<StudyModel> query = JPA.em().createQuery(queryStr,
				StudyModel.class);
		return query.setParameter("title", title).getResultList();
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
