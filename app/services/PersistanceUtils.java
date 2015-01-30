package services;

import java.util.List;

import com.google.inject.Inject;

import models.ComponentModel;
import models.StudyModel;
import models.UserDao;
import models.UserModel;
import models.results.ComponentResult;
import models.results.StudyResult;
import models.workers.JatosWorker;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import models.workers.Worker;
import play.Play;

/**
 * Utility class that provides persistence methods.
 * 
 * @author Kristian Lange
 */
public class PersistanceUtils {

	/**
	 * Is true if an in-memory database is used.
	 */
	public static boolean IN_MEMORY_DB = Play.application().configuration()
			.getString("db.default.url").contains("jdbc:h2:mem:");

	private final UserDao userDao;

	@Inject
	public PersistanceUtils(UserDao userDao) {
		this.userDao = userDao;
	}

	/**
	 * Creates StudyResult and adds it to the worker.
	 */
	public StudyResult createStudyResult(StudyModel study, Worker worker) {
		StudyResult studyResult = new StudyResult(study);
		studyResult.persist();
		worker.addStudyResult(studyResult);
		worker.merge();
		return studyResult;
	}

	/**
	 * Creates ComponentResult and adds it to the study.
	 */
	public ComponentResult createComponentResult(StudyResult studyResult,
			ComponentModel component) {
		ComponentResult componentResult = new ComponentResult(component);
		componentResult.setStudyResult(studyResult);
		componentResult.persist();
		studyResult.addComponentResult(componentResult);
		studyResult.merge();
		componentResult.merge();
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
		worker.persist();
		return worker;
	}

	/**
	 * Persist user und creates it's JatosWorker.
	 */
	public void addUser(UserModel user) {
		JatosWorker worker = new JatosWorker(user);
		worker.persist();
		user.setWorker(worker);
		userDao.persist(user);
		worker.merge();
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
		study.persist();
		addMemberToStudy(study, loggedInUser);
	}

	/**
	 * Add member to study.
	 */
	public void addMemberToStudy(StudyModel study, UserModel member) {
		study.addMember(member);
		study.merge();
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
		study.merge();
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
		study.merge();
	}

	/**
	 * Remove study and its components
	 */
	public void removeStudy(StudyModel study) {
		// Remove all study's components
		for (ComponentModel component : study.getComponentList()) {
			component.remove();
		}
		// Remove study's StudyResults and ComponentResults
		for (StudyResult studyResult : StudyResult.findAllByStudy(study)) {
			removeStudyResult(studyResult);
		}
		study.remove();
	}

	/**
	 * Persist component and add to study.
	 */
	public void addComponent(StudyModel study, ComponentModel component) {
		component.setStudy(study);
		study.addComponent(component);
		component.persist();
		study.merge();
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
		component.merge();
	}

	/**
	 * Change and persist active property.
	 */
	public void changeActive(ComponentModel component, boolean active) {
		component.setActive(active);
		component.merge();
	}

	/**
	 * Remove component from study, all its ComponentResults and the component
	 * itself.
	 */
	public void removeComponent(StudyModel study, ComponentModel component) {
		// Remove component from study
		study.removeComponent(component);
		study.merge();
		// Remove component's ComponentResults
		for (ComponentResult componentResult : ComponentResult
				.findAllByComponent(component)) {
			StudyResult studyResult = componentResult.getStudyResult();
			studyResult.removeComponentResult(componentResult);
			studyResult.merge();
			componentResult.remove();
		}
		component.remove();
	}

	/**
	 * Remove ComponentResult form its StudyResult and remove itself.
	 */
	public void removeComponentResult(ComponentResult componentResult) {
		StudyResult studyResult = componentResult.getStudyResult();
		studyResult.removeComponentResult(componentResult);
		studyResult.merge();
		componentResult.remove();
	}

	/**
	 * Remove StudyResult and all its ComponentResults. Remove study result from
	 * worker.
	 */
	public void removeStudyResult(StudyResult studyResult) {
		// Remove all component results of this study result
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			componentResult.remove();
		}

		// Remove study result from worker
		Worker worker = studyResult.getWorker();
		worker.removeStudyResult(studyResult);
		worker.merge();

		// Remove studyResult
		studyResult.remove();
	}

	/**
	 * Removes all StudyResults including their ComponentResult of the specified
	 * study.
	 */
	public void removeAllStudyResults(StudyModel study) {
		List<StudyResult> studyResultList = StudyResult.findAllByStudy(study);
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
				componentResult.remove();
			}
			studyResult.remove();
		}

		// Remove worker
		worker.remove();
	}

}
