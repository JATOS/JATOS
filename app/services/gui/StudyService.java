package services.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import persistance.StudyDao;
import persistance.UserDao;
import services.RequestScopeMessaging;
import utils.IOUtils;
import utils.JsonUtils;

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
public class StudyService {

	public static final String COMPONENT_POSITION_DOWN = "down";
	public static final String COMPONENT_POSITION_UP = "up";

	private final ComponentService componentService;
	private final StudyDao studyDao;
	private final UserDao userDao;

	@Inject
	StudyService(ComponentService componentService, StudyDao studyDao,
			UserDao userDao) {
		this.componentService = componentService;
		this.studyDao = studyDao;
		this.userDao = userDao;
	}

	/**
	 * Clones the given StudyModel and persists it. Copies the corresponding
	 * study assets.
	 */
	public StudyModel cloneStudy(StudyModel study, UserModel loggedInUser)
			throws IOException {
		StudyModel clone = cloneStudyProperties(study);
		String destDirName = IOUtils.cloneStudyAssetsDirectory(study
				.getDirName());
		clone.setDirName(destDirName);
		studyDao.create(clone, loggedInUser);
		return clone;
	}

	/**
	 * Deletes all current members of the given study and adds the new users. A
	 * user is identified by its email. In case of an empty list an Exception is
	 * thrown.
	 */
	public void exchangeMembers(StudyModel study, String[] userEmailArray)
			throws BadRequestException {
		if (userEmailArray == null) {
			String errorMsg = MessagesStrings.STUDY_AT_LEAST_ONE_MEMBER;
			throw new BadRequestException(errorMsg);
		}
		List<UserModel> userList = new ArrayList<>();
		for (String email : userEmailArray) {
			UserModel user = userDao.findByEmail(email);
			if (user == null) {
				String errorMsg = MessagesStrings.userNotExist(email);
				RequestScopeMessaging.error(errorMsg);
				throw new BadRequestException(errorMsg);
			}
			userList.add(userDao.findByEmail(email));
		}
		if (userList.isEmpty()) {
			String errorMsg = MessagesStrings.STUDY_AT_LEAST_ONE_MEMBER;
			RequestScopeMessaging.error(errorMsg);
			throw new BadRequestException(errorMsg);
		}
		study.getMemberList().clear();
		for (UserModel user : userList) {
			studyDao.addMember(study, user);
		}
	}

	/**
	 * Clones a StudyModel. It does not copy the memberList, id, uuid, date or
	 * locked (set to false).
	 */
	private StudyModel cloneStudyProperties(StudyModel study) {
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
	 * Update a couple of study's properties (but not all) and persist it.
	 */
	public void updateStudy(StudyModel study, StudyModel updatedStudy) {
		study.setTitle(updatedStudy.getTitle());
		study.setDescription(updatedStudy.getDescription());
		study.setJsonData(updatedStudy.getJsonData());
		study.getAllowedWorkerList().clear();
		for (String workerType : updatedStudy.getAllowedWorkerList()) {
			study.addAllowedWorker(workerType);
		}
		studyDao.update(study);
	}

	/**
	 * Throws an Exception if a study is locked.
	 */
	public void checkStudyLocked(StudyModel study) throws ForbiddenException {
		if (study.isLocked()) {
			String errorMsg = MessagesStrings.studyLocked(study.getId());
			throw new ForbiddenException(errorMsg);
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

	/**
	 * Changes the position of the given component within the given study to the
	 * new position given in newPosition. Remember the first position is 1 (and
	 * not 0).
	 */
	public void changeComponentPosition(String newPosition, StudyModel study,
			ComponentModel component) throws BadRequestException {
		try {
			int currentIndex = study.getComponentList().indexOf(component);
			int newIndex = Integer.valueOf(newPosition) - 1;
			study.getComponentList().remove(currentIndex);
			study.getComponentList().add(newIndex, component);
			studyDao.update(study);
		} catch (NumberFormatException e) {
			throw new BadRequestException(
					MessagesStrings.COULDNT_CHANGE_POSITION_OF_COMPONENT);
		} catch (IndexOutOfBoundsException e) {
			throw new BadRequestException(
					MessagesStrings.studyReorderUnknownPosition(newPosition,
							study.getId()));
		}
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
		study.setJsonData(JsonUtils.asStringForDB(formMap
				.get(StudyModel.JSON_DATA)[0]));
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
