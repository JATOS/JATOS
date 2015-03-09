package persistance;

import java.util.Iterator;
import java.util.List;

import javax.persistence.TypedQuery;

import models.ComponentModel;
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
public class StudyDao extends AbstractDao<StudyModel> {

	private final StudyResultDao studyResultDao;

	@Inject
	StudyDao(StudyResultDao studyResultDao) {
		this.studyResultDao = studyResultDao;
	}

	/**
	 * Persist study and add member.
	 */
	public void create(StudyModel study, UserModel loggedInUser) {
		persist(study);
		addMember(study, loggedInUser);
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
		// Remove all study's components
		for (ComponentModel component : study.getComponentList()) {
			remove(component);
		}
		// Remove study's StudyResults and ComponentResults
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
