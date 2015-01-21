package services;

import java.util.Map;

import models.StudyModel;

/**
 * @author Kristian Lange
 */
public class StudyBinder {

	/**
	 * Binds study data from a edit/create study request onto a StudyModel.
	 * Play's default form binder doesn't work here.
	 */
	public static StudyModel bindStudyFromRequest(Map<String, String[]> formMap) {
		StudyModel study = new StudyModel();
		study.setTitle(formMap.get(StudyModel.TITLE)[0]);
		study.setDescription(formMap.get(StudyModel.DESCRIPTION)[0]);
		study.setDirName(formMap.get(StudyModel.DIRNAME)[0]);
		study.setJsonData(formMap.get(StudyModel.JSON_DATA)[0]);
		String[] allowedWorkerArray = formMap
				.get(StudyModel.ALLOWED_WORKER_LIST);
		if (allowedWorkerArray != null) {
			study.getAllowedWorkerList().clear();
			for (String worker : allowedWorkerArray) {
				study.addAllowedWorker(worker);
			}
		} else {
			study.getAllowedWorkerList().clear();
		}
		return study;
	}

}
