package services;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import models.ComponentModel;
import models.StudyModel;
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

	public static StudyResult createStudyResult(StudyModel study, Worker worker) {
		StudyResult studyResult = new StudyResult(study);
		studyResult.persist();
		worker.addStudyResult(studyResult);
		worker.merge();
		return studyResult;
	}

	public static ComponentResult createComponentResult(
			StudyResult studyResult, ComponentModel component) {
		ComponentResult componentResult = new ComponentResult(component);
		componentResult.setStudyResult(studyResult);
		componentResult.persist();
		studyResult.addComponentResult(componentResult);
		studyResult.merge();
		componentResult.merge();
		return componentResult;
	}

	public static MTWorker createMTWorker(String mtWorkerId,
			boolean mTurkSandbox) {
		MTWorker worker;
		if (mTurkSandbox) {
			worker = new MTSandboxWorker(mtWorkerId);
		} else {
			worker = new MTWorker(mtWorkerId);
		}
		worker.persist();
		return worker;
	}

	public static UserModel createAdmin() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		JatosWorker adminWorker = new JatosWorker();
		adminWorker.persist();
		String passwordHash = UserModel.getHashMDFive("admin");
		UserModel adminUser = new UserModel("admin", "Admin", passwordHash);
		adminUser.setWorker(adminWorker);
		adminUser.persist();
		adminWorker.setUser(adminUser);
		adminWorker.merge();
		return adminUser;
	}

	public static void addStudy(StudyModel study, UserModel loggedInUser) {
		study.persist();
		PersistanceUtils.addMemberToStudy(study, loggedInUser);
	}

	public static void addMemberToStudy(StudyModel study, UserModel member) {
		study.addMember(member);
		study.merge();
	}

	public static void updateStudysProperties(StudyModel study,
			StudyModel updatedStudy) {
		study.setTitle(updatedStudy.getTitle());
		study.setDescription(updatedStudy.getDescription());
		study.setDirName(updatedStudy.getDirName());
		study.setJsonData(updatedStudy.getJsonData());
		study.merge();
	}

	public static void updateStudysPropertiesWODirName(StudyModel study,
			StudyModel updatedStudy) {
		study.setTitle(updatedStudy.getTitle());
		study.setDescription(updatedStudy.getDescription());
		study.setJsonData(updatedStudy.getJsonData());
		study.merge();
	}

	public static void updateStudysComponents(StudyModel currentStudy,
			StudyModel updatedStudy) {
		// Clear list and rebuild it from updated study
		List<ComponentModel> currentComponentList = new ArrayList<ComponentModel>(
				currentStudy.getComponentList());
		currentStudy.getComponentList().clear();

		for (ComponentModel updatedComponent : updatedStudy.getComponentList()) {
			ComponentModel currentComponent = null;
			// Find both matching components with the same UUID
			for (ComponentModel tempComponent : currentComponentList) {
				if (tempComponent.getUuid().equals(updatedComponent.getUuid())) {
					currentComponent = tempComponent;
					break;
				}
			}
			if (currentComponent != null) {
				PersistanceUtils.updateComponentsProperties(currentComponent,
						updatedComponent);
				currentStudy.addComponent(currentComponent);
				currentComponentList.remove(currentComponent);
			} else {
				// If the updated component doesn't exist in the current study
				// add it.
				PersistanceUtils.addComponent(currentStudy, updatedComponent);
			}
		}

		// Check whether any component from the current study are left that
		// aren't in the updated study. Add them to the end of the list and
		// put them into inactive (we don't remove them, because they could be
		// associated with results)
		for (ComponentModel currentComponent : currentComponentList) {
			currentComponent.setActive(false);
			currentStudy.addComponent(currentComponent);
		}

		currentStudy.merge();
	}

	public static void removeStudy(StudyModel study) {
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

	public static void addComponent(StudyModel study, ComponentModel component) {
		component.setStudy(study);
		study.addComponent(component);
		component.persist();
		study.merge();
	}

	public static void updateComponentsProperties(ComponentModel component,
			ComponentModel updatedComponent) {
		component.setTitle(updatedComponent.getTitle());
		component.setReloadable(updatedComponent.isReloadable());
		component.setHtmlFilePath(updatedComponent.getHtmlFilePath());
		component.setComments(updatedComponent.getComments());
		component.setJsonData(updatedComponent.getJsonData());
		component.setActive(updatedComponent.isActive());
		component.merge();
	}

	public static void changeActive(ComponentModel component, boolean active) {
		component.setActive(active);
		component.merge();
	}

	public static void removeComponent(StudyModel study,
			ComponentModel component) {
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

	public static void removeComponentResult(ComponentResult componentResult) {
		StudyResult studyResult = componentResult.getStudyResult();
		studyResult.removeComponentResult(componentResult);
		studyResult.merge();
		componentResult.remove();
	}

	public static void removeStudyResult(StudyResult studyResult) {
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
	public static void removeAllStudyResults(StudyModel study) {
		List<StudyResult> studyResultList = StudyResult.findAllByStudy(study);
		for (StudyResult studyResult : studyResultList) {
			removeStudyResult(studyResult);
		}
	}

	public static void removeWorker(Worker worker) {
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
