package services.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.publix.Publix;
import controllers.publix.workers.GeneralSinglePublix;
import daos.common.BatchDao;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import models.common.Study;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.Worker;
import play.mvc.Http.Cookie;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.group.GroupService;
import services.publix.idcookie.IdCookieService;

/**
 * GeneralSinglePublix' implementation of PublixUtils
 * 
 * Additionally to the parent class PublixUtils it manages the cookie where the
 * GeneralSingle workers are stored.
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublixUtils extends PublixUtils<GeneralSingleWorker> {

	private static final String GENERAL_SINGLE_COOKIE_DELIMITER = ":";

	@Inject
	GeneralSinglePublixUtils(ResultCreator resultCreator,
			IdCookieService idCookieService, GroupService groupService,
			GeneralSingleErrorMessages errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao,
			BatchDao batchDao) {
		super(resultCreator, idCookieService, groupService, errorMessages, studyDao,
				studyResultDao, componentDao, componentResultDao, workerDao,
				batchDao);
	}

	@Override
	public GeneralSingleWorker retrieveTypedWorker(Long workerId)
			throws ForbiddenPublixException {
		Worker worker = super.retrieveWorker(workerId);
		if (!(worker instanceof GeneralSingleWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (GeneralSingleWorker) worker;
	}

	/**
	 * Check if the given study was done before. Throws an
	 * ForbiddenPublixException if this study's UUID is in the cookie. This
	 * cookie is used only in General Single runs to determine whether in a
	 * browser a study was done already. This is a (simple) way of preventing
	 * workers doing the same study twice.
	 */
	public void checkStudyInGeneralSingleCookie(Study study,
			Cookie generalSingleCookie) throws ForbiddenPublixException {
		if (generalSingleCookie != null) {
			String[] studyUuidArray = generalSingleCookie.value()
					.split(GENERAL_SINGLE_COOKIE_DELIMITER);
			for (String uuidStr : studyUuidArray) {
				if (study.getUuid().equals(uuidStr)) {
					throw new ForbiddenPublixException(
							PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
				}
			}
		}
	}

	/**
	 * Sets the cookie in the response that contains all General Single studies
	 * done by this worker so far. This cookie is HTTP only and has an expiry
	 * date in the far future.
	 */
	public void setGeneralSingleCookie(Study study) {
		String newCookieValue = addStudyUuidToGeneralSingleCookie(study,
				GeneralSinglePublix.request()
						.cookie(GeneralSinglePublix.COOKIE));
		Publix.response().setCookie(GeneralSinglePublix.COOKIE, newCookieValue,
				Integer.MAX_VALUE, "/", "", false, true);
	}

	/**
	 * If the cookie is not null it adds this study's UUID to the cookie's value
	 * and returns it. If the cookie is null (this client never did a general
	 * single run) just return the new cookie's value which is just the study's
	 * UUID.
	 */
	public String addStudyUuidToGeneralSingleCookie(Study study,
			Cookie generalSingleCookie) {
		if (generalSingleCookie != null) {
			return generalSingleCookie.value() + GENERAL_SINGLE_COOKIE_DELIMITER
					+ study.getUuid();
		} else {
			return study.getUuid();
		}
	}

}
