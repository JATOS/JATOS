package services;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import models.results.StudyResult;
import models.workers.MAWorker;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import models.workers.Worker;

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
		componentResult.persist();
		studyResult.addComponentResult(componentResult);
		studyResult.merge();
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
		study.persist();
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
			for (ComponentResult componentResult : studyResult
					.getComponentResultList()) {
				componentResult.remove();
			}
			// Remove StudyResult from worker
			Worker worker = studyResult.getWorker();
			worker.removeStudyResult(studyResult);
			worker.merge();
			studyResult.remove();
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

	public static void removeComponentResult(String componentResultIdStr)
			throws NumberFormatException {
		Long componentResultId = Long.valueOf(componentResultIdStr);
		ComponentResult componentResult = ComponentResult
				.findById(componentResultId);
		if (componentResult != null) {
			StudyResult studyResult = componentResult.getStudyResult();
			studyResult.removeComponentResult(componentResult);
			studyResult.merge();
			componentResult.remove();
		}
	}

}
