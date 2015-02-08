package utils;

import java.util.List;

import models.ComponentModel;
import models.ComponentResult;
import models.StudyModel;
import models.StudyResult;
import models.UserModel;
import models.workers.JatosWorker;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import models.workers.Worker;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import daos.ComponentDao;
import daos.ComponentResultDao;
import daos.StudyDao;
import daos.StudyResultDao;
import daos.UserDao;
import daos.workers.WorkerDao;

/**
 * Utility class that provides persistence methods.
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersistanceUtils {

	private final UserDao userDao;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;
	private final ComponentResultDao componentResultDao;
	private final StudyResultDao studyResultDao;
	private final WorkerDao workerDao;

	@Inject
	public PersistanceUtils(UserDao userDao, StudyDao studyDao,
			ComponentDao componentDao, ComponentResultDao componentResultDao,
			StudyResultDao studyResultDao, WorkerDao workerDao) {
		this.userDao = userDao;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
		this.componentResultDao = componentResultDao;
		this.studyResultDao = studyResultDao;
		this.workerDao = workerDao;
	}

	/**
	 * Creates StudyResult and adds it to the worker.
	 */
	public StudyResult createStudyResult(StudyModel study, Worker worker) {
		StudyResult studyResult = new StudyResult(study);
		studyResultDao.persist(studyResult);
		worker.addStudyResult(studyResult);
		workerDao.merge(worker);
		return studyResult;
	}

	/**
	 * Creates ComponentResult and adds it to the study.
	 */
	public ComponentResult createComponentResult(StudyResult studyResult,
			ComponentModel component) {
		ComponentResult componentResult = new ComponentResult(component);
		componentResult.setStudyResult(studyResult);
		componentResultDao.persist(componentResult);
		studyResult.addComponentResult(componentResult);
		studyResultDao.merge(studyResult);
		componentResultDao.merge(componentResult);
		return componentResult;
	}

	/**
	 * Create MTWorker. Distinguishes between normal and sandbox.
	 */
	public MTWorker createMTWorker(String mtWorkerId, boolean mTurkSandbox) {
		MTWorker worker;
		if (mTurkSandbox) {
			worker = new MTSandboxWorker(mtWorkerId);
		} else {
			worker = new MTWorker(mtWorkerId);
		}
		workerDao.persist(worker);
		return worker;
	}

	/**
	 * Persist user und creates it's JatosWorker.
	 */
	public void addUser(UserModel user) {
		JatosWorker worker = new JatosWorker(user);
		workerDao.persist(worker);
		user.setWorker(worker);
		userDao.persist(user);
		workerDao.merge(worker);
	}

	/**
	 * Changes name of user.
	 */
	public void updateUser(UserModel user, String name) {
		user.setName(name);
		userDao.merge(user);
	}

	/**
	 * Persist study and add member.
	 */
	public void addStudy(StudyModel study, UserModel loggedInUser) {
		studyDao.persist(study);
		addMemberToStudy(study, loggedInUser);
	}

	/**
	 * Add member to study.
	 */
	public void addMemberToStudy(StudyModel study, UserModel member) {
		study.addMember(member);
		studyDao.merge(study);
	}

	/**
	 * Update properties of study with properties of updatedStudy.
	 */
	public void updateStudysProperties(StudyModel study, StudyModel updatedStudy) {
		study.setTitle(updatedStudy.getTitle());
		study.setDescription(updatedStudy.getDescription());
		study.setDirName(updatedStudy.getDirName());
		study.setJsonData(updatedStudy.getJsonData());
		study.setAllowedWorkerList(updatedStudy.getAllowedWorkerList());
		studyDao.merge(study);
	}

	/**
	 * Update properties of study with properties of updatedStudy (excluding
	 * study's dir name).
	 */
	public void updateStudysPropertiesWODirName(StudyModel study,
			StudyModel updatedStudy) {
		study.setTitle(updatedStudy.getTitle());
		study.setDescription(updatedStudy.getDescription());
		study.setJsonData(updatedStudy.getJsonData());
		study.setAllowedWorkerList(updatedStudy.getAllowedWorkerList());
		studyDao.merge(study);
	}

	/**
	 * Remove study and its components
	 */
	public void removeStudy(StudyModel study) {
		// Remove all study's components
		for (ComponentModel component : study.getComponentList()) {
			componentDao.remove(component);
		}
		// Remove study's StudyResults and ComponentResults
		for (StudyResult studyResult : studyResultDao.findAllByStudy(study)) {
			removeStudyResult(studyResult);
		}
		studyDao.remove(study);
	}

	/**
	 * Persist component and add to study.
	 */
	public void addComponent(StudyModel study, ComponentModel component) {
		component.setStudy(study);
		study.addComponent(component);
		componentDao.persist(component);
		studyDao.merge(study);
	}

	/**
	 * Update component's properties with the ones from updatedComponent
	 */
	public void updateComponentsProperties(ComponentModel component,
			ComponentModel updatedComponent) {
		component.setTitle(updatedComponent.getTitle());
		component.setReloadable(updatedComponent.isReloadable());
		component.setHtmlFilePath(updatedComponent.getHtmlFilePath());
		component.setComments(updatedComponent.getComments());
		component.setJsonData(updatedComponent.getJsonData());
		component.setActive(updatedComponent.isActive());
		componentDao.merge(component);
	}

	/**
	 * Change and persist active property.
	 */
	public void changeActive(ComponentModel component, boolean active) {
		component.setActive(active);
		componentDao.merge(component);
	}

	/**
	 * Remove component from study, all its ComponentResults and the component
	 * itself.
	 */
	public void removeComponent(StudyModel study, ComponentModel component) {
		// Remove component from study
		study.removeComponent(component);
		studyDao.merge(study);
		// Remove component's ComponentResults
		for (ComponentResult componentResult : componentResultDao
				.findAllByComponent(component)) {
			StudyResult studyResult = componentResult.getStudyResult();
			studyResult.removeComponentResult(componentResult);
			studyResultDao.merge(studyResult);
			componentResultDao.remove(componentResult);
		}
		componentDao.remove(component);
	}

	/**
	 * Remove ComponentResult form its StudyResult and remove itself.
	 */
	public void removeComponentResult(ComponentResult componentResult) {
		StudyResult studyResult = componentResult.getStudyResult();
		studyResult.removeComponentResult(componentResult);
		studyResultDao.merge(studyResult);
		componentResultDao.remove(componentResult);
	}

	/**
	 * Remove StudyResult and all its ComponentResults. Remove study result from
	 * worker.
	 */
	public void removeStudyResult(StudyResult studyResult) {
		// Remove all component results of this study result
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			componentResultDao.remove(componentResult);
		}

		// Remove study result from worker
		Worker worker = studyResult.getWorker();
		worker.removeStudyResult(studyResult);
		workerDao.merge(worker);

		// Remove studyResult
		studyResultDao.remove(studyResult);
	}

	/**
	 * Removes all StudyResults including their ComponentResult of the specified
	 * study.
	 */
	public void removeAllStudyResults(StudyModel study) {
		List<StudyResult> studyResultList = studyResultDao
				.findAllByStudy(study);
		for (StudyResult studyResult : studyResultList) {
			removeStudyResult(studyResult);
		}
	}

	/**
	 * Removes a Worker including its StudyResults and their ComponentResults.
	 */
	public void removeWorker(Worker worker) {
		// Don't remove JATOS' own workers
		if (worker instanceof JatosWorker) {
			return;
		}

		// Remove all studyResults and their componentResults
		for (StudyResult studyResult : worker.getStudyResultList()) {
			for (ComponentResult componentResult : studyResult
					.getComponentResultList()) {
				componentResultDao.remove(componentResult);
			}
			studyResultDao.remove(studyResult);
		}

		// Remove worker
		workerDao.remove(worker);
	}

}
