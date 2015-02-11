package services;

import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.mvc.Controller;
import play.mvc.Http;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.routes;
import daos.IComponentDao;
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
	public StudyService(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			IComponentDao studyDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.componentDao = studyDao;
	}

	/**
	 * Throws a JatosGuiException if a study is locked. Distinguishes between
	 * normal and Ajax request.
	 */
	public void checkStudyLocked(StudyModel study) throws JatosGuiException {
		if (study.isLocked()) {
			String errorMsg = ErrorMessages.studyLocked(study.getId());
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
			String errorMsg = ErrorMessages.studyNotExist(studyId);
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		// Check that the user is a member of the study
		if (!study.hasMember(user)) {
			String errorMsg = ErrorMessages.studyNotMember(user.getName(),
					user.getEmail(), studyId, study.getTitle());
			jatosGuiExceptionThrower.throwHome(errorMsg, Http.Status.FORBIDDEN);
		}
	}

	public void componentOrderMinusOne(StudyModel study,
			ComponentModel component) {
		int index = study.getComponentList().indexOf(component);
		if (index > 0) {
			ComponentModel prevComponent = study.getComponentList().get(
					index - 1);
			componentOrderSwap(study, component, prevComponent);
		}
	}

	public void componentOrderPlusOne(StudyModel study, ComponentModel component) {
		int index = study.getComponentList().indexOf(component);
		if (index < (study.getComponentList().size() - 1)) {
			ComponentModel nextComponent = study.getComponentList().get(
					index + 1);
			componentOrderSwap(study, component, nextComponent);
		}
	}

	public void componentOrderSwap(StudyModel study, ComponentModel component1,
			ComponentModel component2) {
		int index1 = study.getComponentList().indexOf(component1);
		int index2 = study.getComponentList().indexOf(component2);
		componentDao.changeComponentOrder(component1, index2);
		componentDao.changeComponentOrder(component2, index1);
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
