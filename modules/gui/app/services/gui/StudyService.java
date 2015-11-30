package services.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;

import models.common.Component;
import models.common.Group;
import models.common.Study;
import models.common.User;
import models.gui.StudyProperties;
import play.Logger;
import play.data.validation.ValidationError;
import utils.common.IOUtils;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyService {

	private static final String CLASS_NAME = StudyService.class.getSimpleName();

	public static final String COMPONENT_POSITION_DOWN = "down";
	public static final String COMPONENT_POSITION_UP = "up";

	private final GroupService groupService;
	private final ComponentService componentService;
	private final StudyDao studyDao;
	private final UserDao userDao;
	private final IOUtils ioUtils;

	@Inject
	StudyService(GroupService groupService, ComponentService componentService, StudyDao studyDao, UserDao userDao,
			IOUtils ioUtils) {
		this.groupService = groupService;
		this.componentService = componentService;
		this.studyDao = studyDao;
		this.userDao = userDao;
		this.ioUtils = ioUtils;
	}
	
	/**
	 * Clones the given Study. Does not clone id, uuid, or date. Generates a new
	 * UUID for the clone. Copies the corresponding study assets. Does not
	 * persist the clone.
	 */
	public Study clone(Study study) throws IOException {
		Study clone = new Study();
		// Generate new UUID for clone
		clone.setUuid(UUID.randomUUID().toString());
		clone.setTitle(cloneTitle(study.getTitle()));
		clone.setDescription(study.getDescription());
		clone.setDirName(study.getDirName());
		clone.setComments(study.getComments());
		clone.setJsonData(study.getJsonData());
		clone.setLocked(false);
		
		// Clone each Group
		for (Group group : study.getGroupList()) {
			clone.addGroup(groupService.clone(group));
		}

		// Clone each component
		for (Component component : study.getComponentList()) {
			Component componentClone = componentService.clone(component);
			componentClone.setStudy(clone);
			clone.addComponent(componentClone);
		}

		// Clone assets directory
		String destDirName = ioUtils
				.cloneStudyAssetsDirectory(study.getDirName());
		clone.setDirName(destDirName);

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
	public void checkStandardForStudy(Study study, Long studyId, User user)
			throws ForbiddenException, BadRequestException {
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
	 * Binds study and group properties from a edit/create study request onto a
	 * Study.
	 */
	public Study bindToStudy(StudyProperties studyProperties) {
		Study study = new Study();
		bindToStudyWithoutDirName(study, studyProperties);
		study.setDirName(studyProperties.getDirName());
		return study;
	}

	/**
	 * Create and persist a Study with given properties and greate the default
	 * Group.
	 */
	public Study createStudy(User loggedInUser, StudyProperties studyProperties) {
		Study study = bindToStudy(studyProperties);
		return createStudy(loggedInUser, study);
	}

	/**
	 * Persist the given Study and create the Group.
	 */
	public Study createStudy(User loggedInUser, Study study) {
		Group group = groupService.createGroup();
		study.addGroup(group);
		studyDao.create(study, loggedInUser);
		return study;
	}

	/**
	 * Update properties of study with properties of updatedStudy.
	 */
	public void updateStudy(Study study, Study updatedStudy) {
		updateStudyCommon(study, updatedStudy);
		study.setDirName(updatedStudy.getDirName());
		studyDao.update(study);
	}

	/**
	 * Update properties of study with properties of updatedStudy but not
	 * Study's field dirName.
	 */
	public void updateStudyWithoutDirName(Study study, Study updatedStudy) {
		updateStudyCommon(study, updatedStudy);
		studyDao.update(study);
	}

	private void updateStudyCommon(Study study, Study updatedStudy) {
		study.setTitle(updatedStudy.getTitle());
		study.setDescription(updatedStudy.getDescription());
		study.setComments(updatedStudy.getComments());
		study.setJsonData(updatedStudy.getJsonData());
	}

	/**
	 * Update Study with given properties and persist. It doesn't update Study's
	 * dirName field.
	 */
	public void updateStudy(Study study, StudyProperties studyProperties) {
		bindToStudyWithoutDirName(study, studyProperties);
		studyDao.update(study);
	}

	/**
	 * Update properties of study with properties of updatedStudy (excluding
	 * study's dir name).
	 */
	public void bindToStudyWithoutDirName(Study study,
			StudyProperties studyProperties) {
		study.setTitle(studyProperties.getTitle());
		study.setDescription(studyProperties.getDescription());
		study.setComments(studyProperties.getComments());
		study.setJsonData(studyProperties.getJsonData());
	}

	/**
	 * Renames the directory in the file system and persists the study's
	 * property.
	 */
	public void renameStudyAssetsDir(Study study, String newDirName)
			throws IOException {
		ioUtils.renameStudyAssetsDir(study.getDirName(), newDirName);
		study.setDirName(newDirName);
		studyDao.update(study);
	}

	/**
	 * Fills a new StudyProperties with values from the given Study.
	 */
	public StudyProperties bindToProperties(Study study) {
		StudyProperties studyProperties = new StudyProperties();
		studyProperties.setStudyId(study.getId());
		studyProperties.setUuid(study.getUuid());
		studyProperties.setTitle(study.getTitle());
		studyProperties.setDescription(study.getDescription());
		studyProperties.setDate(study.getDate());
		studyProperties.setLocked(study.isLocked());
		studyProperties.setDirName(study.getDirName());
		studyProperties.setComments(study.getComments());
		studyProperties.setJsonData(study.getJsonData());
		return studyProperties;
	}

	/**
	 * Validates the study by converting it to StudyProperties and uses its
	 * validate method. Throws ValidationException in case of an error.
	 */
	public void validate(Study study) throws ValidationException {
		StudyProperties studyProperties = bindToProperties(study);
		if (studyProperties.validate() != null) {
			Logger.warn(CLASS_NAME
					+ ".validate: "
					+ studyProperties.validate().stream()
							.map(ValidationError::message)
							.collect(Collectors.joining(", ")));
			throw new ValidationException(MessagesStrings.STUDY_INVALID);
		}
	}

}
