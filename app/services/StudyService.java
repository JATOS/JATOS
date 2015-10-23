package services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.Component;
import models.Group;
import models.Study;
import models.User;
import persistance.StudyDao;
import persistance.UserDao;
import utils.IOUtils;
import utils.JsonUtils;
import utils.MessagesStrings;

import common.RequestScopeMessaging;

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
	private final GroupService groupService;
	private final StudyDao studyDao;
	private final UserDao userDao;

	@Inject
	StudyService(ComponentService componentService, GroupService groupService,
			StudyDao studyDao, UserDao userDao) {
		this.componentService = componentService;
		this.groupService = groupService;
		this.studyDao = studyDao;
		this.userDao = userDao;
	}

	/**
	 * Clones the given Study and persists it. Copies the corresponding
	 * study assets.
	 */
	public Study cloneStudy(Study study, User loggedInUser)
			throws IOException {
		Study clone = cloneStudyProperties(study);
		clone.setTitle(cloneTitle(study.getTitle()));
		String destDirName = IOUtils.cloneStudyAssetsDirectory(study
				.getDirName());
		clone.setDirName(destDirName);
		studyDao.create(clone, loggedInUser);
		return clone;
	}

	/**
	 * Clones a Study. It does NOT copy the userList, id, uuid, date or
	 * locked (set to false).
	 */
	private Study cloneStudyProperties(Study study) {
		Study clone = new Study();
		clone.setDescription(study.getDescription());
		clone.setDirName(study.getDirName());
		clone.setComments(study.getComments());
		clone.setJsonData(study.getJsonData());
		clone.setTitle(study.getTitle());
		clone.setGroupStudy(study.isGroupStudy());
		if (study.getGroup() != null) {
			Group groupClone = groupService.clone(study.getGroup());
			groupClone.setStudy(clone);
			clone.setGroup(groupClone);
		}
		clone.setLocked(false);
		study.getAllowedWorkerTypeList().forEach(clone::addAllowedWorkerType);
		// Clone each component
		for (Component component : study.getComponentList()) {
			Component componentClone = componentService
					.cloneComponentEntity(component);
			componentClone.setStudy(clone);
			clone.addComponent(componentClone);
		}
		return clone;
	}

	/**
	 * Generates an title for the cloned study by adding '(clone)' and numbers
	 * that doesn't exist so far.
	 */
	private String cloneTitle(String origTitle) {
		String cloneTitle = origTitle + " (clone)";
		int i = 2;
		while (!studyDao.findByTitle(cloneTitle).isEmpty()) {
			cloneTitle = origTitle + " (clone " + i + ")";
			i++;
		}
		return cloneTitle;
	}

	/**
	 * Update properties of study with properties of updatedStudy.
	 */
	public void updateProperties(Study study, Study updatedStudy) {
		updatePropertiesWODirNameWOUpdate(study, updatedStudy);
		study.setDirName(updatedStudy.getDirName());
		studyDao.update(study);
	}

	/**
	 * Update properties of study with properties of updatedStudy (excluding
	 * study's dir name).
	 */
	public void updatePropertiesWODirName(Study study,
			Study updatedStudy) {
		updatePropertiesWODirNameWOUpdate(study, updatedStudy);
		studyDao.update(study);
	}

	private void updatePropertiesWODirNameWOUpdate(Study study,
			Study updatedStudy) {
		study.setTitle(updatedStudy.getTitle());
		study.setDescription(updatedStudy.getDescription());
		study.setComments(updatedStudy.getComments());
		study.setGroupStudy(updatedStudy.isGroupStudy());
		if (study.isGroupStudy()) {
			groupService.updateProperties(study.getGroup(),
					updatedStudy.getGroup());
		}
		study.setJsonData(updatedStudy.getJsonData());
		study.getAllowedWorkerTypeList().clear();
		updatedStudy.getAllowedWorkerTypeList().forEach(study::addAllowedWorkerType);
	}

	/**
	 * Deletes all current users of the given study and adds the new users. A
	 * user is identified by its email. In case of an empty list an
	 * BadRequestException is thrown.
	 */
	public void exchangeUsers(Study study, String[] userEmailArray)
			throws BadRequestException {
		if (userEmailArray == null) {
			String errorMsg = MessagesStrings.STUDY_AT_LEAST_ONE_USER;
			throw new BadRequestException(errorMsg);
		}
		List<User> userList = new ArrayList<>();
		for (String email : userEmailArray) {
			User user = userDao.findByEmail(email);
			if (user == null) {
				String errorMsg = MessagesStrings.userNotExist(email);
				RequestScopeMessaging.error(errorMsg);
				throw new BadRequestException(errorMsg);
			}
			userList.add(user);
		}
		if (userList.isEmpty()) {
			String errorMsg = MessagesStrings.STUDY_AT_LEAST_ONE_USER;
			RequestScopeMessaging.error(errorMsg);
			throw new BadRequestException(errorMsg);
		}
		study.getUserList().clear();
		for (User user : userList) {
			studyDao.addUser(study, user);
		}
	}

	/**
	 * Throws an ForbiddenException if a study is locked.
	 */
	public void checkStudyLocked(Study study) throws ForbiddenException {
		if (study.isLocked()) {
			String errorMsg = MessagesStrings.studyLocked(study.getId());
			throw new ForbiddenException(errorMsg);
		}
	}

	/**
	 * Checks the study and throws an Exception in case of a problem.
	 */
	public void checkStandardForStudy(Study study, Long studyId,
			User user) throws ForbiddenException, BadRequestException {
		if (study == null) {
			String errorMsg = MessagesStrings.studyNotExist(studyId);
			throw new BadRequestException(errorMsg);
		}
		// Check that the user is a user of the study
		if (!study.hasUser(user)) {
			String errorMsg = MessagesStrings.studyNotUser(user.getName(),
					user.getEmail(), studyId, study.getTitle());
			throw new ForbiddenException(errorMsg);
		}
	}

	/**
	 * Changes the position of the given component within the given study to the
	 * new position given in newPosition. Remember the first position is 1 (and
	 * not 0). Throws BadRequestException if number has wrong format or number
	 * isn't within the studies positions.
	 */
	public void changeComponentPosition(String newPosition, Study study,
			Component component) throws BadRequestException {
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
	 * Binds study data from a edit/create study request onto a Study.
	 * Play's default form binder doesn't work here.
	 */
	public Study bindStudyFromRequest(Map<String, String[]> formMap) {
		Study study = new Study();
		study.setTitle(formMap.get(Study.TITLE)[0]);
		study.setDescription(formMap.get(Study.DESCRIPTION)[0]);
		study.setComments(formMap.get(Study.COMMENTS)[0]);
		study.setDirName(formMap.get(Study.DIRNAME)[0]);
		study.setGroupStudy(Boolean.parseBoolean(formMap
				.get(Study.GROUP_STUDY)[0]));
		if (study.isGroupStudy()) {
			study.setGroup(groupService.bindFromRequest(formMap));
		}
		study.setJsonData(JsonUtils.asStringForDB(formMap
				.get(Study.JSON_DATA)[0]));
		String[] allowedWorkerArray = formMap
				.get(Study.ALLOWED_WORKER_LIST);
		if (allowedWorkerArray != null) {
			study.getAllowedWorkerTypeList().clear();
			for (String worker : allowedWorkerArray) {
				study.addAllowedWorkerType(worker);
			}
		} else {
			study.getAllowedWorkerTypeList().clear();
		}
		return study;
	}

	/**
	 * Renames the directory in the file system and persists the study's
	 * property.
	 */
	public void renameStudyAssetsDir(Study study, String newDirName)
			throws IOException {
		IOUtils.renameStudyAssetsDir(study.getDirName(), newDirName);
		study.setDirName(newDirName);
		studyDao.update(study);
	}

}
