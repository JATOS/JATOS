package services;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.ComponentModel;
import models.ComponentResult;
import models.StudyModel;
import models.StudyResult;
import models.UserModel;
import models.workers.Worker;
import persistance.ComponentResultDao;
import persistance.StudyResultDao;
import utils.MessagesStrings;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.NotFoundException;

/**
 * Service class around ComponentResults and StudyResults. It's used by
 * controllers or other services.
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
	 * an BadRequestException if an ID is not a number or if the original String
	 * doesn't contain any ID.
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
	 * belonging Study and Component are fine (checkStandard). It also checks
	 * whether the study is locked.
	 * 
	 * @param componentResultList
	 *            A list of ComponentResults
	 * @param user
	 *            The study that corresponds to the results must have this user
	 *            as a member otherwise ForbiddenException will be thrown.
	 * @param studyMustNotBeLocked
	 *            If true the study that corresponds to the results must not be
	 *            locked and it will throw an ForbiddenException.
	 * @throws ForbiddenException
	 * @throws BadRequestException
	 */
	public void checkComponentResults(
			List<ComponentResult> componentResultList, UserModel user,
			boolean studyMustNotBeLocked) throws ForbiddenException,
			BadRequestException {
		for (ComponentResult componentResult : componentResultList) {
			ComponentModel component = componentResult.getComponent();
			StudyModel study = component.getStudy();
			studyService.checkStandardForStudy(study, study.getId(), user);
			componentService.checkStandardForComponents(study.getId(),
					component.getId(), component);
			if (studyMustNotBeLocked) {
				studyService.checkStudyLocked(study);
			}
		}
	}

	/**
	 * Checks a list of StudyResult. Checks each StudyResult whether the
	 * belonging Study is fine (checkStandard). It also checks whether the study
	 * is locked.
	 * 
	 * @param studyResultList
	 *            A list of StudyResults
	 * @param user
	 *            The study that corresponds to the results must have this user
	 *            as a member otherwise ForbiddenException will be thrown.
	 * @param studyMustNotBeLocked
	 *            If true the study that corresponds to the results must not be
	 *            locked and it will throw an ForbiddenException.
	 * @throws ForbiddenException
	 * @throws BadRequestException
	 */
	public void checkStudyResults(List<StudyResult> studyResultList,
			UserModel user, boolean studyMustNotBeLocked)
			throws ForbiddenException, BadRequestException {
		for (StudyResult studyResult : studyResultList) {
			StudyModel study = studyResult.getStudy();
			studyService.checkStandardForStudy(study, study.getId(), user);
			if (studyMustNotBeLocked) {
				studyService.checkStudyLocked(study);
			}
		}
	}

	/**
	 * Gets the corresponding ComponentResult for a list of IDs. Throws an
	 * exception if the ComponentResult doesn't exist.
	 */
	public List<ComponentResult> getComponentResults(
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
	 * Get all StudyResults or throw an Exception if one doesn't exist. Throws
	 * an exception if the StudyResult doesn't exist.
	 */
	public List<StudyResult> getStudyResults(List<Long> studyResultIdList)
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
	 * that the given user is allowed to see. A user is allowed if the study
	 * that the StudyResult belongs too has the user as a member.
	 */
	public List<StudyResult> getAllowedStudyResultList(UserModel user,
			Worker worker) {
		return worker.getStudyResultList().stream()
				.filter(studyResult -> studyResult.getStudy().hasMember(user))
				.collect(Collectors.toList());
	}

	/**
	 * Put all StudyResult's result data into a String each in a separate line.
	 */
	public String studyResultDataToString(List<StudyResult> studyResultList) {
		StringBuilder sb = new StringBuilder();
		Iterator<StudyResult> iterator = studyResultList.iterator();
		while (iterator.hasNext()) {
			StudyResult studyResult = iterator.next();
			String componentResultData = componentResultDataToString(studyResult
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
	public String componentResultDataToString(
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

}
