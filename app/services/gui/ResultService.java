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

import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.NotFoundException;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class ResultService {

	private final ComponentService componentService;
	private final StudyService studyService;
	private final ComponentResultDao componentResultDao;
	private final StudyResultDao studyResultDao;

	@Inject
	ResultService(ComponentService componentService, StudyService studyService,
			ComponentResultDao componentResultDao, StudyResultDao studyResultDao) {
		this.componentService = componentService;
		this.studyService = studyService;
		this.componentResultDao = componentResultDao;
		this.studyResultDao = studyResultDao;
	}

	/**
	 * Parses a String with result IDs and returns them in a List<Long>. Throws
	 * an Exception if an ID is not a number or if the original String doesn't
	 * contain any ID.
	 */
	public List<Long> extractResultIds(String resultIds)
			throws BadRequestException {
		String[] resultIdStrArray = resultIds.split(",");
		List<Long> resultIdList = new ArrayList<>();
		for (String idStr : resultIdStrArray) {
			try {
				if (idStr.trim().isEmpty()) {
					continue;
				}
				resultIdList.add(Long.parseLong(idStr.trim()));
			} catch (NumberFormatException e) {
				throw new BadRequestException(
						MessagesStrings.resultIdMalformed(idStr));
			}
		}
		if (resultIdList.size() < 1) {
			throw new BadRequestException(MessagesStrings.NO_RESULTS_SELECTED);
		}
		return resultIdList;
	}

	/**
	 * Checks a list of ComponentResult. Checks each ComponentResult whether the
	 * belonging Study and Component are fine. It also checks whether the study
	 * is locked. In case of any problem an Exception is thrown.
	 */
	public void checkAllComponentResults(
			List<ComponentResult> componentResultList, UserModel loggedInUser,
			boolean studyMustNotBeLocked) throws ForbiddenException,
			BadRequestException {
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

	/**
	 * Checks a list of StudyResult. Checks each StudyResult whether the
	 * belonging Study is fine. It also checks whether the study is locked. In
	 * case of any problem an Exception is thrown.
	 */
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

	/**
	 * Retrieves the to the IDs corresponding StudyResults, checks them and
	 * returns them all in one string.
	 */
	public String generateStudyResultStr(String studyResultIds,
			UserModel loggedInUser) throws BadRequestException,
			NotFoundException, ForbiddenException {
		List<Long> studyResultIdList = extractResultIds(studyResultIds);
		List<StudyResult> studyResultList = getAllStudyResults(studyResultIdList);
		checkAllStudyResults(studyResultList, loggedInUser, false);
		String studyResultDataAsStr = getStudyResultData(studyResultList);
		return studyResultDataAsStr;
	}

	/**
	 * Retrieves the to the IDs corresponding ComponentResults, checks them and
	 * returns them all in one string.
	 */
	public String generateComponentResultDataStr(String componentResultIds,
			UserModel loggedInUser) throws BadRequestException,
			NotFoundException, ForbiddenException {
		List<Long> componentResultIdList = extractResultIds(componentResultIds);
		List<ComponentResult> componentResultList = getAllComponentResults(componentResultIdList);
		checkAllComponentResults(componentResultList, loggedInUser, false);
		String componentResultDataAsStr = getComponentResultData(componentResultList);
		return componentResultDataAsStr;
	}

	/**
	 * Gets the corresponding ComponentResult for a list of IDs.
	 */
	public List<ComponentResult> getAllComponentResults(
			List<Long> componentResultIdList) throws NotFoundException {
		List<ComponentResult> componentResultList = new ArrayList<>();
		for (Long componentResultId : componentResultIdList) {
			ComponentResult componentResult = componentResultDao
					.findById(componentResultId);
			if (componentResult == null) {
				throw new NotFoundException(
						MessagesStrings
								.componentResultNotExist(componentResultId));
			}
			componentResultList.add(componentResult);
		}
		return componentResultList;
	}

	/**
	 * Get all StudyResults or throw an Exception if one doesn't exist.
	 * 
	 * @throws NotFoundException
	 */
	public List<StudyResult> getAllStudyResults(List<Long> studyResultIdList)
			throws NotFoundException {
		List<StudyResult> studyResultList = new ArrayList<>();
		for (Long studyResultId : studyResultIdList) {
			StudyResult studyResult = studyResultDao.findById(studyResultId);
			if (studyResult == null) {
				throw new NotFoundException(
						MessagesStrings.studyResultNotExist(studyResultId));
			}
			studyResultList.add(studyResult);
		}
		return studyResultList;
	}

	/**
	 * Generate the list of StudyResults that belong to the given Worker and
	 * that the logged-in user is allowed to see.
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
	 * Put all StudyResult's data into a String each in a separate line.
	 */
	public String getStudyResultData(List<StudyResult> studyResultList) {
		StringBuilder sb = new StringBuilder();
		Iterator<StudyResult> iterator = studyResultList.iterator();
		while (iterator.hasNext()) {
			StudyResult studyResult = iterator.next();
			String componentResultData = getComponentResultData(studyResult
					.getComponentResultList());
			sb.append(componentResultData);
			if (iterator.hasNext()) {
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	/**
	 * Put all ComponentResult's data into a String each in a separate line.
	 */
	public String getComponentResultData(
			List<ComponentResult> componentResultList) {
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
	 * Retrieves all ComponentResults that correspond to the IDs in the given
	 * String, checks them and removes them.
	 */
	public void removeAllComponentResults(String componentResultIds,
			UserModel loggedInUser) throws BadRequestException,
			NotFoundException, ForbiddenException {
		List<Long> componentResultIdList = extractResultIds(componentResultIds);
		List<ComponentResult> componentResultList = getAllComponentResults(componentResultIdList);
		checkAllComponentResults(componentResultList, loggedInUser, true);
		for (ComponentResult componentResult : componentResultList) {
			componentResultDao.remove(componentResult);
		}
	}

	/**
	 * Retrieves all StudyResults that correspond to the IDs in the given
	 * String, checks if you are allowed to remove them and removes them.
	 */
	public void removeAllStudyResults(String studyResultIds,
			UserModel loggedInUser) throws BadRequestException,
			NotFoundException, ForbiddenException {
		List<Long> studyResultIdList = extractResultIds(studyResultIds);
		List<StudyResult> studyResultList = getAllStudyResults(studyResultIdList);
		checkAllStudyResults(studyResultList, loggedInUser, true);
		for (StudyResult studyResult : studyResultList) {
			studyResultDao.remove(studyResult);
		}
	}

}
