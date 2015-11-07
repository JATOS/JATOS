package services.gui;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.Worker;
import daos.ComponentResultDao;
import daos.StudyResultDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;

/**
 * Service class that generates Strings from ComponentResult's or StudyResult's
 * result data. It's used by controllers or other services.
 * 
 * @author Kristian Lange
 */
@Singleton
public class ResultDataStringGenerator {

	private final ResultService resultService;
	private final ComponentResultDao componentResultDao;
	private final StudyResultDao studyResultDao;

	@Inject
	ResultDataStringGenerator(ResultService resultService,
			ComponentResultDao componentResultDao, StudyResultDao studyResultDao) {
		this.resultService = resultService;
		this.componentResultDao = componentResultDao;
		this.studyResultDao = studyResultDao;
	}

	/**
	 * Retrieves all StudyResults that belong to the given worker and that the
	 * given user is allowed to see (means StudyResults from studies he is a
	 * user of), checks them and returns all their result data in one string.
	 */
	public String forWorker(User user, Worker worker)
			throws ForbiddenException, BadRequestException {
		List<StudyResult> allowedStudyResultList = resultService
				.getAllowedStudyResultList(user, worker);
		resultService.checkStudyResults(allowedStudyResultList, user, false);
		return resultService.studyResultDataToString(allowedStudyResultList);
	}

	/**
	 * Retrieves all StudyResults of the given study, checks them and returns
	 * all their result data in one string.
	 */
	public String forStudy(User user, Study study)
			throws ForbiddenException, BadRequestException {
		List<StudyResult> studyResultList = studyResultDao
				.findAllByStudy(study);
		resultService.checkStudyResults(studyResultList, user, false);
		return resultService.studyResultDataToString(studyResultList);
	}

	/**
	 * Retrieves all ComponentResults of the given component, checks them and
	 * returns them all their result data in one string.
	 */
	public String forComponent(User user, Component component)
			throws ForbiddenException, BadRequestException {
		List<ComponentResult> componentResultList = componentResultDao
				.findAllByComponent(component);
		resultService.checkComponentResults(componentResultList, user, false);
		return resultService.componentResultDataToString(componentResultList);
	}

	/**
	 * Retrieves the StudyResults that correspond to the IDs, checks them and
	 * returns all their result data in one string.
	 */
	public String fromListOfStudyResultIds(String studyResultIds, User user)
			throws BadRequestException, NotFoundException, ForbiddenException {
		List<Long> studyResultIdList = resultService
				.extractResultIds(studyResultIds);
		List<StudyResult> studyResultList = resultService
				.getStudyResults(studyResultIdList);
		resultService.checkStudyResults(studyResultList, user, false);
		return resultService.studyResultDataToString(studyResultList);
	}

	/**
	 * Retrieves the ComponentResults that correspond to the IDs, checks them
	 * and returns all their result data in one string.
	 */
	public String fromListOfComponentResultIds(String componentResultIds,
			User user) throws BadRequestException, NotFoundException,
			ForbiddenException {
		List<Long> componentResultIdList = resultService
				.extractResultIds(componentResultIds);
		List<ComponentResult> componentResultList = resultService
				.getComponentResults(componentResultIdList);
		resultService.checkComponentResults(componentResultList, user, false);
		return resultService.componentResultDataToString(componentResultList);
	}

}
