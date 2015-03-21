package services.gui;

import java.io.IOException;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import persistance.ComponentDao;
import persistance.StudyDao;
import play.mvc.Controller;
import utils.IOUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.BadRequestException;
import exceptions.ForbiddenException;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyService extends Controller {

	private final ComponentService componentService;
	private final ComponentDao componentDao;
	private final StudyDao studyDao;

	@Inject
	StudyService(ComponentService componentService, ComponentDao componentDao,
			StudyDao studyDao) {
		this.componentService = componentService;
		this.componentDao = componentDao;
		this.studyDao = studyDao;
	}

	/**
	 * Clones a StudyModel. It does not copy the memberList, id, uuid, date or
	 * locked (set to false).
	 */
	public StudyModel clone(StudyModel study) {
		StudyModel clone = new StudyModel();
		clone.setDescription(study.getDescription());
		clone.setDirName(study.getDirName());
		clone.setJsonData(study.getJsonData());
		clone.setTitle(study.getTitle());
		clone.setLocked(false);
		for (String workerType : study.getAllowedWorkerList()) {
			clone.addAllowedWorker(workerType);
		}
		for (ComponentModel component : study.getComponentList()) {
			ComponentModel componentClone = componentService.clone(component);
			componentClone.setStudy(clone);
			clone.addComponent(componentClone);
		}
		return clone;
	}

	/**
	 * Throws an Exception if a study is locked.
	 */
	public void checkStudyLocked(StudyModel study) throws ForbiddenException {
		if (study.isLocked()) {
			String errorMsg = MessagesStrings.studyLocked(study.getId());
			throw new ForbiddenException(errorMsg);
			// jatosGuiExceptionThrower.throwRedirectOrForbidden(
			// controllers.gui.routes.Studies.index(study.getId()),
			// errorMsg);
		}
	}

	/**
	 * Checks the study and throws an Exception in case of a problem.
	 */
	public void checkStandardForStudy(StudyModel study, Long studyId,
			UserModel user) throws ForbiddenException, BadRequestException {
		if (study == null) {
			String errorMsg = MessagesStrings.studyNotExist(studyId);
			throw new BadRequestException(errorMsg);
		}
		// Check that the user is a member of the study
		if (!study.hasMember(user)) {
			String errorMsg = MessagesStrings.studyNotMember(user.getName(),
					user.getEmail(), studyId, study.getTitle());
			throw new ForbiddenException(errorMsg);
		}
	}

	public void componentPositionMinusOne(StudyModel study,
			ComponentModel component) {
		int index = study.getComponentList().indexOf(component);
		if (index > 0) {
			ComponentModel prevComponent = study.getComponentList().get(
					index - 1);
			componentPositionSwap(study, component, prevComponent);
		}
	}

	public void componentPositionPlusOne(StudyModel study,
			ComponentModel component) {
		int index = study.getComponentList().indexOf(component);
		if (index < (study.getComponentList().size() - 1)) {
			ComponentModel nextComponent = study.getComponentList().get(
					index + 1);
			componentPositionSwap(study, component, nextComponent);
		}
	}

	public void componentPositionSwap(StudyModel study,
			ComponentModel component1, ComponentModel component2) {
		int position1 = study.getComponentList().indexOf(component1) + 1;
		int position2 = study.getComponentList().indexOf(component2) + 1;
		componentDao.changePosition(component1, position2);
		componentDao.changePosition(component2, position1);
	}

	/**
	 * Binds study data from a edit/create study request onto a StudyModel.
	 * Play's default form binder doesn't work here.
	 */
	public StudyModel bindStudyFromRequest(Map<String, String[]> formMap) {
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

	/**
	 * Renames the directory in the file system and persists the study's
	 * property.
	 */
	public void renameStudyAssetsDir(StudyModel study, String newDirName)
			throws IOException {
		IOUtils.renameStudyAssetsDir(study.getDirName(), newDirName);
		study.setDirName(newDirName);
		studyDao.update(study);
	}
}
