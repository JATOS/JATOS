package services.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.ClosedStandaloneWorker;
import models.workers.TesterWorker;
import models.workers.Worker;
import persistance.ComponentDao;
import persistance.StudyDao;
import persistance.UserDao;
import persistance.workers.WorkerDao;
import play.data.validation.ValidationError;
import play.mvc.Controller;
import services.RequestScopeMessaging;
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

	public static final String COMPONENT_POSITION_DOWN = "down";
	public static final String COMPONENT_POSITION_UP = "up";

	private final ComponentService componentService;
	private final ComponentDao componentDao;
	private final StudyDao studyDao;
	private final UserDao userDao;
	private final WorkerDao workerDao;

	@Inject
	StudyService(ComponentService componentService, ComponentDao componentDao,
			StudyDao studyDao, UserDao userDao, WorkerDao workerDao) {
		this.componentService = componentService;
		this.componentDao = componentDao;
		this.studyDao = studyDao;
		this.userDao = userDao;
		this.workerDao = workerDao;
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
			RequestScopeMessaging.error(errorMsg);
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

	/**
	 * Change the position of the component within the study. The direction is
	 * determined by the direction and can be minus one or plus one. Changes are
	 * persisted.
	 */
	public void changeComponentPosition(String direction, StudyModel study,
			ComponentModel component) throws BadRequestException {
		switch (direction) {
		case COMPONENT_POSITION_UP:
			componentPositionMinusOne(study, component);
			break;
		case COMPONENT_POSITION_DOWN:
			componentPositionPlusOne(study, component);
			break;
		default:
			throw new BadRequestException(
					MessagesStrings.studyReorderUnknownDirection(direction,
							study.getId()));
		}
		// The actual change in order happens within the component model. The
		// study model we just have to refresh.
		studyDao.refresh(study);
	}

	private void componentPositionMinusOne(StudyModel study,
			ComponentModel component) {
		int index = study.getComponentList().indexOf(component);
		if (index > 0) {
			ComponentModel prevComponent = study.getComponentList().get(
					index - 1);
			componentPositionSwap(study, component, prevComponent);
		}
	}

	private void componentPositionPlusOne(StudyModel study,
			ComponentModel component) {
		int index = study.getComponentList().indexOf(component);
		if (index < (study.getComponentList().size() - 1)) {
			ComponentModel nextComponent = study.getComponentList().get(
					index + 1);
			componentPositionSwap(study, component, nextComponent);
		}
	}

	private void componentPositionSwap(StudyModel study,
			ComponentModel component1, ComponentModel component2) {
		int position1 = study.getComponentList().indexOf(component1) + 1;
		int position2 = study.getComponentList().indexOf(component2) + 1;
		componentDao.changePosition(component1, position2);
		componentDao.changePosition(component2, position1);
	}

	public ClosedStandaloneWorker createClosedStandaloneWorker(String comment,
			Long studyId) throws BadRequestException {
		ClosedStandaloneWorker worker = new ClosedStandaloneWorker(comment);
		checkWorker(studyId, worker);
		workerDao.create(worker);
		return worker;
	}

	public TesterWorker createTesterWorker(String comment, Long studyId)
			throws BadRequestException {
		TesterWorker worker = new TesterWorker(comment);
		checkWorker(studyId, worker);
		workerDao.create(worker);
		return worker;
	}

	private void checkWorker(Long studyId, Worker worker)
			throws BadRequestException {
		List<ValidationError> errorList = worker.validate();
		if (errorList != null && !errorList.isEmpty()) {
			String errorMsg = errorList.get(0).message();
			throw new BadRequestException(errorMsg);
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
