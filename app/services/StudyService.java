package services;

import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import persistance.IComponentDao;
import play.mvc.Controller;
import play.mvc.Http;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.routes;
import exceptions.JatosGuiException;

/**
 * Utility class for all JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyService extends Controller {

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final IComponentDao componentDao;

	@Inject
	StudyService(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			IComponentDao componentDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.componentDao = componentDao;
	}

	/**
	 * Throws a JatosGuiException if a study is locked. Distinguishes between
	 * normal and Ajax request.
	 */
	public void checkStudyLocked(StudyModel study) throws JatosGuiException {
		if (study.isLocked()) {
			String errorMsg = MessagesStrings.studyLocked(study.getId());
			jatosGuiExceptionThrower.throwRedirectOrForbidden(
					routes.Studies.index(study.getId(), null), errorMsg);
		}
	}

	/**
	 * Checks the study and throws a JatosGuiException in case of a problem.
	 * Distinguishes between normal and Ajax request.
	 */
	public void checkStandardForStudy(StudyModel study, Long studyId,
			UserModel user) throws JatosGuiException {
		if (study == null) {
			String errorMsg = MessagesStrings.studyNotExist(studyId);
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		// Check that the user is a member of the study
		if (!study.hasMember(user)) {
			String errorMsg = MessagesStrings.studyNotMember(user.getName(),
					user.getEmail(), studyId, study.getTitle());
			jatosGuiExceptionThrower.throwHome(errorMsg, Http.Status.FORBIDDEN);
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

	public void componentPositionSwap(StudyModel study, ComponentModel component1,
			ComponentModel component2) {
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

}
