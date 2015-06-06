package services;

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
 * Service class that removes ComponentResults or StudyResults. It's used by
 * controllers or other services.
 * 
 * @author Kristian Lange
 */
@Singleton
public class ResultRemover {

	private final ResultService resultService;
	private final ComponentResultDao componentResultDao;
	private final StudyResultDao studyResultDao;

	@Inject
	ResultRemover(ResultService resultService,
			ComponentResultDao componentResultDao, StudyResultDao studyResultDao) {
		this.resultService = resultService;
		this.componentResultDao = componentResultDao;
		this.studyResultDao = studyResultDao;
	}

	/**
	 * Retrieves all ComponentResults that correspond to the IDs in the given
	 * String, checks them and if yes, removes them.
	 * 
	 * @param componentResultIds
	 *            Takes a comma separated list of IDs of ComponentResults.
	 * @param user
	 *            For each ComponentResult it will be checked that the given
	 *            user is a member of the study that the ComponentResult belongs
	 *            too.
	 * @throws BadRequestException
	 * @throws NotFoundException
	 * @throws ForbiddenException
	 */
	public void removeComponentResults(String componentResultIds, UserModel user)
			throws BadRequestException, NotFoundException, ForbiddenException {
		List<Long> componentResultIdList = resultService
				.extractResultIds(componentResultIds);
		List<ComponentResult> componentResultList = resultService
				.getComponentResults(componentResultIdList);
		resultService.checkComponentResults(componentResultList, user, true);
		for (ComponentResult componentResult : componentResultList) {
			componentResultDao.remove(componentResult);
		}
	}

	/**
	 * Retrieves all StudyResults that correspond to the IDs in the given
	 * String, checks if the given user is allowed to remove them and if yes,
	 * removes them.
	 * 
	 * @param studyResultIds
	 *            Takes a comma separated list of IDs of StudyResults.
	 * @param user
	 *            For each StudyResult it will be checked that the given user is
	 *            a member of the study that the StudyResult belongs too.
	 * @throws BadRequestException
	 * @throws NotFoundException
	 * @throws ForbiddenException
	 */
	public void removeStudyResults(String studyResultIds, UserModel user)
			throws BadRequestException, NotFoundException, ForbiddenException {
		List<Long> studyResultIdList = resultService
				.extractResultIds(studyResultIds);
		List<StudyResult> studyResultList = resultService
				.getStudyResults(studyResultIdList);
		resultService.checkStudyResults(studyResultList, user, true);
		for (StudyResult studyResult : studyResultList) {
			studyResultDao.remove(studyResult);
		}
	}

	/**
	 * Removes all ComponentResults that belong to the given component.
	 * Retrieves all ComponentResults of the given component, checks if the
	 * given user is allowed to remove them and if yes, removes them.
	 */
	public void removeAllComponentResults(ComponentModel component,
			UserModel user) throws ForbiddenException, BadRequestException {
		List<ComponentResult> componentResultList = componentResultDao
				.findAllByComponent(component);
		resultService.checkComponentResults(componentResultList, user, true);
		for (ComponentResult componentResult : componentResultList) {
			componentResultDao.remove(componentResult);
		}
	}

	/**
	 * Removes all StudyResults that belong to the given study. Retrieves all
	 * StudyResults of the given study, checks if the given user is allowed to
	 * remove them and if yes, removes them.
	 */
	public void removeAllStudyResults(StudyModel study, UserModel user)
			throws ForbiddenException, BadRequestException {
		List<StudyResult> studyResultList = studyResultDao
				.findAllByStudy(study);
		resultService.checkStudyResults(studyResultList, user, true);
		for (StudyResult studyResult : studyResultList) {
			studyResultDao.remove(studyResult);
		}
	}

	/**
	 * Removes all StudyResults that belong to the given worker. Retrieves all
	 * StudyResults that belong to the given worker, checks if the given user is
	 * allowed to remove them and if yes, removes them.
	 */
	public void removeAllStudyResults(Worker worker, UserModel user)
			throws ForbiddenException, BadRequestException {
		List<StudyResult> allowedStudyResultList = resultService
				.getAllowedStudyResultList(user, worker);
		resultService.checkStudyResults(allowedStudyResultList, user, true);
		for (StudyResult studyResult : allowedStudyResultList) {
			studyResultDao.remove(studyResult);
		}
	}

}
