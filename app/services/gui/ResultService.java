package services.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import models.ComponentModel;
import models.ComponentResult;
import models.StudyModel;
import models.StudyResult;
import models.UserModel;
import models.workers.Worker;
import persistance.ComponentResultDao;
import persistance.StudyResultDao;
import play.mvc.Http;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.gui.JatosGuiException;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class ResultService {

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final ComponentService componentService;
	private final StudyService studyService;
	private final ComponentResultDao componentResultDao;
	private final StudyResultDao studyResultDao;

	@Inject
	ResultService(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			ComponentService componentService, StudyService studyService,
			ComponentResultDao componentResultDao, StudyResultDao studyResultDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.componentService = componentService;
		this.studyService = studyService;
		this.componentResultDao = componentResultDao;
		this.studyResultDao = studyResultDao;
	}

	/**
	 * Parses a String with result IDs and returns them in a List<Long>. Throws
	 * a JatosGuiException if an ID is not a number or if the original String
	 * doesn't contain any ID.
	 */
	public List<Long> extractResultIds(String resultIds)
			throws JatosGuiException {
		String[] resultIdStrArray = resultIds.split(",");
		List<Long> resultIdList = new ArrayList<>();
		for (String idStr : resultIdStrArray) {
			try {
				if (idStr.isEmpty()) {
					continue;
				}
				resultIdList.add(Long.parseLong(idStr.trim()));
			} catch (NumberFormatException e) {
				String errorMsg = MessagesStrings.resultNotExist(idStr);
				jatosGuiExceptionThrower.throwAjax(errorMsg,
						Http.Status.NOT_FOUND);
			}
		}
		if (resultIdList.size() < 1) {
			String errorMsg = MessagesStrings.NO_RESULTS_SELECTED;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		return resultIdList;
	}

	public void checkAllComponentResults(
			List<ComponentResult> componentResultList, UserModel loggedInUser,
			boolean studyMustNotBeLocked) throws JatosGuiException,
			ForbiddenException, BadRequestException {
		for (ComponentResult componentResult : componentResultList) {
			ComponentModel component = componentResult.getComponent();
			StudyModel study = component.getStudy();
			studyService.checkStandardForStudy(study, study.getId(),
					loggedInUser);
			componentService.checkStandardForComponents(study.getId(),
					component.getId(), loggedInUser, component);
			if (studyMustNotBeLocked) {
				studyService.checkStudyLocked(study);
			}
		}
	}

	public void checkAllStudyResults(List<StudyResult> studyResultList,
			UserModel loggedInUser, boolean studyMustNotBeLocked)
			throws ForbiddenException, BadRequestException {
		for (StudyResult studyResult : studyResultList) {
			StudyModel study = studyResult.getStudy();
			studyService.checkStandardForStudy(study, study.getId(),
					loggedInUser);
			if (studyMustNotBeLocked) {
				studyService.checkStudyLocked(study);
			}
		}
	}

	public List<ComponentResult> getAllComponentResults(
			List<Long> componentResultIdList) throws JatosGuiException {
		List<ComponentResult> componentResultList = new ArrayList<>();
		for (Long componentResultId : componentResultIdList) {
			ComponentResult componentResult = componentResultDao
					.findById(componentResultId);
			if (componentResult == null) {
				String errorMsg = MessagesStrings
						.componentResultNotExist(componentResultId);
				jatosGuiExceptionThrower.throwAjax(errorMsg,
						Http.Status.NOT_FOUND);
			}
			componentResultList.add(componentResult);
		}
		return componentResultList;
	}

	/**
	 * Put all ComponentResult's data into a String each in a separate line.
	 */
	public String getComponentResultData(
			List<ComponentResult> componentResultList) throws JatosGuiException {
		StringBuilder sb = new StringBuilder();
		Iterator<ComponentResult> iterator = componentResultList.iterator();
		while (iterator.hasNext()) {
			ComponentResult componentResult = iterator.next();
			String data = componentResult.getData();
			if (data != null) {
				sb.append(data);
				if (iterator.hasNext()) {
					sb.append("\n");
				}
			}
		}
		return sb.toString();
	}

	/**
	 * Get all StudyResults or throw a JatosGuiException if one doesn't exist.
	 */
	public List<StudyResult> getAllStudyResults(List<Long> studyResultIdList)
			throws JatosGuiException {
		List<StudyResult> studyResultList = new ArrayList<>();
		for (Long studyResultId : studyResultIdList) {
			StudyResult studyResult = studyResultDao.findById(studyResultId);
			if (studyResult == null) {
				String errorMsg = MessagesStrings
						.studyResultNotExist(studyResultId);
				jatosGuiExceptionThrower.throwAjax(errorMsg,
						Http.Status.NOT_FOUND);
			}
			studyResultList.add(studyResult);
		}
		return studyResultList;
	}

	/**
	 * Generate the list of StudyResults that the logged-in user is allowed to
	 * see.
	 */
	public List<StudyResult> getAllowedStudyResultList(UserModel loggedInUser,
			Worker worker) {
		List<StudyResult> allowedStudyResultList = new ArrayList<StudyResult>();
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().hasMember(loggedInUser)) {
				allowedStudyResultList.add(studyResult);
			}
		}
		return allowedStudyResultList;
	}

	/**
	 * Put all ComponentResult's data into a String each in a separate line.
	 */
	public String getStudyResultData(List<StudyResult> studyResultList)
			throws JatosGuiException {
		StringBuilder sb = new StringBuilder();
		for (StudyResult studyResult : studyResultList) {
			Iterator<ComponentResult> iterator = studyResult
					.getComponentResultList().iterator();
			while (iterator.hasNext()) {
				ComponentResult componentResult = iterator.next();
				String data = componentResult.getData();
				if (data != null) {
					sb.append(data);
					if (iterator.hasNext()) {
						sb.append("\n");
					}
				}
			}
		}
		return sb.toString();
	}

}
