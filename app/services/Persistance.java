package services;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import models.results.StudyResult;
import models.workers.MAWorker;
import models.workers.MTSandboxWorker;
import models.workers.MTTesterWorker;
import models.workers.MTWorker;
import models.workers.Worker;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility class that provides persistence methods.
 * 
 * @author madsen
 */
public class Persistance {

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
		if (StringUtils.containsIgnoreCase(mtWorkerId,
				MTTesterWorker.WORKER_TYPE)) {
			worker = new MTTesterWorker(mtWorkerId.toLowerCase());
		} else if (mTurkSandbox) {
			worker = new MTSandboxWorker(mtWorkerId);
		} else {
			worker = new MTWorker(mtWorkerId);
		}
		worker.persist();
		return worker;
	}

	public static UserModel createAdmin() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		MAWorker adminWorker = new MAWorker();
		adminWorker.persist();
		String passwordHash = UserModel.getHashMDFive("admin");
		UserModel adminUser = new UserModel("admin", "Admin", passwordHash);
		adminUser.setWorker(adminWorker);
		adminUser.persist();
		adminWorker.setUser(adminUser);
		adminWorker.merge();
		return adminUser;
	}

	public static void addMemberToStudy(StudyModel study, UserModel member) {
		study.addMember(member);
		study.merge();
	}

	public static void updateStudy(StudyModel study, String title,
			String description, String jsonData) {
		study.setTitle(title);
		study.setDescription(description);
		study.setJsonData(jsonData);
		study.merge();
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

	public static void updateComponent(ComponentModel component, String title,
			boolean reloadable, String viewUrl, String jsonData) {
		component.setTitle(title);
		component.setReloadable(reloadable);
		component.setViewUrl(viewUrl);
		component.setJsonData(jsonData);
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

	public static void removeComponentResult(String componentResultIdStr)
			throws NumberFormatException {
		Long componentResultId = Long.valueOf(componentResultIdStr);
		ComponentResult componentResult = ComponentResult
				.findById(componentResultId);
		if (componentResult != null) {
			removeComponentResult(componentResult);
		}
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
		// Don't remove MA's own workers
		if (worker instanceof MAWorker) {
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
